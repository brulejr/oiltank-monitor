# Configuration

Every setting is under `oiltank.*` in `src/main/resources/application.yml`, bound
to a validated `*Datafill` per feature: a missing or inconsistent value stops
startup with a message naming the key.

## Environment variables

Everything except the camera's RTSP URL has a working default:

| Variable | Default | Purpose |
| --- | --- | --- |
| `PROFILE` | `local` | Spring profile |
| `SERVER_PORT` | `5001` | HTTP port. Not 8080, which tends to collide locally |
| `CAMERA_ENABLED` | `true` | `false` stops the poller and removes the camera endpoints |
| `CAMERA_SOURCE` | `ffmpeg` | `ffmpeg` reads the camera; `file` replays a directory of frames |
| `OILTANK_CAMERA_RTSP_URL` | — | The camera's stream, credentials included. Required for `ffmpeg` |
| `OILTANK_CAMERA_FILE_DIRECTORY` | — | Frames to replay. Required for `file` |
| `FFMPEG_PATH` | `ffmpeg` | The ffmpeg executable, resolved on `PATH` |
| `PUBLISH_ENABLED` | `true` | `false` runs without a broker; levels are counted and dropped |
| `MQTT_URL` | `tcp://localhost:1883` | The broker Home Assistant listens on |
| `MQTT_CLIENT_ID` | `oiltank-monitor` | MQTT client identifier |
| `MQTT_USERNAME`, `MQTT_PASSWORD` | — | Broker credentials, if required |

Any other key can be set from the environment the same way, through Spring Boot's
relaxed binding: `oiltank.camera.snapshot-interval` is
`OILTANK_CAMERA_SNAPSHOTINTERVAL`.

The finer settings are described in the guide for each stage:

| Prefix | Guide |
| --- | --- |
| `oiltank.camera` | [Camera](camera.md): interval (`30s`, at least `5s`), capture timeout |
| `oiltank.detection` | [Detection](detection.md#settings): the box, colours, marks, smoothing |
| `oiltank.level` | [Home Assistant](home-assistant.md#the-level): the LOW and HIGH thresholds |
| `oiltank.publish` | [Home Assistant](home-assistant.md#settings): QoS, retention, retries, identity |

The detection settings in `application.yml` are not defaults in the usual sense:
they belong to one camera position, and need re-measuring when it changes. See
[Calibrating](detection.md#calibrating).

## Local overrides

The `local` profile is active by default (`spring.profiles.active:
${PROFILE:local}`), so `src/main/resources/application-local.yml` is read on every
run if it exists. It is gitignored, and the boot jar leaves it out, so it is the
place for the camera's credentials:

```yaml
oiltank:
  camera:
    rtsp-url: "rtsp://USER:PASSWORD@CAMERA_IP:554/stream1"
  publish:
    mqtt:
      url: tcp://192.168.1.50:1883
      username: oiltank
      password: hunter2
```

A fresh clone has no `application-local.yml`, so it starts without a camera URL
and fails, naming `OILTANK_CAMERA_RTSP_URL`. Set it there, in the environment, or
run with `CAMERA_SOURCE=file`.

When running the jar rather than `bootRun`, point it at the file explicitly:

```bash
java -jar build/libs/oiltank-monitor.jar \
  --spring.config.additional-location=file:src/main/resources/application-local.yml
```

## Credentials

The RTSP URL is the only secret the camera needs, and it is never logged or
reported raw: wherever it appears (info, health, ffmpeg's error output) the
credentials are redacted. The broker's password does not appear at all; info shows
the broker URL and client id only.

In a container the credentials come from an env file kept out of git: `.env` at the
repository root for [`docker/local`](../docker/local/README.md), the deployment's own
for [`docker/prod`](../docker/prod/README.md). They are redacted from the logs and
the actuator endpoints, but visible to anyone who can `docker inspect` the
container.

## In a container

The compose files run the `prod` profile and take everything from the env file.
The jar carries no `application-local.yml`, so nothing from development leaks in,
and no `application-prod.yml` ships either: the environment and `application.yml`
are the whole configuration.

| Variable | Required | Purpose |
| --- | --- | --- |
| `OILTANK_CAMERA_RTSP_URL` | yes | The camera's stream, credentials included |
| `MQTT_URL` | yes | The broker to publish to. No default, on purpose: the identity is the real one |
| `MQTT_USERNAME`, `MQTT_PASSWORD` | no | Broker credentials, if required |
| `MQTT_CLIENT_ID` | no | `oiltank-monitor` in production, `oiltank-monitor-local` locally |
| `APP_PORT` | no | Host port, default 5001 |
| `APP_VERSION` | no | Production image tag, default `latest` |

Compose passes these to the application under their full property names
(`OILTANK_PUBLISH_MQTT_URL` and so on), which outrank a profile file; the short
placeholders in `application.yml` would not. Inside a container `localhost` is the
container itself, so `tcp://localhost:1883` does not reach a broker on the host:
use the broker's address on the LAN, or `tcp://mosquitto:1883` for the development
broker. Why it is done this way rather than with Docker secrets:
[Design](DESIGN.md#deployment).
