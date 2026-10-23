# Development

The support services the application needs while it runs in the IDE: a Mosquitto
broker on localhost:1883, which `application.yml`'s default `MQTT_URL` already
names. The application itself is not containerised here; see
[`../local`](../local/README.md) for that.

```bash
# From the repository root
docker compose -f docker/dev/compose.dev.yaml up -d
```

| Service | Address | Credentials |
| --- | --- | --- |
| Mosquitto | tcp://localhost:1883 | none |

`MQTT_PORT` in `.env` moves the host port (pass `--env-file .env` then, and point
`MQTT_URL` at the new port).

The broker keeps retained messages in its `mosquitto-data` volume, so Home
Assistant's discovery config and the last state survive a restart. To watch what
the application publishes:

```bash
docker compose -f docker/dev/compose.dev.yaml exec mosquitto \
  mosquitto_sub -v -t 'homeassistant/#'
```

The application run from the IDE reads its settings, the camera's RTSP URL among
them, from `src/main/resources/application-local.yml` (gitignored); see
[`docs/HANDOFF.md`](../../docs/HANDOFF.md#setting-up-another-machine).
