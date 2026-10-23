# oiltank-monitor

A Kotlin / Spring Integration service that reads a heating-oil tank's level from
its float gauge, through a camera, and publishes it to Home Assistant. Every 30
seconds it takes a snapshot from an RTSP camera pointed at the gauge, finds the
float, reads it against the gauge's own graduations, and publishes the level as a
percentage over MQTT discovery.

It rewrites the prototype
[monitor-oiltank](https://github.com/brulejr/monitor-oiltank) using the
conventions of [iot-pipeline](https://github.com/brulejr/iot-pipeline). It keeps
no storage of its own: history lives in Home Assistant, and the latest value is
retained on the broker.

## How it works

```
RTSP camera ─► camera ─► detection ─► level ─► publish ─► MQTT ─► Home Assistant
  (ffmpeg)      ▲ every 30 s   │
                │              ├─ by day and at dawn/dusk: the float's yellow
   POST /api/camera/snapshot   └─ by night (infrared): the float's glow
```

1. **Capture.** ffmpeg pulls one frame from the camera's RTSP stream. Without a
   camera, a directory of saved frames is replayed instead.
2. **Detect.** Inside a configured box around the gauge tube, the float is found by
   colour in daylight, or by brightness once the camera switches to infrared.
   The gauge's marks (F, ¾, ½, ¼, E) are tracked as the camera drifts, and the
   float's position is interpolated between them, then smoothed. A reading that
   barely moved is not passed on.
3. **Level.** The fill fraction becomes a percentage, with a status of LOW, NORMAL
   or HIGH against configured thresholds.
4. **Publish.** Home Assistant finds the sensor through MQTT discovery and shows
   the percentage, with the status and raw readings as attributes. It shows the
   sensor as unavailable while the monitor is down. Messages that cannot be
   delivered are held and retried.

## Status

The whole chain works, and runs in production on a home automation Docker host,
reading the real camera and publishing to Home Assistant.

| Stage | State |
| --- | --- |
| Camera | Working: ffmpeg over RTSP, or replayed files, polled every 30 s |
| Detection | Working: colour by day and at dawn/dusk, infrared by night, mark tracking, a day/night cross-check |
| Level | Working: percentage and LOW/NORMAL/HIGH status |
| Publish | Working: Home Assistant discovery, state, attributes and availability; failures held and retried |
| Deployment | Working: a Docker image with ffmpeg, compose files for development, local and production, and a Bruno collection |

Every test frame shows the gauge at ½, so accuracy at other levels is not yet
proven; see [Detection](docs/detection.md#test-frames).

## Requirements

- JDK 21 (the Gradle toolchain will resolve one if it is not your default)
- An RTSP camera that can see the gauge
- An MQTT broker that Home Assistant listens on, with MQTT discovery enabled
- ffmpeg on `PATH`, to read the camera from the IDE (the Docker image includes it)
- Docker with Compose, to run it in a container, or for a development broker

Gradle itself does not need to be installed: use the bundled wrapper.

## Getting started

The gauge's position in the frame is configured, not found: the box around the
tube and the rows of its marks in `application.yml` belong to one camera position,
and ship in the jar. On a new camera, or after it has moved, calibrate first; see
[Detection](docs/detection.md#calibrating).

### Local development

For development and trying it out: the application run with Gradle or from the
IDE, and a Mosquitto broker in Docker standing in for Home Assistant's.

To try it without a camera or broker, replay the test frames and leave publishing
off:

```bash
CAMERA_SOURCE=file OILTANK_CAMERA_FILE_DIRECTORY=src/test/resources/gauge \
PUBLISH_ENABLED=false ./gradlew bootRun

curl -s localhost:5001/api/level | jq
```

With the camera, put its RTSP URL, which carries its credentials, in a gitignored
`src/main/resources/application-local.yml` rather than in your shell history:

```yaml
oiltank:
  camera:
    rtsp-url: "rtsp://USER:PASSWORD@CAMERA_IP:554/stream1"
```

```bash
# 1. Start the development broker on localhost:1883, the default MQTT_URL
docker compose -f docker/dev/compose.dev.yaml up -d

# 2. Run the application
./gradlew bootRun

# 3. Watch what it publishes
docker compose -f docker/dev/compose.dev.yaml exec mosquitto \
  mosquitto_sub -v -t 'homeassistant/#'
```

| Service | Address |
| --- | --- |
| Application | http://localhost:5001, Actuator under `/mgmt` |
| Mosquitto | tcp://localhost:1883, no credentials |

To run the application in a container on this machine too, the way production
runs it, see [`docker/local`](docker/local/README.md). All the settings are in
[Configuration](docs/configuration.md).

### Production deployment

The application alone on a Docker host, reading the camera and publishing to the
broker Home Assistant listens on. It keeps nothing between restarts, so there is
no database or volume to look after. From a checkout of this repository on that
host:

```bash
# 1. Name the camera and the broker; production has no defaults for them
cat > .env <<'EOF'
OILTANK_CAMERA_RTSP_URL=rtsp://USER:PASSWORD@CAMERA_IP:554/stream1
MQTT_URL=tcp://<broker Home Assistant listens on>:1883
EOF

# 2. Build the jar, then the image, and start it
./gradlew bootJar
docker compose -f docker/prod/compose.prod.yaml --env-file .env up -d --build
```

The image runs on x86-64 or ARM64 (a Raspberry Pi included), and is built on the
host that runs it. After the first reading an **Oil Tank Level** sensor appears in
Home Assistant under the device **Oil Tank Monitor**: the prototype's identity, so
an existing entity and its history carry over. Stop the prototype first, or both
publish to the same entity.

[`docker/prod`](docker/prod/README.md) lists every variable the env file can set,
including broker credentials, the host port and the image tag. In the home
deployment, Ansible keeps the env file and runs these steps.

## Usage

Once it is running:

- **See the level**: `GET /api/level`, or the sensor in Home Assistant.
- **See what the camera sees**: `GET /api/camera/snapshot` returns the latest frame,
  and `GET /api/detection/debug` the same frame with the box, the marks and the
  float drawn on it.
- **Take a reading now**: `POST /api/camera/snapshot`, rather than waiting for
  the next poll.
- **Check its health**: `GET /mgmt/health` reports each stage, with the reason
  for any failure.

```bash
curl -s localhost:5001/api/level | jq
curl -s localhost:5001/api/detection/debug -o debug.jpg
curl -s -X POST localhost:5001/api/camera/snapshot | jq
```

The [Bruno](https://www.usebruno.com/) collection in
[`bruno/oiltank-monitor`](bruno/oiltank-monitor) has a request for every endpoint,
Actuator's included.

The API has no authentication, and the snapshots show what the camera sees; keep
port 5001 on a trusted network.

## Documentation

| Guide | Covers |
| --- | --- |
| [Camera](docs/camera.md) | Capturing over RTSP, replaying files, and the snapshot endpoints |
| [Detection](docs/detection.md) | Finding the float by day and night, the marks, calibrating, test frames |
| [Home Assistant](docs/home-assistant.md) | The level and its status, MQTT messages, availability, and retries |
| [Configuration](docs/configuration.md) | Environment variables, local overrides, credentials |
| [Operations](docs/operations.md) | Actuator, the REST API, logs, metrics, and troubleshooting |
| [Docker](docker/README.md) | Running in containers: a development broker, locally, or in production |
| [Development](docs/development.md) | Building, testing, and the source layout |
| [Design](docs/DESIGN.md) | How it is built and why: each stage, the decisions, open questions |

## Development

```bash
./gradlew build      # compile and test
```

Built with Kotlin and Spring Boot on a Java 21 toolchain. See
[Development](docs/development.md) for the layout and the pitfalls to watch for,
and [`docs/DESIGN.md`](docs/DESIGN.md) before changing the pipeline's structure.

## License

MIT; see [LICENSE](LICENSE).
