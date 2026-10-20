# Plan: Scaffold `oiltank-monitor` (Kotlin / Spring Boot)

Status: approved and implemented 2026-10-20. Feature design continues in `docs/DESIGN.md`.

## Context
`~/src/prototypes/oiltank-monitor` is empty. This project will rewrite the prototype
[`brulejr/monitor-oiltank`](https://github.com/brulejr/monitor-oiltank): an RTSP camera snapshot is
taken via ffmpeg, OpenCV finds the gauge float, the reading is calibrated and smoothed into a tank
level, and the level is published to Home Assistant over MQTT. The new project follows the layout and
conventions of [`brulejr/iot-pipeline`](https://github.com/brulejr/iot-pipeline): Spring Boot 4,
Gradle Kotlin DSL with a version catalog, feature packages that each have a `*Config`, `*Datafill`,
`*Service`, `*HealthIndicator` and `*InfoContributor`, explicit `@Bean` wiring, an SPDX license header,
and actuator endpoints under `/mgmt` on port 5001.

**Scope:** only the buildable, runnable skeleton (build, tooling, conventions, app class, config,
smoke test). Porting the prototype's features (camera, detection, level, publish) comes in later
steps; CLAUDE.md lists them as the planned layout.

Decisions:
- Build tool: Gradle Kotlin DSL
- Starters: Web (MVC), Actuator, Spring Integration and MQTT
- Group `io.brulejr`, base package `io.brulejr.oiltank`, artifact `oiltank-monitor`
- Layout: modeled on iot-pipeline

## Files to create (repo root)

**Build**
- `settings.gradle.kts`: `rootProject.name = "oiltank-monitor"`
- `gradle/libs.versions.toml`: copied from iot-pipeline but trimmed: kotlin `2.3.21`, spring-boot `4.1.1`,
  spring-dependency-management `1.1.7`, paho `1.2.5`; plugins kotlin-jvm, kotlin-spring, spring-boot,
  spring-dependency-management; library `paho-mqttv3`. No webjars yet.
- `build.gradle.kts`: same shape as iot-pipeline's: group `io.brulejr`, version `0.0.1-SNAPSHOT`, Java 21
  toolchain, mavenCentral. Dependencies:
  - `spring-boot-starter-webmvc`, `-actuator`, `-integration`, `-jackson`
  - `spring-integration-mqtt` plus explicit `libs.paho.mqttv3`, with the comment that Paho is
    optional in `spring-integration-mqtt`
  - `tools.jackson.module:jackson-module-kotlin`, `kotlin-reflect`
  - tests: `spring-boot-starter-test`, `spring-integration-test`, `kotlin-test-junit5`,
    `junit-platform-launcher`

  Compiler args `-Xjsr305=strict -Xannotation-default-target=param-property`; JUnit platform;
  `jar` disabled; `bootJar` named `oiltank-monitor.jar` with `application-local.yml` excluded.
  OpenCV (`org.bytedeco:opencv-platform`) is **not** added yet. It is large and belongs with the
  detection feature.
- Gradle wrapper 9.8.1: generated with the local `gradle wrapper --gradle-version 9.8.1`
  (creates `gradlew`, `gradlew.bat` and `gradle/wrapper/*`).

**Repo hygiene** (copied from iot-pipeline with project-specific lines adapted)
- `.editorconfig` and `.gitattributes`: copied verbatim.
- `.gitignore`: same file without the InfluxDB lines.
- `LICENSE`: MIT, © 2026 Jon Brule.
- `.claude/CONVENTIONS.md`: copied verbatim from iot-pipeline.
- `CLAUDE.md`: project summary, `@.claude/CONVENTIONS.md`, and a Layout section with the planned feature
  packages `camera/` (snapshot capture and scheduling), `detection/` (OpenCV float detection,
  calibration, smoothing, hysteresis), `level/` (tank level and thresholds) and `publish/` (Home
  Assistant MQTT discovery and state). Also Commands (`./gradlew build`, `bootRun` on :5001), and Notes
  (Java 21, `tools.jackson` namespace, port 5001, explicit Paho, license header rule).
- `README.md`: short description and how to build and run.
- `git init` (no commit).

**Source**
- `src/main/kotlin/io/brulejr/oiltank/OiltankMonitorApplication.kt`: SPDX header (see below),
  `@SpringBootApplication`, `@ConfigurationPropertiesScan`, `main`.
- `src/main/resources/application.yml`: `spring.application.name: oiltank-monitor`,
  `spring.profiles.active: ${PROFILE:local}`, `server.port: ${SERVER_PORT:5001}` with the 8080 comment,
  `info.app.*`, and a `management` block copied from iot-pipeline (base-path `/mgmt`, exposing
  health, info, metrics and integrationgraph; `show-details: always`; build, env, java and os info).
  It also holds an `oiltank:` root that later features' Datafills will bind to.
- `src/test/kotlin/io/brulejr/oiltank/OiltankMonitorApplicationTests.kt`: `@SpringBootTest` context-load
  smoke test.

## Verification
1. `./gradlew build`: compiles and the context-load test passes.
2. `./gradlew bootRun`, then `curl localhost:5001/mgmt/health` returns `{"status":"UP",...}` and
   `curl localhost:5001/mgmt/info` shows the app name.
3. `ls build/libs` shows only `oiltank-monitor.jar`.

---

# Reference context

## Local environment
- JDK 21 (sdkman), Gradle on PATH (sdkman), Maven at `/usr/bin/mvn`. No `gh` CLI; GitHub is reached
  with `curl` against `api.github.com` and `raw.githubusercontent.com`.

## iot-pipeline conventions to follow
Source: `.claude/CONVENTIONS.md` and `CLAUDE.md` in iot-pipeline.
- One top-level type per file, named after it. Related top-level functions may share a file named
  for their purpose.
- Organize packages by feature, never by layer (no top-level `controllers/`, `services/` or
  `repositories/`). Cross-cutting pieces go in their own package (iot-pipeline uses `pipeline/`).
- Each feature has:
  - exactly one `@Configuration` `*Config` declaring all its beans and flows as `@Bean` methods
  - a `@ConfigurationProperties` data class `*Datafill`, validated with `require(...)` in `init`.
    Datafills are the only consumers of `application.yml`; `@Value` is never used.
    `@ConditionalOnProperty` is allowed only to switch a feature on or off.
  - a `*Service` holding the logic, registered in the Config and never annotated `@Service`
  - a `@RestController` `*Controller` under `api/`
  - a `*HealthIndicator` (`org.springframework.boot.health.contributor.HealthIndicator`) and a
    `*InfoContributor`, both registered as beans in the Config
- `@Component` and `@Service` are never used.
- Jackson 3 uses the `tools.jackson` namespace, not `com.fasterxml.jackson`.
- Server port 5001 (8080 collides with local Docker containers). Actuator lives at `/mgmt`.
- Spring tests switch off anything that reaches a broker (for example `*.publish.enabled=false`).
- iot-pipeline also has: an `ActuatorContributionTest` asserting every feature's health indicator is
  UP and that it contributes info, Bruno collections under `bruno/<project>/`, and Docker content
  under `docker/{dev,local,prod}/compose.<usage>.yaml` with a shared `docker/Dockerfile` that packages
  the boot jar.
- Every `.kt` file under `src/` starts with this header (a block comment, not KDoc), placed above
  `package`:
  ```kotlin
  /*
   * Copyright (c) 2026 Jon Brule
   *
   * Licensed under the MIT License; see LICENSE in the project root for the full
   * license text.
   *
   * SPDX-License-Identifier: MIT
   */
  ```
- Versions in iot-pipeline: Kotlin 2.3.21, Spring Boot 4.1.1, dependency-management 1.1.7, Paho
  1.2.5, Gradle 9.8.1.

## Prototype (monitor-oiltank) behaviour to port later
The prototype uses package `io.jrb.labs.monitor.oiltank`, Spring Boot 3.5.7, and the private
`ksb-commons` library (`EventBus`, `SystemEventBus`, `ControllableService`). It also uses `@Service` and
`@Component`. The rewrite drops all three: features are wired explicitly, and Spring Integration
channels can replace the event bus.

Pipeline (sealed `OilEvent`):
`SnapshotRequested` → `SnapshotReceived(bytes)` → `FloatPositionDetected(FloatPosition(relativeHeight))`
→ `LevelCalculated(TankLevel(percent))`.

1. **camera / ingestion**: `@Scheduled(fixedDelay = application.camera.snapshot-interval-ms)` emits a
   snapshot request. The capture runs
   `ffmpeg -hide_banner -loglevel error -rtsp_transport tcp -i <rtspUrl> -frames:v 1 -f mjpeg pipe:1`,
   rejects a non-zero exit, empty output, or output `ImageIO` cannot read, and writes a debug copy to
   `/tmp/oiltank-latest.jpg`. Config: `snapshot-url` (`OILTANK_CAMERA_RTSP_URL`, a Tapo camera),
   `snapshot-interval-ms: 30000`.
2. **detection**: `FloatDetector.detect(bytes)` uses OpenCV (`org.bytedeco:opencv-platform:4.9.0-1.5.10`):
   it converts to grayscale and equalizes the histogram. The ROI is 300×500, centred horizontally,
   with its vertical centre at 55% of frame height. Then a 5×5 Gaussian blur and an Otsu threshold
   (inverted if the mean is above 127). The largest external contour is the float, and its
   bounding-rect centre gives `1 - centerY/roiHeight`, clamped to 0..1.
   `FloatDetectionService` then:
   - calibrates (`slope * raw + intercept`, clamped)
   - applies EMA smoothing (`alpha`)
   - publishes only if the change since the last published value is at least `hysteresis-delta`

   Micrometer metrics: timer `oiltank.float.detection.time`; summaries
   `oiltank.float.level.{raw,calibrated,smoothed,percent}`. Config (`oiltank.detection`):
   calibration-slope 1.0, calibration-intercept 0.0, smoothing-alpha 0.30, hysteresis-delta 0.003.
3. **level / processing**: `TankLevel(percent = (relativeHeight * 100).toInt())`. Thresholds
   `application.level.minPercent: 20`, `maxPercent: 95` are defined but unused. Bug to avoid: the
   prototype subscribes in `onStop` and cancels in `onStart`, the wrong way round.
4. **publish**: Home Assistant MQTT discovery. Topics come from `discovery-prefix` (`homeassistant`)
   and `sensor-id` (`oiltank_level`):
   - `<prefix>/sensor/<id>/config`: discovery payload, retained, published once
   - `<prefix>/sensor/<id>/state`: the percentage
   - `<prefix>/sensor/<id>/attributes`: JSON with percentage, raw, calibrated, smoothed and
     timestampIso

   Discovery config: `unit_of_measurement: "%"`, `state_class: measurement`, `icon: mdi:gauge`,
   device `{identifiers:[oiltank_monitor], name: "Oil Tank Monitor", manufacturer, model}`.
   The prototype uses the HiveMQ MQTT 3 async client (QoS 1, retain true by default); the rewrite
   uses Spring Integration MQTT and Paho, as iot-pipeline does. Broker config: `MQTT_HOST`,
   `MQTT_PORT`, `MQTT_USERNAME`, `MQTT_PASSWORD`.

Deployment in the prototype: Jib image `brulejr/monitor-oiltank` on `eclipse-temurin:21-jre-jammy`
running as user 1000:1000. A static ffmpeg is downloaded into `/usr/local/bin`. The entrypoint reads
`*_FILE` Docker secrets into env vars. The prototype's Bruno requests are Management Health and Info,
and Tank → Get Tank Level.
