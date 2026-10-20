# Oil Tank Monitor: Design Notes

Status: draft for review. `pipeline/` and `camera/` are implemented; detection,
level and publish are not. Items marked
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
- `detection/FloatReading(raw: Double, calibrated: Double, smoothed: Double, capturedAt: Instant)`.
  All three values are relative heights in 0..1.
- `level/TankLevel(percent: Int, status: LevelStatus, reading: FloatReading)`, where
  `LevelStatus` is `LOW | NORMAL | HIGH`.

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

Turns a snapshot into a smoothed relative float height.

- `FloatDetector` (port), implemented by `OpenCvFloatDetector`. It ports the
  prototype's algorithm: grayscale, histogram equalization, a centred ROI, 5×5
  Gaussian blur, Otsu threshold (inverted when the ROI mean is above 127), the
  largest external contour, then `1 - centerY / roiHeight`, clamped to 0..1.
  Returns `null` when no contour is found.
- The ROI becomes configuration instead of constants: width, height, and vertical
  centre as a fraction of frame height (prototype values are 300, 500 and 0.55).
- `DetectionService.process(snapshot)` detects, calibrates
  (`slope × raw + intercept`, clamped), applies EMA smoothing, and returns a
  `FloatReading`. It holds the EMA state and the last *published* value.
- `DetectionConfig` declares the flow `snapshotChannel → transform(process) →
  filter(hysteresis) → floatReadingChannel`. The filter drops a reading whose
  smoothed value moved less than `hysteresis-delta` from the last one passed on.
  The first reading always passes.
- `DetectionController`: `GET /api/detection` returns the last reading (raw,
  calibrated, smoothed) and the active calibration. **Proposed:**
  `GET /api/detection/debug` returns the last snapshot with the ROI and contour drawn
  on it, for tuning the ROI.
- Metrics (Micrometer, names kept from the prototype): timer
  `oiltank.float.detection.time`; summaries `oiltank.float.level.{raw,calibrated,smoothed}`.
- Health: UP if the last detection found a float. DOWN with the reason (no
  contour, decode failure) otherwise.
- Info: calibration, smoothing, hysteresis, ROI, detection and miss counts.

| Key (`oiltank.detection.*`) | Default | Validation |
|---|---|---|
| `calibration.slope` | `1.0` | non-zero |
| `calibration.intercept` | `0.0` | |
| `smoothing-alpha` | `0.30` | in (0, 1] |
| `hysteresis-delta` | `0.003` | ≥ 0 |
| `roi.width` / `roi.height` | `300` / `500` | > 0 |
| `roi.center-y` | `0.55` | in (0, 1) |

OpenCV comes from `org.bytedeco:opencv-platform`, added with this feature. The full
platform artifact bundles natives for every OS, which makes it about 300 MB. The
build should pull only the target platform's natives (`javacpp.platform`); see the
open questions.

### level/

Turns a relative height into what a person cares about.

- `LevelService.toLevel(reading)` returns
  `TankLevel(percent = round(smoothed × 100), status, reading)`. The status is LOW
  below `low-percent` and HIGH above `high-percent`. It keeps the latest level.
- `LevelConfig` declares `floatReadingChannel → transform(toLevel) → tankLevelChannel`.
- `LevelController`: `GET /api/level` returns the latest `TankLevel` (404 before the
  first). This replaces the prototype's Bruno request "Tank → Get Tank Level".
- Metrics: gauge `oiltank.level.percent`. The prototype used a summary; a gauge
  is the right type for a current value.
- Health: always UP once a level exists. A LOW tank is not an application fault,
  so the status goes in the details, not the health status.
- Info: thresholds, latest percent and status.

| Key (`oiltank.level.*`) | Default | Validation |
|---|---|---|
| `low-percent` | `20` | 0..100, below `high-percent` |
| `high-percent` | `95` | 0..100 |

**Proposed:** a tank-geometry correction. A horizontal or oval tank does not hold
volume linearly with float height. The prototype treats height as percent. A
piecewise-linear `height → percent` table in the Datafill, empty by default
(linear), would allow it later without changing the pipeline.

### publish/

Publishes to Home Assistant through MQTT discovery.

- `PublishService` builds the messages: the discovery config, the state (percent),
  and the attributes JSON (percent, status, raw, calibrated, smoothed, `timestampIso`).
- `PublishConfig` declares the Paho client factory, the outbound adapter
  (`Mqtt.outboundChannelAdapter`), and the flow
  `tankLevelChannel → split into state + attributes → mqttOutbound`. The discovery
  message is sent on startup and again on every reconnect, retained, so Home
  Assistant recovers after either side restarts. The prototype sent it once.
- Topics: `<discovery-prefix>/sensor/<sensor-id>/{config,state,attributes}`.
  Discovery config: `unit_of_measurement: "%"`, `state_class: measurement`,
  `icon: mdi:gauge`, `json_attributes_topic`, and the device block
  `{identifiers: [oiltank_monitor], name: "Oil Tank Monitor", manufacturer, model}`.
- **Proposed:** an availability topic `<prefix>/sensor/<id>/availability`, with
  `online` published on connect and `offline` as the Paho last will. Home Assistant
  then shows the sensor as unavailable when the monitor is down, instead of a stale
  value.
