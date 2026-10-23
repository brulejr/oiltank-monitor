# Home Assistant

Each reading passed on by detection becomes a tank level, which is published to
the broker Home Assistant listens on (`MQTT_URL`) through MQTT discovery. Home
Assistant needs nothing configured beyond its MQTT integration with discovery
enabled, which is the default.

Why it is built this way: [level/](DESIGN.md#level) and
[publish/](DESIGN.md#publish).

## The level

The smoothed fill fraction is rounded to a whole percentage (0 to 100), and given a
status:

| Status | When |
| --- | --- |
| `LOW` | Below `oiltank.level.low-percent` (20) |
| `NORMAL` | Between the thresholds, the thresholds themselves included |
| `HIGH` | Above `oiltank.level.high-percent` (95) |

A LOW tank is not a fault of the application, so `level` health stays UP and
carries the status in its details. `GET /api/level` returns the latest level, or 404
before the first reading:

```json
{
  "percent": 50,
  "status": "NORMAL",
  "reading": { "floatY": 608, "raw": 0.506, "smoothed": 0.503, "mode": "COLOR", "capturedAt": "2026-10-21T14:02:30Z" }
}
```

Detection passes a level on only when it changes by at least 0.3 %, so a steady
tank can go hours without a new message. The broker holds the last one.

## Messages

All four topics are under `homeassistant/sensor/oiltank_level/`:

| Topic | Payload | Retained |
| --- | --- | --- |
| `config` | Discovery: the sensor and its device | always |
| `state` | The percentage, e.g. `50` | `retain-state`, default yes |
| `attributes` | `percentage`, `status`, `raw`, `smoothed`, `mode`, `timestampIso` | `retain-state`, default yes |
| `availability` | `online` or `offline` | always |

The sensor is **Oil Tank Level** (`unique_id: oiltank_level`), in %, with
`state_class: measurement` so Home Assistant keeps long-term statistics, on the
device **Oil Tank Monitor** (`oiltank_monitor`). These are the prototype's
identifiers, kept so the existing entity and its history carry over: **changing
`sensor-id` or `device-id` creates a new entity**. The attribute names
`percentage` and `timestampIso` are the prototype's too, for existing templates.

The status is an attribute, not an entity of its own. To act on a low tank, use
the attribute or the percentage in an automation, for example:

```yaml
trigger:
  - platform: numeric_state
    entity_id: sensor.oil_tank_level
    below: 20
```

The entity id depends on what Home Assistant made of the name; check it under
**Settings › Devices & services › MQTT**.

## Availability

Every connection to the broker first sends the discovery config and `online`, so
Home Assistant finds the sensor again after either side restarts. `offline` is
registered as the connection's last will, which the broker sends if the monitor
disappears, and is sent by the monitor itself on a clean shutdown. While
`offline`, Home Assistant shows the sensor as unavailable rather than a stale
value.

The connection is made on demand, at the first message after startup, so the
sensor appears after the first reading.

## Publication and retries

When the broker cannot be reached, the failed message is held, and so is
everything after it, one per topic: a newer state replaces the one waiting, since
Home Assistant only wants the latest. Held messages are retried in order after a
backoff that doubles from `retry.initial-backoff` (1 s) up to `retry.max-backoff`
(1 min). Meanwhile `publish` health is DOWN. Held messages are kept in memory, so a
restart drops them, but the next reading brings fresh state.

Set `PUBLISH_ENABLED=false` to run without a broker: levels are counted and
dropped, and nothing connects.

| Endpoint | What it does |
| --- | --- |
| `GET /api/publish` | Messages published, held, superseded and dropped; failures; whether the circuit is open and when it is next retried |
| `GET /api/publish/messages` | The last message sent to each topic |
| `POST /api/publish/discovery` | Sends the discovery config again |

To see the messages on the broker:

```bash
mosquitto_sub -h <broker> -t 'homeassistant/sensor/oiltank_level/#' -v
```

## Settings

Under `oiltank.publish`; the broker's are in [Configuration](configuration.md).

| Key | Default | Purpose |
| --- | --- | --- |
| `retain-state` | `true` | Retain state and attributes, so Home Assistant shows the last level after a restart |
| `retry.initial-backoff` / `max-backoff` | `1s` / `1m` | The retry backoff |
| `home-assistant.discovery-prefix` | `homeassistant` | Home Assistant's discovery prefix |
| `home-assistant.sensor-id` / `sensor-name` | `oiltank_level` / `Oil Tank Level` | The entity; the id is `[a-z0-9_]+` |
| `home-assistant.device-id` / `device-name` | `oiltank_monitor` / `Oil Tank Monitor` | The device it belongs to |
