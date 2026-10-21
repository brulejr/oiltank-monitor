# oiltank-monitor

Kotlin / Spring Boot / Spring Integration service that reads a heating-oil tank's
float gauge from an RTSP camera snapshot (ffmpeg, then colour or infrared detection),
turns the float position into a calibrated, smoothed tank level, and publishes it to
Home Assistant over MQTT discovery. It rewrites the prototype `brulejr/monitor-oiltank` using the conventions
of `brulejr/iot-pipeline`. `docs/DESIGN.md` is the design doc: read it before
changing pipeline structure.

Status: `pipeline/`, `camera/`, `detection/` and `level/` work: snapshots by ffmpeg or
from a directory, polled every 30 s, the gauge read by day (colour) and by night
(infrared), and the reading turned into a percentage with a LOW/NORMAL/HIGH status.
Publish is not started; until it exists, `tankLevelSinkFlow` in `PipelineConfig` logs
and drops each level.

@.claude/CONVENTIONS.md

## Layout

One package per feature, each a pipeline stage, under `io.brulejr.oiltank`:

- `camera/`: snapshot capture (ffmpeg over RTSP, or replayed files) and the polling schedule
- `detection/`: finding the float (colour by day, brightness by night), calibration
  against the gauge's marks, smoothing, hysteresis
- `level/`: tank level as a percentage, and its status against the thresholds
- `publish/` (planned): Home Assistant MQTT discovery and state

Following the conventions, each feature has exactly one `*Config` declaring all its
beans and flows, a validated `*Datafill` bound under `oiltank.*`, a `*Service`
holding its logic, a `*Controller` under `api/`, a `*HealthIndicator` and a
`*InfoContributor`. Cross-cutting pieces go in `pipeline/`: `PipelineChannels.kt`
names the channels joining the stages, `PipelineConfig` declares them and the error
log. `PipelineTopologyTest` asserts the wiring against the graph Spring Integration
builds; update its expected map when a stage is rewired.

## Commands

```
./gradlew build          # compile + test
./gradlew test           # tests only
./gradlew bootRun        # app on :5001, actuator at /mgmt; needs OILTANK_CAMERA_RTSP_URL
CAMERA_SOURCE=file OILTANK_CAMERA_FILE_DIRECTORY=src/test/resources/gauge ./gradlew bootRun
                         # without a camera: replays the images in that directory
```

## Notes

- Java 21 toolchain. Jackson is the new `tools.jackson` 3.x namespace, not
  `com.fasterxml.jackson`.
- Server port is 5001, not the Spring Boot default of 8080, which collides with
  other local Docker containers.
- Paho must stay an explicit dependency; it is optional in
  `spring-integration-mqtt`.
- Detection is plain Kotlin over `BufferedImage`, deliberately without OpenCV
  (see `docs/DESIGN.md`). Its ROI, mark rows and reference offset in `application.yml`
  belong to one camera position. `MarkTracker` follows small drift by shifting the
  configured mark rows; it never replaces them with what it finds, since the float can
  pass for a mark. After a bigger move, re-measure from `GET /api/detection/debug`, and
  update `Fixtures.currentDatafill` to match.
- `GaugeReadingTest` is the detection regression suite: real frames in
  `src/test/resources/gauge/`, each with the level the gauge showed. Add every new
  frame at a known level there. `gauge/` holds only frames matching the current
  calibration, since the Spring tests replay it; others go in a subdirectory. Mask every
  frame before adding it (grey outside a box around the tube, still 1920x1080): the
  repo must not carry pictures of the house.
- The boot jar excludes `application-local.yml`, which holds development
  credentials.
- Spring tests must not reach a broker or camera: every `@SpringBootTest` sets
  `oiltank.camera.source=file` and `oiltank.camera.file.directory=src/test/resources/gauge`,
  and will set `oiltank.publish.enabled=false` once publishing exists.
- The RTSP URL carries the camera's credentials. Never log or report it raw: use
  `redactCredentials` / `redactUrlIn` (`camera/Redaction.kt`), including on ffmpeg's
  stderr, which echoes the URL.
- Every `.kt` file under `src/` opens with the MIT license header: a `/* */`
  block comment (not KDoc, which would attach to the first declaration) above
  the `package` line, carrying the copyright line and
  `SPDX-License-Identifier: MIT`. Copy it from any existing file when adding
  one. Gradle build scripts are exempt.
