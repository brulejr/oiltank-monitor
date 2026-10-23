# Oil Tank Monitor: Design Notes

Status: draft for review. The whole chain is implemented: `pipeline/`, `camera/`,
`detection/` (with mark tracking and the day/night cross-check), `level/` and `publish/`.
`docker/` too; the Bruno collection and the end-to-end test are not. Items marked
**Proposed** go beyond the prototype and need a decision. Questions still open
are collected at the end.

## Goal

Read the level of a home heating-oil tank from its mechanical float gauge, using an
RTSP camera pointed at the gauge, and publish the level to Home Assistant over MQTT.
This rewrites the prototype [`brulejr/monitor-oiltank`](https://github.com/brulejr/monitor-oiltank)
to follow the conventions of [`brulejr/iot-pipeline`](https://github.com/brulejr/iot-pipeline).

## What changes from the prototype

| Prototype | Rewrite |
|---|---|
| `ksb-commons` `EventBus` with a sealed `OilEvent` | Spring Integration channels; each stage is a flow |
| `@Scheduled` emitting `SnapshotRequested` | A polled inbound flow: the poller *is* the schedule |
| `@Service` / `@Component`, `ControllableService` | One `*Config` per feature, `@Bean` wiring, Spring lifecycle |
| HiveMQ MQTT 3 async client | `spring-integration-mqtt` outbound adapter on Paho |
| Debug copy written to `/tmp/oiltank-latest.jpg` | Latest snapshot held in memory, served by `GET /api/camera/snapshot` |
| Level thresholds defined but unused | Thresholds drive a level status (see Level) |
| Subscribes in `onStop`, cancels in `onStart` | Gone: channels are wired once, at startup |
| Spring Boot 3.5.7, Jib image | Spring Boot 4.1.1, `docker/Dockerfile` packaging the boot jar |

## Pipeline

One reading moves through four stages, joined by direct channels named in
`pipeline/PipelineChannels.kt` and declared in `pipeline/PipelineConfig`:

```
 camera ──snapshot──▶ detection ──floatReading──▶ level ──tankLevel──▶ publish ──▶ MQTT broker
   ▲ poller (snapshot-interval)        │ (filtered by hysteresis)                    (Home Assistant)
   └── POST /api/camera/snapshot       ▼
                             pipelineErrorChannel ──▶ log (stage health records it too)
```

| Channel | Payload | Producer → consumer |
|---|---|---|
| `snapshotChannel` | `Snapshot` | camera → detection |
| `floatReadingChannel` | `FloatReading` | detection → level |
| `tankLevelChannel` | `TankLevel` | level → publish |

Payloads (one file each, in the package that produces them):

- `camera/Snapshot(jpeg: ByteArray, capturedAt: Instant)`
- `detection/FloatReading(floatY: Int, raw: Double, smoothed: Double, capturedAt: Instant)`.
  `raw` and `smoothed` are fill fractions in 0..1.
- `level/TankLevel(percent: Int, status: LevelStatus, reading: FloatReading)`, where
  `LevelStatus` is `LOW | NORMAL | HIGH`.

(`FloatReading` was revised after the first real frames; see detection/.)

Every stage is synchronous on the poller thread, so one capture is processed
end to end before the next starts. With a 30 s interval and a sub-second
pipeline there is no need for queues or executors.

Failures (ffmpeg exits non-zero, no contour found, broker down) raise an exception
in the stage, which records it for its own health indicator. The exception unwinds
to the camera poller, whose error channel is `pipelineErrorChannel`; that flow logs
the cause's message at WARN under `oiltank.errors`, without a stack trace, since an
unreachable camera fails every poll. The next poll starts clean. A failure never stops the
poller.

`PipelineTopologyTest` asserts the wiring against the graph Spring Integration
builds, as in iot-pipeline.

## Features

Every feature follows `.claude/CONVENTIONS.md`: one `*Config`, a validated
`*Datafill`, a `*Service`, a controller under `api/`, a `*HealthIndicator` and an
`*InfoContributor`. Datafills bind under the `oiltank:` root.

### camera/

Captures one JPEG frame from the RTSP stream.

- `SnapshotSource` (port), implemented by `FfmpegSnapshotSource`. It runs
  `ffmpeg -hide_banner -loglevel error -rtsp_transport tcp -i <url> -frames:v 1 -f mjpeg pipe:1`
  with a timeout. It rejects a non-zero exit, empty output, or bytes `ImageIO`
  cannot decode. The port lets tests and local runs use a `FileSnapshotSource`
  that replays fixture images, so no camera is needed.
- `CameraService.capture()` calls the source, checks the bytes decode (`ImageIO`)
  whatever the source, keeps the latest `Snapshot` and capture statistics, and
  returns the snapshot. `captureNow()` also sends it to `snapshotChannel`.
- `CameraConfig` declares the polled inbound flow `cameraFlow`, a fixed delay of
  `snapshot-interval`, sending to `snapshotChannel`. On-demand captures go through
  `CameraService.captureNow()` rather than a gateway. So a manual capture and a poll
  can overlap, and detection's state must be thread-safe.
- `CameraController`: `GET /api/camera` returns the capture status.
  `GET /api/camera/snapshot` returns the latest JPEG with `Last-Modified` (404 before
  the first capture). `POST /api/camera/snapshot` captures now, runs the pipeline,
  and returns the status. A capture failure is a 502 with the reason.
- ffmpeg's stderr is passed into the failure message with the RTSP URL redacted,
  since ffmpeg echoes the input URL, credentials included.
- Health: UP if the last capture succeeded and is younger than `3 × snapshot-interval`.
  DOWN with the last error otherwise. UNKNOWN until the first capture completes.
  Details: capture and failure counts, last capture time, last failure.
- Info (configuration only, as in iot-pipeline; runtime counts are in health):
  source type, redacted URL, ffmpeg path, timeout, interval.

| Key (`oiltank.camera.*`) | Default | Notes |
|---|---|---|
| `enabled` | `true` | `@ConditionalOnProperty`: off stops the poller |
| `source` | `${CAMERA_SOURCE:ffmpeg}` | `ffmpeg` or `file` |
| `rtsp-url` | `${OILTANK_CAMERA_RTSP_URL:}` | Required when `source=ffmpeg`; startup fails without it |
| `snapshot-interval` | `30s` | `Duration`, at least 5 s |
| `capture-timeout` | `15s` | Kills ffmpeg if exceeded |
| `ffmpeg-path` | `${FFMPEG_PATH:ffmpeg}` | Resolved on `PATH` |
| `file.directory` | (none) | Required for `source=file`; `.jpg`/`.jpeg` replayed in name order, cycling |

### detection/

Turns a snapshot into a smoothed fill fraction.

**Revised 2026-10-20 after the first real frames** (`src/test/resources/gauge/`): the
prototype's algorithm does not fit this camera. Findings:

- **The float is the only yellow object on the gauge, so colour finds it.** Inside a
  box around the tube, an HSV threshold (hue 15–40 on OpenCV's 0–180 scale,
  saturation ≥ 80, value ≥ 60 at first) gives exactly one blob in both frames, and nothing else.
  The prototype's grayscale Otsu threshold with "largest contour" competes with the
  black graduation marks, the black pipe behind the tube and the dark ring under the
  float. It was not tried on these frames, but the float is neither the darkest nor the
  largest region.
- **The box must be configured, not centred.** The yellow board at the bottom left is
  the same hue (about 7,400 matching pixels), so a box is required. The prototype's
  fixed 300×500 box, centred horizontally, misses the gauge entirely in the November
  frame (tube at x 1190–1340) and contains it today only because the camera was re-aimed.
- **The camera moves.** Between the two frames the gauge shifted from x≈1260 to x≈970
  and about 110 px down. Any pixel calibration is per framing, so recalibrating has to
  be cheap: see the debug endpoint below.
- **The marks are not evenly spaced in the image** (perspective from below): the gaps are
  85/93/92/78 px in one frame and 91/87/88/82 px in the other. A single
  slope-and-intercept cannot fit that; interpolating between the marks can.
- **The visible float changes shape with viewing angle.** The wide upper part is 40 px
  tall in the November frame and 13 px today. The gauge is read where the wide part
  steps down to the narrow skirt; in both frames that sits on the ½ mark. So the
  distance from any detected edge to the reading line belongs to the camera position,
  like the marks (62 px from the top in November, 20 px today).
- **At night the camera switches to infrared** (found live, 22:49). The frame is exactly
  grey, so the yellow threshold finds nothing. Under IR the float glows white, and so
  does a narrow reflection running the length of the tube (18–33 px wide, against the
  float's 57–80). The gauge's metal base also glows, from row 816.
- **Dawn and dusk give dim colour frames** (found live, 07:15 on 2026-10-21). The camera
  is in colour, but the float's value falls to about 42, under the first threshold of
  60, so it was missed. Lowering the thresholds generally pulled in warm-tinted parts
  of the tube in daylight and moved the float's top by 6 rows, and the region's mean
  brightness (53 by day, 44 at dawn) is too close to switch on. What holds is
  saturation: the float stays strongly saturated as the light fades, the tint does not.
  With saturation ≥ 120 and value ≥ 30, the float's top is 608 by day (as before), 612
  at dawn (as at night) and 455 in the November frame (456 before). Below the float,
  the skirt seen through the ½ mark then forms a second blob, sometimes larger, so the
  colour detector takes the **topmost** blob of at least `min-area`, as the infrared
  one takes the topmost band: the tube above the float is clear.
- **The top edge is the reference both modes agree on.** In today's framing the float's
  top is at 608 by day and 612 by night. Its bottom is 620 by day (only the part above
  the ½ mark's black line reads as yellow) but 672 by night (the skirt glows too).

Design:

- `FloatDetector` (port) returns a `FloatBlob` (bounding box, area, `DetectionMode`) or
  `null`. The application's is `DayNightFloatDetector`, which checks whether the ROI
  has any colour (mean |R−G| + |G−B| below 1 is grey; daylight frames measure 5 or more)
  and delegates:
  - `ColorFloatDetector` (day): thresholds the ROI in HSV, opens the mask with a 3×3
    erosion and dilation (removes specks), and takes the topmost 8-connected blob.
  - `InfraredFloatDetector` (night): counts each ROI row's pixels at or above
    `infrared.brightness-min`. Rows at least `infrared.min-width` wide are the float's,
    since the reflection down the tube is narrower. Bands separated by no more than
    `infrared.max-gap` rows are joined, because a mark's black line crosses the float.
    The topmost band is taken.

  Either way a blob smaller than `min-area` is no float.
- **No OpenCV.** Both detectors are plain Kotlin over `BufferedImage`: the ROI is about
  50,000 pixels and the steps are simple, so they take a few milliseconds (measured
  17 ms for the first frame, under 6 ms after). This drops the ~300 MB of natives
  `opencv-platform` would add, and with them the build's dependency on the target CPU
  architecture. An OpenCV detector could still be added behind the port.
- **Mark tracking** (`MarkTracker`), added 2026-10-21. The marks are dark bars across
  the tube, so each shows as a row darker than its neighbourhood in two column bands
  near the tube's edges. Automatic discovery found exactly the five marks in all three
  frames, within 2–3 px of hand measurements; for E it found 787–789 against a hand
  measurement of 800, which was the error. But a stress test (a copy of the float pasted
  between marks) showed free discovery can be fooled: the float's edges and dark
  underside produce bands that sometimes *replace* a real mark while keeping the count
  at five, so a count check would not catch it. So the marks are tracked, not
  discovered:
  - bands are looked for only outside the float's rows (`float-rows` below its top);
  - the configured `marks.rows` are the reference, and the one vertical shift (within
    `max-shift`) that matches the most bands within `tolerance` is chosen, then refined
    to the mean offset of the matched marks;
  - every mark moves by that shift, including those the float hides;
  - fewer than `min-matches` matches fails the frame ("has the camera moved?") rather
    than reading it against wrong marks;
  - every mark that should be visible (inside the region and at least 15 rows from its
    edges, outside the float's rows) must also be matched. The marks are evenly spaced
    (about 88 rows), so a drift of one gap lines most of them up with their neighbours at
    a small apparent shift, and enough would match to read every mark one level off. In
    such a false alignment an end mark lands where there is no band. Tested at ±88 rows.

  A stray band can at worst fail to match. Small camera drift is absorbed: frames moved
  ±25 rows read 0.494–0.506, by day and by night. The ROI does not move, so a bigger
  move needs recalibrating. Scaling the reference offset by the local mark gap was
  considered, to absorb angle changes too, but the two framings disagree (0.23 of a gap
  today, 0.66 in November), so the offset stays in pixels.
- **Day/night cross-check.** When a reading follows one in the other mode within
  `cross-check.window`, their raw levels are compared (`ModeComparison`), since the tank
  barely changes in that time. The difference is a health detail and the summary
  `oiltank.float.mode.difference`, flagged beyond `cross-check.tolerance`: a reason to
  recalibrate, not a failed reading.
- `DetectionService.process(snapshot)` detects the float, aligns the marks around it,
  reads the gauge at the float's top plus `reference-offset`, converts that row to a fill
  fraction by piecewise-linear interpolation between the aligned marks
  (clamped to 0..1), applies EMA smoothing, and returns a `FloatReading`. It holds the
  EMA state and the last value passed on, behind a lock (`@Synchronized`), since a
  manual capture can overlap a poll.
- `FloatReading(floatY: Int, raw: Double, smoothed: Double, mode: DetectionMode, capturedAt)`.
  `raw` is the interpolated fraction; `calibrated` is gone, since the marks *are* the
  calibration.
- `DetectionConfig` declares `snapshotChannel → transform(process) →
  filter(hysteresis) → floatReadingChannel`. The filter drops a reading whose
  smoothed value moved less than `hysteresis-delta` from the last one passed on.
  The first reading always passes. A frame with no float is a failure: it is
  recorded for health and logged, and nothing is published.
- `DetectionController`: `GET /api/detection` returns the counts and the last reading
  (the calibration is on `/mgmt/info`). **Built (was Proposed):** `GET /api/detection/debug`
  returns the latest frame, whether or not a float was found in it, with the ROI (cyan),
  the dark bands found (orange ticks either side), the marks used (magenta), the float's
  box (green) and the row read (red) drawn on it. Lines
  only, no text, since text rendering needs fonts a slim container image may lack.
  Recalibrating after the camera moves means reading pixel rows off this image and
  editing `application.yml`.
- Metrics: timer `oiltank.float.detection.time`; summaries
  `oiltank.float.level.{raw,smoothed}`; counter `oiltank.float.detection.misses`.
- Health: UP if the latest frame yielded a reading, DOWN with the reason otherwise,
  UNKNOWN before the first. A reading beyond the end marks is clamped to 0 or 1, not
  failed. Details include `markShift`, `marksMatched` and the latest
  `dayNightComparison`.
- Info: the ROI, colour and infrared settings, marks, offset, smoothing, hysteresis and
  cross-check.

| Key (`oiltank.detection.*`) | Today's framing | Validation |
|---|---|---|
| `roi.x` / `roi.y` / `roi.width` / `roi.height` | `905` / `420` / `130` / `390` | > 0; must end above the base, which glows under IR |
| `color.hue-min` / `color.hue-max` | `15` / `40` | 0..180, min < max |
| `color.saturation-min` / `color.value-min` | `120` / `30` | 0..255; saturation-led, for dim frames too |
| `infrared.brightness-min` | `200` | 0..255 |
| `infrared.min-width` | `45` px | > 0; between the reflection's width and the float's |
| `infrared.max-gap` | `12` rows | ≥ 0; a mark's thickness |
| `min-area` | `150` px | > 0 |
| `marks.levels` | `[1.0, 0.75, 0.5, 0.25, 0.0]` | ≥ 2, in 0..1, decreasing top to bottom |
| `marks.rows` | `[454, 541, 629, 720, 787]` | one per level, increasing; the tracker's reference |
| `marks.tracking.enabled` / `max-shift` / `tolerance` | `true` / `60` / `6` rows | |
| `marks.tracking.min-matches` | `3` | 2..number of marks |
| `marks.tracking.min-contrast` / `min-spacing` / `float-rows` | `12` / `40` / `75` | |
| `cross-check.window` / `tolerance` | `30m` / `0.03` | |
| `reference-offset` | `20` px | From the float's top down to the reading line; fitted at ½ only |
| `smoothing-alpha` | `0.30` | in (0, 1] |
| `hysteresis-delta` | `0.003` | ≥ 0 |

The ROI, marks and offset have no meaningful defaults: they belong to one camera
position, so `application.yml` carries today's values with a comment saying so.

The gauge marks are fractions of the tank's volume, since a gauge is graduated for its
tank. Interpolating between them therefore already corrects for the horizontal tank's
shape, which retires the tank-geometry proposal under level/ unless finer correction
between marks proves necessary.

Test images (`src/test/resources/gauge/`) are masked: everything outside a box around
the gauge tube is flat grey, so they show nothing else of the house, while staying
1920×1080 so their coordinates match `application.yml` (the Spring tests replay them
with the real configuration). All read ½: `2026-10-20-half.jpg` (today,
day), `2026-10-20-half-night.jpg` (today, infrared), `2026-10-21-half-dawn.jpg` (dim colour), and `old-framing/2025-11-15-half.jpg`
(the earlier camera position, kept in a subdirectory so the test file source, which
replays `gauge/`, sees only frames matching today's calibration). They read 0.506 by
day, 0.497 at dawn and 0.492–0.497 by night with one offset. Since every frame shows ½, the offset is
fitted, not validated: frames at other levels are still needed.

### level/

Turns a reading into what a person cares about. Built 2026-10-21.

- `LevelService.toLevel(reading)` returns
  `TankLevel(percent = round(smoothed × 100), status, reading)`, clamped to 0..100. The
  status is LOW below `low-percent`, HIGH above `high-percent`, and NORMAL otherwise,
  the thresholds themselves included. It keeps the latest level.
- `LevelConfig` declares `levelFlow`: `floatReadingChannel → transform(toLevel) →
  tankLevelChannel`, logging each level at DEBUG under `oiltank.level`.
- `LevelController`: `GET /api/level` returns the latest `TankLevel` (404 before the
  first). This replaces the prototype's Bruno request "Tank → Get Tank Level".
- Metrics: gauge `oiltank.level.percent` (base unit `percent`), NaN until the first level
  rather than a misleading 0. The prototype used a summary; a gauge is the right type
  for a current value.
- Health: UP once a level exists, UNKNOWN before; percent, status and capture time in
  the details. A LOW tank is not an application fault, so the status goes in the
  details, never the health status.
- Info: the thresholds (configuration only; the latest level is in health and the API).

| Key (`oiltank.level.*`) | Default | Validation |
|---|---|---|
| `low-percent` | `20` | 0..100, below `high-percent` |
| `high-percent` | `95` | 0..100 |

The status has no hysteresis of its own. Detection's hysteresis (0.3 %) and smoothing
keep the level from jittering, but a tank sitting right at 20 % could still alternate
between 19 and 20, and so between LOW and NORMAL. This only matters once something acts
on the status (the proposed "tank low" binary sensor); a margin can be added then.

~~**Proposed:** a tank-geometry correction.~~ Retired: interpolating between the gauge's
own marks (see detection/) already maps float position to volume.

### publish/

Publishes to Home Assistant through MQTT discovery. Built 2026-10-21, following
iot-pipeline's publish feature rather than a Spring Integration outbound adapter, since
connection, retry and birth/last-will handling all need to be explicit.

- `HomeAssistantMessages` builds the messages, all under
  `<discovery-prefix>/sensor/<sensor-id>/`: `config` (discovery, retained), `state` (the
  percentage), `attributes` (`percentage`, `status`, `raw`, `smoothed`, `mode`,
  `timestampIso`; the prototype's `percentage` and `timestampIso` names are kept for
  existing templates) and `availability` (`online`/`offline`, retained).
- The prototype's identity is kept (decided 2026-10-21): `unique_id: oiltank_level`,
  device `identifiers: [oiltank_monitor]`, name "Oil Tank Level", device "Oil Tank
  Monitor", so the existing Home Assistant entity and its history carry over. Only the
  device's manufacturer and model change (`brulejr`, `oiltank-monitor`).
- `MqttPublisher` (behind `PublisherPort`) is a plain Paho client that connects on
  demand, with `offline` registered as its last will. Every connection first sends the
  discovery config and `online`, so after a reconnect of either side Home Assistant
  finds the sensor again; the prototype sent discovery once. On a clean shutdown it
  sends `offline` itself, since a broker publishes a last will only for a lost
  connection. Paho's automatic reconnect is off.
- **Availability (was Proposed), built:** Home Assistant shows the sensor as unavailable
  while the monitor is down, instead of a stale value.
- `PublishService` delivers each level as state then attributes. A message that fails is
  held, latest per topic, and everything after it too; the circuit opens and held
  messages are retried in order after a backoff that doubles up to `retry.max-backoff`,
  on a one-second tick. Holding matters here because detection passes a level on only
  when it changes, which can be hours apart. Held messages live in memory (see State
  and storage). With `enabled: false` messages are counted and dropped, and nothing
  connects; the Spring tests use that, rather than `@ConditionalOnProperty`, as
  iot-pipeline does.
- `PublishController`: `GET /api/publish` returns the counts and circuit state;
  `GET /api/publish/messages` the last message sent to each topic;
  `POST /api/publish/discovery` re-sends the discovery config.
- Health: DOWN while the circuit is open (messages held), UP otherwise, with the broker,
  counts, last publish time and last failure. UP proves nothing until the first publish,
  since the connection is made on demand.
- Info: broker URL, client id, QoS, retain, sensor and device ids, topics. No credentials.
- Verified live against the local Mosquitto: discovery, `online`, state `50` and the
  attributes on the first level, `offline` on shutdown; with the broker unreachable,
  health DOWN and both messages held. `MqttPublisherIT` covers birth and last will against
  a broker on localhost:1883 under a throwaway topic prefix, and skips without one.
- **Still Proposed:** a `binary_sensor` "Oil tank low" driven by `LevelStatus.LOW`. A Home
  Assistant automation can use the percentage meanwhile.

| Key (`oiltank.publish.*`) | Default | Notes |
|---|---|---|
| `enabled` | `${PUBLISH_ENABLED:true}` | Spring tests set `false` |
| `mqtt.url` | `${MQTT_URL:tcp://localhost:1883}` | Replaces `MQTT_HOST` + `MQTT_PORT` (decided 2026-10-21) |
| `mqtt.client-id` | `${MQTT_CLIENT_ID:oiltank-monitor}` | |
| `mqtt.username` / `mqtt.password` | `${MQTT_USERNAME:}` / `${MQTT_PASSWORD:}` | Blank means none |
| `mqtt.qos` | `1` | 0..2 |
| `mqtt.connect-timeout` / `completion-timeout` | `5s` / `10s` | |
| `retain-state` | `true` | State and attributes; discovery and availability are always retained |
| `retry.initial-backoff` / `max-backoff` | `1s` / `1m` | |
| `home-assistant.discovery-prefix` | `homeassistant` | |
| `home-assistant.sensor-id` / `sensor-name` | `oiltank_level` / `Oil Tank Level` | `[a-z0-9_]+` |
| `home-assistant.device-id` / `device-name` | `oiltank_monitor` / `Oil Tank Monitor` | |

## Cross-cutting: pipeline/

- `PipelineChannels.kt`: channel name constants.
- `PipelineConfig`: declares the `DirectChannel`s and the `pipelineErrorChannel`
  logging flow.
- `CameraService.captureNow()` (the capture API) sends downstream failures to
  `pipelineErrorChannel`, as the poller does, rather than failing the request: the
  capture worked, and the failing stage's health records the rest.
- There is no feature-level Datafill here. If one is needed later it is
  `PipelineDatafill` under `oiltank.pipeline`.

## State and storage

Decided: the application keeps no persistent storage. There is no database and no
state file.

- **History lives in Home Assistant.** Its recorder keeps every state change, and
  `state_class: measurement` gives the sensor long-term statistics. A store here
  would duplicate it.
- **The latest value lives on the broker.** State and attributes are published
  retained, so Home Assistant shows the last level across a restart of either side.
- **Calibration and thresholds are configuration**, in `application.yml` through the
  Datafills, not runtime data.
- **Runtime state is in memory only.** It is lost on restart:

| State | Owner | Effect of losing it |
|---|---|---|
| Latest `Snapshot` and capture stats | `CameraService` | `GET /api/camera/snapshot` returns 404 until the next capture |
| EMA value | `DetectionService` | Smoothing restarts from one raw reading |
| Last published smoothed value | `DetectionService` | The hysteresis filter passes the first reading |
| Latest `TankLevel` | `LevelService` | `GET /api/level` returns 404 until the next reading |
| Last message per topic | `PublishService` | `GET /api/publish` is empty until the next publish |

The only real cost is the restart of the smoothing: one noisy frame straight after
startup moves the published value more than it would later. With a 30 s interval
and α = 0.3, the EMA settles within a few minutes.

Decided for the first version: accept the settling time. Startup is unseeded.

**Deferred:** seed from the broker on startup, if the settling proves a problem
in practice. Before the first capture,
`PublishService` subscribes once to its own retained `state` and `attributes`
topics, takes `smoothed` from the attributes, and seeds the EMA and the last
published value in `DetectionService`. Then it unsubscribes. If nothing arrives
within a short timeout, startup goes ahead unseeded. This needs a subscription
in `publish/`, plus a seeding call on `DetectionService` that only the publish
config invokes.

When storage would become necessary:

- **Consumption rate, days-until-empty, refill detection.** These need history.
  Home Assistant can derive them from its own data with derivative and statistics
  helpers, so they stay out of this application unless that proves inadequate.
- **Keeping captures for retuning detection.** This would be a directory of JPEGs on
  a mounted volume, capped by count or age, behind `oiltank.camera.archive.*`. It is
  still not a database. Off by default.

## Observability

- Actuator at `/mgmt`: `health`, `info`, `metrics`, `integrationgraph`.
- `spring.integration.management.observation-patterns: "*"`, as in iot-pipeline,
  for per-channel and per-handler metrics.
- Logging under `oiltank.*` per stage, at DEBUG for each reading while the
  detection is being tuned.

## Testing

- Every `@SpringBootTest` sets `oiltank.camera.source=file` with
  `oiltank.camera.file.directory=src/test/resources/gauge` (real frames),
  and later `oiltank.publish.enabled=false`, so no test reaches a camera or a broker.
- `FfmpegSnapshotSourceTest` runs a stand-in shell script as ffmpeg, to stage
  success, failure, empty output and timeout.
- Real frames under `src/test/resources/gauge/`, named with the level the gauge
  showed. `GaugeReadingTest` reads each end to end with the application's detector
  and asserts it within ±0.03. This is the regression suite for detection.
  **Needs:** frames at levels other than ½.
- Unit tests for `DetectionService` (calibration, EMA, hysteresis), `LevelService`
  (rounding, thresholds), and `PublishService` (topics, payload JSON).
- `PipelineTopologyTest`, plus `ActuatorContributionTest` (every feature's indicator
  is UP and contributes info), as in iot-pipeline.
- **Proposed:** one end-to-end test that runs a fixture image through all four
  stages to a captured outbound message (`spring-integration-test` mock handler),
  with no broker.

## Deployment

- `docker/Dockerfile`, shared by `docker/{dev,local,prod}/compose.<usage>.yaml`,
  packages `build/libs/oiltank-monitor.jar` on `eclipse-temurin:21-jre` with Ubuntu's
  ffmpeg, layered as iot-pipeline's is, and runs as a system user `app` rather than
  1000:1000, as iot-pipeline's does. Built on the host that runs it; see `docker/README.md`.
- Secrets: decided 2026-10-22, plain environment variables from an env file kept out
  of git (`.env` locally, Ansible's in production), as iot-pipeline does, rather than
  the `configtree:` that was leaning. The only secret is the RTSP URL (plus broker
  credentials if any), and one convention across the two projects outweighs keeping
  it out of `docker inspect`. Compose passes full property names
  (`OILTANK_CAMERA_RTSP_URL`, `OILTANK_PUBLISH_MQTT_URL`), which outrank profile files.
- `dev` compose: a Mosquitto broker on localhost:1883 for runs from the IDE.
  `local`/`prod`: the app only, pointed at the broker `MQTT_URL` names (no default);
  `local` shares `dev`'s project name, so it can publish to that broker as
  `tcp://mosquitto:1883`. Verified 2026-10-22: the `local` container read the real
  camera (infrared, 48 %) and published to `dev`'s broker, with the URL's password in
  neither the logs nor the actuator endpoints.
- Bruno collection `bruno/oiltank-monitor/`: management (health, info, metrics,
  integration graph), camera, detection, level, publish.

## Implementation order

1. ~~`pipeline/` channels and error flow, plus the `ActuatorContributionTest` scaffold.~~ Done.
2. ~~`camera/` with `FileSnapshotSource` first, then `FfmpegSnapshotSource`.~~ Done; not yet
   tried against the real camera.
3. ~~`detection/` with the fixture-image tests.~~ Done, without OpenCV, by day and night.
4. ~~`level/`.~~ Done.
5. ~~`publish/` against a dev Mosquitto broker.~~ Done, verified against the local one.
6. `docker/` done; Bruno collection and the end-to-end test to follow.

## Open questions

1. ~~**Target hardware.**~~ Resolved 2026-10-22: it does not matter. `eclipse-temurin:21-jre`
   and Ubuntu's ffmpeg are both built for amd64 and arm64, so the one Dockerfile serves
   an x86-64 server or a Raspberry Pi.
2. ~~**Night.**~~ Resolved: an infrared detector reads the night frame (see detection/).
3. **Capture strategy.** Keep a new ffmpeg process per snapshot (simple; measured at 2.6 s
   of RTSP negotiation each time), or use the camera's HTTP snapshot endpoint if it
   has one? 30 s polling makes the ffmpeg cost acceptable.
4. ~~**Proposed items above.**~~ Availability built; the low-level binary sensor stays
   Proposed.
5. **Detection confidence.** Should a reading that jumps implausibly (for example
   more than 10 % in one interval, when a tank only drains slowly) be rejected
   rather than smoothed in?
6. ~~**MQTT env vars.**~~ Decided: a single `MQTT_URL`, as iot-pipeline does.
7. ~~**Sensor identity.**~~ Decided: the prototype's `oiltank_level` and `oiltank_monitor`
   are kept.
8. **Calibration by assertion** (Option B, deferred until a frame at another level
   exists): `POST /api/detection/calibrate {"level": 0.75}` fits the reference offset
   from the current frame and returns the config snippet to paste, keeping the
   application storage-free. With two or more levels it could fit an offset per mark,
   if a single offset proves not to hold along the gauge.
