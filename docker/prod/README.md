# Production

The application alone on its Docker host: it reads the camera and publishes to
Home Assistant's broker, and keeps nothing between restarts. Deployment is done
by Ansible, outside this repository; this directory is what it runs.

## What the deployment does

From a checkout of this repository:

```bash
./gradlew bootJar
docker compose -f docker/prod/compose.prod.yaml --env-file <env file> up -d --build
```

`bootJar` writes `build/libs/oiltank-monitor.jar`, which
[`../Dockerfile`](../Dockerfile) packages with ffmpeg; the image is tagged
`oiltank-monitor:${APP_VERSION:-latest}`. Tests run with `./gradlew build` if the
deployment should refuse a failing commit. The host may be x86-64 or ARM64.

## What it must provide

The env file, kept by Ansible and never committed:

| Variable | Required | |
| --- | --- | --- |
| `OILTANK_CAMERA_RTSP_URL` | yes | The camera's stream, credentials included |
| `MQTT_URL` | yes | The broker Home Assistant listens on |
| `MQTT_USERNAME`, `MQTT_PASSWORD` | no | If the broker needs them |
| `MQTT_CLIENT_ID` | no | Default `oiltank-monitor` |
| `APP_PORT` | no | Host port, default 5001 |
| `APP_VERSION` | no | Image tag, default `latest` |

The `prod` profile reads an `application-prod.yml` if one is on the classpath;
none ships, so the environment and `application.yml` are the whole
configuration. Variables reach the application under their full property names
(`OILTANK_*`), which outrank any profile file.

## Behaviour worth knowing

- **Calibration ships in the jar.** The ROI, mark rows and reference offset in
  `application.yml` belong to one camera position. After the camera moves,
  re-measure from `GET /api/detection/debug`, commit, and redeploy; mark tracking
  absorbs only small drift.
- **Credentials stay out of the logs.** The RTSP URL is redacted wherever the
  application logs or reports it, ffmpeg's own output included, but it is
  visible to anyone who can `docker inspect` the container.
- **Start-up.** Without a reachable broker the application still starts: its
  publish health goes DOWN and the latest message per topic is held and retried.
  Home Assistant shows the sensor unavailable until it connects.
- **Identity.** The sensor keeps the prototype's identity (`oiltank_level`,
  device `oiltank_monitor`), so the existing entity and its history carry over.
  Stop the prototype before starting this, or both publish to the same entity.
- **Exposure.** The application (5001) is published on every interface. Its API
  has no authentication and serves camera snapshots of the basement, so keep
  5001 on a trusted network.
