# oiltank-monitor

Kotlin / Spring Boot / Spring Integration service that reads a heating-oil tank's
float gauge from an RTSP camera snapshot (ffmpeg + OpenCV), turns the float position
into a calibrated, smoothed tank level, and publishes it to Home Assistant over MQTT
discovery. It rewrites the prototype `brulejr/monitor-oiltank` using the conventions
of `brulejr/iot-pipeline`. `docs/DESIGN.md` is the design doc: read it before
changing pipeline structure.

Status: `pipeline/` and `camera/` work (snapshots by ffmpeg or from a directory, polled
every 30 s). Detection, level and publish are not started; until detection exists,
`snapshotSinkFlow` in `PipelineConfig` logs and drops each snapshot.

@.claude/CONVENTIONS.md

## Layout

One package per feature, each a pipeline stage, under `io.brulejr.oiltank`:

- `camera/`: snapshot capture (ffmpeg over RTSP, or replayed files) and the polling schedule
- `detection/` (planned): OpenCV float detection, calibration, smoothing, hysteresis
- `level/` (planned): tank level and thresholds
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
CAMERA_SOURCE=file OILTANK_CAMERA_FILE_DIRECTORY=src/test/resources/snapshots ./gradlew bootRun
                         # without a camera: replays the images in that directory
```

## Notes

- Java 21 toolchain. Jackson is the new `tools.jackson` 3.x namespace, not
  `com.fasterxml.jackson`.
- Server port is 5001, not the Spring Boot default of 8080, which collides with
  other local Docker containers.
- Paho must stay an explicit dependency; it is optional in
  `spring-integration-mqtt`.
- OpenCV (`org.bytedeco:opencv-platform`) is not a dependency yet; it arrives with
  `detection/`.
- The boot jar excludes `application-local.yml`, which holds development
  credentials.
- Spring tests must not reach a broker or camera: every `@SpringBootTest` sets
  `oiltank.camera.source=file` and `oiltank.camera.file.directory=src/test/resources/snapshots`,
  and will set `oiltank.publish.enabled=false` once publishing exists.
- The RTSP URL carries the camera's credentials. Never log or report it raw: use
  `redactCredentials` / `redactUrlIn` (`camera/Redaction.kt`), including on ffmpeg's
  stderr, which echoes the URL.
- Every `.kt` file under `src/` opens with the MIT license header: a `/* */`
  block comment (not KDoc, which would attach to the first declaration) above
  the `package` line, carrying the copyright line and
  `SPDX-License-Identifier: MIT`. Copy it from any existing file when adding
  one. Gradle build scripts are exempt.
