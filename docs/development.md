# Development

## Building and testing

```bash
./gradlew build      # compile and test; produces build/libs/oiltank-monitor.jar
./gradlew test       # tests only
./gradlew bootRun    # run locally on :5001

# Without a camera: replay the test frames
CAMERA_SOURCE=file OILTANK_CAMERA_FILE_DIRECTORY=src/test/resources/gauge ./gradlew bootRun
```

Built with Kotlin 2.3.21 and Spring Boot 4.1.1 on a Java 21 toolchain. Things to
watch for when adding code:

- Jackson is the 3.x `tools.jackson` namespace, **not** `com.fasterxml.jackson`.
- Eclipse Paho must stay an explicit dependency; it is optional in
  `spring-integration-mqtt` and will not resolve transitively.
- The RTSP URL carries the camera's credentials. Never log or return it raw: use
  `redactCredentials` / `redactUrlIn` from `camera/Redaction.kt`, ffmpeg's error
  output included.
- Every `.kt` file opens with the MIT license header, as a `/* */` block comment
  above `package`. Copy it from an existing file.

## Tests

The tests never reach a camera or a broker. Every `@SpringBootTest` sets:

```
oiltank.camera.source=file
oiltank.camera.file.directory=src/test/resources/gauge
oiltank.publish.enabled=false
```

Keep all three in any new one. There are two exceptions by design:

- `MqttPublisherIT` uses a broker on `localhost:1883` if one is running (the
  [development broker](../docker/dev/README.md) will do), under a throwaway topic
  prefix, and skips otherwise.
- `PipelineEndToEndTest` runs a real frame through all four stages to the state and
  attributes messages. It turns publishing on, since with it off a level never
  reaches the MQTT client, but swaps that client for a recorder and points the
  broker URL at a closed port.

Worth knowing:

- `GaugeReadingTest` is the regression suite for detection, over the real frames in
  `src/test/resources/gauge/`. Add every new frame at a known level there, masked
  first; see [Test frames](detection.md#test-frames).
- `Fixtures.currentDatafill` mirrors the detection settings in `application.yml`;
  update both together after recalibrating.
- `FfmpegSnapshotSourceTest` runs a stand-in shell script as ffmpeg, to stage
  success, failure, empty output and a timeout.
- `PipelineTopologyTest` asserts the wiring against the graph Spring Integration
  builds; update its expected map when a stage is rewired.
- `ActuatorContributionTest` checks every stage contributes to health and info.

## Layout

```
src/main/kotlin/io/brulejr/oiltank/
├── OiltankMonitorApplication.kt
├── camera/       # Snapshot capture: ffmpeg over RTSP, or replayed files; the poller
├── detection/    # Finding the float by colour or infrared, mark tracking, smoothing, hysteresis
├── level/        # Percentage and LOW/NORMAL/HIGH status
├── publish/      # Home Assistant MQTT discovery, state, availability, retries
│   └── mqtt/     #   the Paho client behind PublisherPort
└── pipeline/     # Cross-cutting: channel names, channels, the error log

src/test/resources/gauge/   # Masked real frames, each at a known level

docker/                     # The image, and compose files for dev, local and prod
bruno/oiltank-monitor/      # A Bruno request for every endpoint
```

Packages mirror pipeline stages, joined by the channels named in
`pipeline/PipelineChannels.kt`:

```
camera ──snapshotChannel──▶ detection ──floatReadingChannel──▶ level ──tankLevelChannel──▶ publish
```

Every stage is synchronous on the poller's thread, so one frame is processed end to
end before the next. A failure in any stage unwinds to the poller and is logged by
`pipelineErrorChannel`'s flow; the next poll starts clean.

Every feature package has the same shape: one `*Config` declaring its beans and
flows, a validated `*Datafill` for its `oiltank.*` settings, a `*Service` holding its
logic, a `*Controller` under `api/`, a health indicator and an info contributor.
Beans are declared in the `*Config`, never by `@Component` or `@Service`. Ports such
as `SnapshotSource`, `FloatDetector` and `PublisherPort` let tests and local runs
swap in replayed frames or a dropped publisher without touching the flow wiring.

## Design and conventions

[Design](DESIGN.md) records the reasoning behind each stage and every decision; read
it before changing the pipeline's structure. [Handoff](HANDOFF.md) has where the work
stands and what comes next. Project conventions, written for Claude Code but
applying to anyone, are in [`CLAUDE.md`](../CLAUDE.md) and
[`.claude/CONVENTIONS.md`](../.claude/CONVENTIONS.md).
