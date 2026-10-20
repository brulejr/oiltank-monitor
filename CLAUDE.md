# oiltank-monitor

Kotlin / Spring Boot / Spring Integration service that reads a heating-oil tank's
float gauge from an RTSP camera snapshot (ffmpeg + OpenCV), turns the float position
into a calibrated, smoothed tank level, and publishes it to Home Assistant over MQTT
discovery. It rewrites the prototype `brulejr/monitor-oiltank` using the conventions
of `brulejr/iot-pipeline`. `docs/DESIGN.md` is the design doc: read it before
changing pipeline structure.

Status: skeleton only (build, actuator, context-load test). No feature is ported yet.

@.claude/CONVENTIONS.md

## Layout

Planned: one package per feature, each a pipeline stage, under `io.brulejr.oiltank`:

- `camera/`: snapshot capture (ffmpeg over RTSP) and the polling schedule
- `detection/`: OpenCV float detection, calibration, smoothing, hysteresis
- `level/`: tank level and thresholds
- `publish/`: Home Assistant MQTT discovery and state

Following the conventions, each feature has exactly one `*Config` declaring all its
beans and flows, a validated `*Datafill` bound under `oiltank.*`, a `*Service`
holding its logic, a `*Controller` under `api/`, a `*HealthIndicator` and a
`*InfoContributor`. Cross-cutting pieces (channel names, the error flow) go in
`pipeline/`.

## Commands

```
./gradlew build          # compile + test
./gradlew test           # tests only
./gradlew bootRun        # app on :5001, actuator at /mgmt
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
- Spring tests must not reach a broker or camera: set `oiltank.publish.enabled=false`
  in any `@SpringBootTest` once publishing exists.
- Every `.kt` file under `src/` opens with the MIT license header: a `/* */`
  block comment (not KDoc, which would attach to the first declaration) above
  the `package` line, carrying the copyright line and
  `SPDX-License-Identifier: MIT`. Copy it from any existing file when adding
  one. Gradle build scripts are exempt.
