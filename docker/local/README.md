# Local provisioning

The application in a container, built from [`../Dockerfile`](../Dockerfile),
reading the real camera and publishing to the broker named in `.env`. Use it to
run the application on this machine the way production runs it.

```bash
# From the repository root
./gradlew bootJar
docker compose -f docker/local/compose.local.yaml --env-file .env up -d --build
```

The application is at http://localhost:5001 (`APP_PORT` moves it).

## Settings

The container runs the `prod` profile and takes everything from the environment.
The jar deliberately carries no `application-local.yml`, so nothing from IDE
development leaks in. Add to `.env`:

| Variable | Required | |
| --- | --- | --- |
| `OILTANK_CAMERA_RTSP_URL` | yes | The camera's stream, credentials included: `rtsp://USER:PASSWORD@CAMERA_IP:554/stream1` |
| `MQTT_URL` | yes | The broker to publish to. `tcp://mosquitto:1883` is [`dev`](../dev/README.md)'s, if it is running |
| `MQTT_CLIENT_ID` | no | Default `oiltank-monitor-local` |
| `MQTT_USERNAME`, `MQTT_PASSWORD` | no | If the broker needs them |
| `APP_PORT` | no | Host port for the application, default 5001 |

`localhost` inside the container is the container itself, so `tcp://localhost:1883`
does not reach a broker on the host. `dev`'s broker is on the same compose network
as `mosquitto`; a broker elsewhere is reached by its address on the LAN.

The compose file passes settings under their full property names
(`OILTANK_PUBLISH_MQTT_URL` and so on). Those outrank a profile file; the short
placeholders in `application.yml` (`MQTT_URL`) do not. `.env` is gitignored; it
holds the camera's credentials.

## Cautions

- **Stop the copy running in the IDE first.** Two instances would read the
  camera and publish every level twice.
- **`MQTT_URL` decides what Home Assistant sees.** It has no default on purpose:
  the identity is the real one (`oiltank_level`), so a broker Home Assistant
  listens on gets this instance's readings. Point it at `dev`'s broker unless you
  mean to publish to the real one.
