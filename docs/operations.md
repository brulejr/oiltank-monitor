# Operations and observability

How to see what the running monitor is doing: Actuator, the REST API, and the logs
that follow each reading through the stages. The stages and the channels joining
them are laid out in [Design](DESIGN.md#pipeline).

## Actuator

Actuator exposes `health`, `info`, `metrics` and `integrationgraph` under
`http://localhost:5001/mgmt`. Micrometer observations are enabled for every Spring
Integration channel and handler, so each stage is individually observable.

Every stage contributes to both `health` and `info`. Info shows each stage's
settings, with credentials redacted; health shows how it is doing:

| Indicator | UP when | Details |
| --- | --- | --- |
| `camera` | The last capture succeeded, within three intervals | Captures, failures, last capture, last failure |
| `detection` | The latest frame gave a reading | Counts, last reading, `markShift`, `marksMatched`, `dayNightComparison` |
| `level` | A level exists | Percent, status, capture time |
| `publish` | No messages are held | Broker, counts, last publish, last failure |

Each is UNKNOWN until its first reading. A LOW tank leaves `level` UP: the status is
a detail, not a fault. `publish` UP proves little before the first publish, since
the connection is made on demand.

```bash
curl -s localhost:5001/mgmt/health | jq
```

## REST API

Each stage also has an endpoint of its own. The API has no authentication, and the
snapshots show what the camera sees; keep port 5001 on a trusted network.

| Endpoint | What it gives |
| --- | --- |
| `GET /api/camera` | Capture counts, the last capture and the last failure |
| `GET /api/camera/snapshot` | The latest frame as a JPEG |
| `POST /api/camera/snapshot` | A capture now, run through the pipeline — see [Camera](camera.md) |
| `GET /api/detection` | Detection counts, the last reading, mark alignment and day/night comparison |
| `GET /api/detection/debug` | The latest frame with the calibration drawn on it — see [Detection](detection.md#calibrating) |
| `GET /api/level` | The latest level and its status |
| `GET /api/publish`, `GET /api/publish/messages` | Publication counts and circuit state, and the last message per topic |
| `POST /api/publish/discovery` | Sends the discovery config again — see [Home Assistant](home-assistant.md) |

## Logs

Each stage logs under an `oiltank.*` category, at DEBUG by default while detection
is being tuned, since the log is the only record of each reading:

| Category | Level | What it shows |
| --- | --- | --- |
| `oiltank.detection` | DEBUG | Where the float was found, by which method, and the raw and smoothed readings |
| `oiltank.level` | DEBUG | The percentage and status passed on |
| `oiltank.publish` | DEBUG | Each message published; WARN when the broker fails, INFO when held messages are delivered |
| `oiltank.errors` | WARN | Any stage's failure, one line without a stack trace |

A reading that changed the level produces:

```
oiltank.detection : float read at y=608 (COLOR): raw=0.506 smoothed=0.503
oiltank.level     : tank at 50% (NORMAL)
oiltank.publish   : published 50 to homeassistant/sensor/oiltank_level/state (retained)
```

One that did not stops after `oiltank.detection`, dropped by the hysteresis filter.
A failure is one line, whatever the stage:

```
oiltank.errors    : SnapshotException: ffmpeg exited with 1: rtsp://***@192.168.1.20:554/stream1: Connection refused
```

The poller carries on after a failure, so an unreachable camera produces one such
line per interval. Once detection is stable, set `logging.level.oiltank=INFO` for
quieter logs.

## Metrics

Besides the per-channel observations, under `/mgmt/metrics/<name>`:

| Metric | Type | What it measures |
| --- | --- | --- |
| `oiltank.float.detection.time` | Timer | Time to read a frame |
| `oiltank.float.detection.misses` | Counter | Frames without a float |
| `oiltank.float.level.raw`, `oiltank.float.level.smoothed` | Summary | Readings before and after smoothing |
| `oiltank.float.mode.difference` | Summary | The day/night cross-check's differences |
| `oiltank.level.percent` | Gauge | The current level; NaN before the first |

## Troubleshooting

**Startup fails naming `OILTANK_CAMERA_RTSP_URL`.** The camera source is `ffmpeg`
and no URL is set; see [Configuration](configuration.md#local-overrides).

**`camera` is DOWN.** Its last failure says why: ffmpeg not on `PATH`, the camera
unreachable, the credentials refused, or a capture that timed out. Try the same
capture by hand:

```bash
ffmpeg -rtsp_transport tcp -i "$OILTANK_CAMERA_RTSP_URL" -frames:v 1 frame.jpg
```

**`detection` is DOWN with "no float found" or "has the camera moved?".** Fetch
`GET /api/detection/debug` and look at where the box and the marks fall on the
gauge. If the camera has moved, [recalibrate](detection.md#calibrating). If it has
not, and the frame was taken in unusual light, save it: it is a candidate for the
[test frames](detection.md#test-frames).

**`dayNightComparison` is flagged.** Day and night read differently, usually
because the reference offset suits one better than the other. Compare a day and a
night debug image.

**`publish` is DOWN.** The broker cannot be reached; messages are held and retried,
and `GET /api/publish` shows when next. Check `MQTT_URL` and the credentials.

**No sensor in Home Assistant.** Check that the MQTT integration is set up with
discovery enabled and the same discovery prefix, and that the config message is on
the broker (see [Home Assistant](home-assistant.md#publication-and-retries));
`POST /api/publish/discovery` sends it again.