- **Proposed:** a second entity, a `binary_sensor` "Oil tank low" driven by
  `LevelStatus.LOW`, so Home Assistant automations need no threshold of their own.
- `PublishController`: `POST /api/publish/discovery` re-sends the discovery config.
  `GET /api/publish` returns the topics and the last message published to each.
- Health: UP when the client is connected, DOWN otherwise. Last publish time
  and error in the details.
- Info: broker URL, client id, topics, QoS, retain.

| Key (`oiltank.publish.*`) | Default | Notes |
|---|---|---|
| `enabled` | `true` | `@ConditionalOnProperty`; Spring tests set `false` |
| `mqtt.url` | `${MQTT_URL:tcp://localhost:1883}` | Replaces `MQTT_HOST` + `MQTT_PORT` |
| `mqtt.client-id` | `oiltank-monitor` | |
| `mqtt.username` / `mqtt.password` | `${MQTT_USERNAME}` / `${MQTT_PASSWORD}` | Optional |
| `qos` | `1` | 0..2 |
| `retain` | `true` | For state and attributes; discovery is always retained |
| `home-assistant.discovery-prefix` | `homeassistant` | |
| `home-assistant.sensor-id` | `oiltank_level` | `[a-z0-9_]+` |
| `home-assistant.device-name` | `Oil Tank Monitor` | |

## Cross-cutting: pipeline/

- `PipelineChannels.kt`: channel name constants.
- `PipelineConfig`: declares the `DirectChannel`s and the `pipelineErrorChannel`
  logging flow. Until detection exists it also holds `snapshotSinkFlow`, which
  logs each snapshot at DEBUG and drops it. Detection replaces it, because a direct
  channel with two subscribers load-balances between them.
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
  `oiltank.camera.file.directory=src/test/resources/snapshots` (a synthetic image),
  and later `oiltank.publish.enabled=false`, so no test reaches a camera or a broker.
- `FfmpegSnapshotSourceTest` runs a stand-in shell script as ffmpeg, to stage
  success, failure, empty output and timeout.
- Fixture images under `src/test/resources/snapshots/`, labelled with their
  expected relative height. `OpenCvFloatDetectorTest` asserts each one is within a
  tolerance. These are the regression suite for the detection algorithm.
  **Needs:** real captures from the camera.
- Unit tests for `DetectionService` (calibration, EMA, hysteresis), `LevelService`
  (rounding, thresholds), and `PublishService` (topics, payload JSON).
- `PipelineTopologyTest`, plus `ActuatorContributionTest` (every feature's indicator
  is UP and contributes info), as in iot-pipeline.
- **Proposed:** one end-to-end test that runs a fixture image through all four
  stages to a captured outbound message (`spring-integration-test` mock handler),
  with no broker.

## Deployment

- `docker/Dockerfile`, shared by `docker/{dev,local,prod}/compose.<usage>.yaml`,
  packages `build/libs/oiltank-monitor.jar` on `eclipse-temurin:21-jre`, runs as
  1000:1000, and includes ffmpeg.
- Secrets: the prototype's entrypoint reads `*_FILE` Docker secrets into env vars.
  The rewrite can do the same, or use Spring Boot's `configtree:` import on
  `/run/secrets`, which needs no shell script. **Leaning:** `configtree:`.
- `dev` compose: a Mosquitto broker for local runs. `local`/`prod`: the app only,
  pointed at the real broker.
- Bruno collection `bruno/oiltank-monitor/`: management (health, info, metrics,
  integration graph), camera, detection, level, publish.

## Implementation order

1. ~~`pipeline/` channels and error flow, plus the `ActuatorContributionTest` scaffold.~~ Done.
2. ~~`camera/` with `FileSnapshotSource` first, then `FfmpegSnapshotSource`.~~ Done; not yet
   tried against the real camera.
3. `detection/` with OpenCV and the fixture-image tests.
4. `level/`.
5. `publish/` against a dev Mosquitto broker.
6. `docker/`, Bruno collection, and the end-to-end test.

## Open questions

1. **Target hardware.** Which CPU architecture runs this (x86-64 server, Raspberry
   Pi / ARM64)? This decides which OpenCV natives to bundle, the base image, and
   whether a static ffmpeg or the distro package is used.
2. **Capture strategy.** Keep a new ffmpeg process per snapshot (simple, about 1–3 s
   of RTSP negotiation each time), or use the camera's HTTP snapshot endpoint if it
   has one? 30 s polling makes the ffmpeg cost acceptable.
3. **Proposed items above:** the debug overlay endpoint, the tank-geometry table,
   the availability topic, and the low-level binary sensor. Which ones are in scope
   for the first version?
4. **Night and lighting.** Does the camera's IR mode change the image enough that
   detection needs a second calibration, or a "no confident reading" outcome that
   skips publishing?
5. **Detection confidence.** Should a reading that jumps implausibly (for example
   more than 10 % in one interval, when a tank only drains slowly) be rejected
   rather than smoothed in?
6. **MQTT env vars.** Switch from `MQTT_HOST`/`MQTT_PORT` to a single `MQTT_URL`,
   as iot-pipeline does? This changes the deployment's env.
7. **Sensor identity.** Keep `oiltank_level` and the `oiltank_monitor` device
   identifier so the existing Home Assistant entity and its history carry over?
   **Leaning:** yes.
