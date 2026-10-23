# Documentation

Start with the [project README](../README.md) for an overview and quick start.
[Design](DESIGN.md) explains how the monitor is built and why; each guide below
links to the part of it that concerns that guide.

## Using the monitor

| Guide | Covers | Design |
| --- | --- | --- |
| [Camera](camera.md) | Capturing over RTSP with ffmpeg, replaying saved frames, the snapshot endpoints | [camera/](DESIGN.md#camera) |
| [Detection](detection.md) | Finding the float by colour and by infrared, tracking the marks, calibrating, test frames | [detection/](DESIGN.md#detection) |
| [Home Assistant](home-assistant.md) | The percentage and its status, discovery and state messages, availability, retries | [level/](DESIGN.md#level), [publish/](DESIGN.md#publish) |

## Running it

| Guide | Covers | Design |
| --- | --- | --- |
| [Configuration](configuration.md) | Environment variables, local overrides, and the camera's credentials | [Features](DESIGN.md#features) |
| [Operations](operations.md) | Actuator, the REST API, per-stage logging, metrics, and troubleshooting | [Observability](DESIGN.md#observability) |
| [Docker](../docker/README.md) | Running in containers: a development broker, locally, or in production | [Deployment](DESIGN.md#deployment) |

## Working on it

| Guide | Covers |
| --- | --- |
| [Development](development.md) | Building, testing, and the source layout |
| [Design](DESIGN.md) | The stage-by-stage design and its reasoning, decisions, open questions |
| [Handoff](HANDOFF.md) | Where the work stands and what comes next |
