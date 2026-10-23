# Camera

The first stage takes one JPEG frame from the camera and sends it down the
pipeline. It runs on a poller, every `oiltank.camera.snapshot-interval` (30 s by
default), and each frame is processed end to end before the next is taken.

Why it is built this way: [Design](DESIGN.md#camera).

## Sources

Set by `CAMERA_SOURCE` (`oiltank.camera.source`):

| Source | Reads | Needs |
| --- | --- | --- |
| `ffmpeg` (default) | One frame from the RTSP stream | `OILTANK_CAMERA_RTSP_URL`, and ffmpeg on `PATH` (or `FFMPEG_PATH`) |
| `file` | `.jpg`/`.jpeg` files from a directory, in name order, cycling | `OILTANK_CAMERA_FILE_DIRECTORY` |

The `ffmpeg` source runs, for each frame:

```
ffmpeg -hide_banner -loglevel error -rtsp_transport tcp -i <url> -frames:v 1 -f mjpeg pipe:1
```

It is killed after `capture-timeout` (15 s). A non-zero exit, empty output, or bytes
that do not decode as an image fail the capture. A new process per frame costs
about 2.6 s of RTSP negotiation, which a 30 s interval absorbs.

The `file` source is for working without the camera: the Spring tests replay
`src/test/resources/gauge/` this way, and so can a local run.

Startup fails if the source's setting is missing: an `ffmpeg` source without an
RTSP URL, or a `file` source without a directory. Setting `CAMERA_ENABLED=false`
stops the poller and removes the camera endpoints altogether.

## Credentials

The RTSP URL carries the camera's username and password
(`rtsp://USER:PASSWORD@CAMERA_IP:554/stream1`; on a Tapo camera, the camera
account under **Advanced settings** in the app). Keep it in the gitignored
`application-local.yml` or in the environment; see
[Configuration](configuration.md#local-overrides).

The URL is never logged or reported as given: the info endpoint, health, and the
failure messages all show it with the credentials redacted. That includes
ffmpeg's own error output, which echoes the input URL.

## Endpoints

| Endpoint | What it does |
| --- | --- |
| `GET /api/camera` | Captures and failures since startup, the last attempt and capture, and the last failure |
| `GET /api/camera/snapshot` | The latest frame as a JPEG, with `Last-Modified`; 404 before the first capture |
| `POST /api/camera/snapshot` | Captures now and runs the frame through the pipeline; returns the status |

A failed `POST` is a 502 with the reason, since it was the camera that failed. A
failure further down the pipeline does not fail the request: the capture worked,
and the stage that failed records it in its own health.

The latest frame is held in memory only, so after a restart the snapshot endpoint
returns 404 until the next capture.

```bash
curl -s localhost:5001/api/camera/snapshot -o latest.jpg
```

## Health

`camera` is UP when the last capture succeeded and is younger than three
intervals, DOWN with the last error otherwise, and UNKNOWN until the first
capture completes.
