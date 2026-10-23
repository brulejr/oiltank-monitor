# Session Handoff

Context for resuming work in a new Claude Code session, possibly on another machine.
Last updated 2026-10-21, at commit `397ffe3`.

Read in this order: `CLAUDE.md` (conventions, commands, notes), `docs/DESIGN.md` (the
design and every decision, with dates), then this file (state, what is pending, and how
the work has been run). `docs/PLAN.md` is the original scaffold plan, done.

## Where things stand

The whole pipeline works end to end and has been verified against the real camera and a
local MQTT broker:

```
camera (ffmpeg/RTSP, every 30 s) → detection → level → publish (Home Assistant over MQTT)
```

| Commit | Step |
|---|---|
| `c73e763` | Scaffold and design doc |
| `d02c0f4` | `pipeline/` channels and `camera/` capture |
| `16e76ad` | `detection/`: colour by day, infrared by night, mark tracking, day/night cross-check |
| `8395c4c` | `level/`: percentage and LOW/NORMAL/HIGH status |
| `63fc555` | Detection fix for dim colour frames at dawn/dusk |
| `397ffe3` | `publish/`: Home Assistant discovery, state, attributes, availability, retry |

105 tests pass (`./gradlew build`; `MqttPublisherIT` skips without a local broker). The live gauge reads about ½ (49–50 %) in every
mode, which is what it shows.

## Next steps

1. **Deployment is done on this side** (implementation order step 6): `docker/` was
   verified against the real camera, `bruno/oiltank-monitor/` covers every endpoint, and
   `PipelineEndToEndTest` runs a frame through every stage. What remains is the Ansible
   side of production, outside this repo. The image is 1.1 GB, mostly Ubuntu's ffmpeg
   and its codec libraries; a slimmer ffmpeg is possible if it matters on the host.
2. **Validate the reading at another level.** Every test frame shows ½, and the reference
   offset (20 px) was fitted to them, so accuracy at other levels is unproven. When the
   gauge sits on ¾ or ¼, save a frame (`curl localhost:5001/api/camera/snapshot -o f.jpg`),
   mask it (below), add it to `src/test/resources/gauge/` and a case to
   `GaugeReadingTest`. If one offset does not hold, build open question 8 (calibration
   by assertion, offset per mark).
3. **Watch the first day/night cross-check** (`dayNightComparison` in
   `/mgmt/health` → detection) after an evening switch to infrared; it has not reported
   live yet.
4. Still Proposed, not requested: a "tank low" `binary_sensor`. Open questions 3
   (capture strategy) and 5 (rejecting implausible jumps) remain open in DESIGN.md.

## How the user likes to work

- Commit each finished step on `main` as a restore point (no feature branches yet),
  with a commit message explaining why. The build must pass at every commit; split
  unrelated changes into separate commits.
- Save plans and research in the project's `docs/`, not in `~/.claude`.
- The user refines designs: give a recommendation with reasons, flag what goes beyond
  the prototype as **Proposed**, and record decisions in DESIGN.md with dates. When
  evidence contradicts an earlier claim or plan, say so and correct the doc.
- Verify against the real system, not only tests: the live camera, the debug image, the
  local broker. Several real problems were found only that way (night infrared, the
  dim dawn frames, mark aliasing).
- **Never commit pictures of the house.** Mask every camera frame before adding it:
  flat grey (128,128,128) outside a box around the gauge tube, still 1920×1080 so it
  matches the configured coordinates. Current framing keeps `(860, 370, 1080, 870)`.
  Unmasked frames have never been in git history; keep it that way.
- The application is storage-free by decision (State and storage in DESIGN.md).

## Setting up another machine

- **Java 21** (the Gradle wrapper fetches Gradle 9.8.1 itself).
- **`src/main/resources/application-local.yml` is gitignored** and holds the camera
  credentials, so it does not travel with the repo. Recreate it:

  ```yaml
  oiltank:
    camera:
      # The Tapo camera account (Tapo app > Advanced settings > Camera account).
      rtsp-url: "rtsp://USER:PASSWORD@CAMERA_IP:554/stream1"
  ```

  Without the camera, run from the test frames instead:
  `CAMERA_SOURCE=file OILTANK_CAMERA_FILE_DIRECTORY=src/test/resources/gauge ./gradlew bootRun`.
- **ffmpeg** on `PATH` for the real camera.
- **An MQTT broker on `tcp://localhost:1883`** for publishing:
  `docker compose -f docker/dev/compose.dev.yaml up -d` starts one. Without one, set
  `PUBLISH_ENABLED=false`, or publish health goes DOWN and messages are held. `MqttPublisherIT` uses the broker if
  present and skips otherwise.
- The packaged jar leaves `application-local.yml` out, so when running the jar (rather
  than `bootRun`) pass `--spring.config.additional-location=file:src/main/resources/application-local.yml`.
- The repo is `git@github.com:brulejr/oiltank-monitor.git` (`origin`); clone it there.

## Facts worth knowing

- **Camera:** Tapo, 1920×1080, in the basement looking at a Scully gauge on a horizontal
  tank. It switches to infrared at night (frames exactly grey) and gives dim colour
  frames around dawn and dusk; all three are handled. It was re-aimed between
  November 2025 and October 2026, so expect it to move again: mark tracking absorbs
  small drift (±25 rows tested), a bigger move needs recalibrating from
  `GET /api/detection/debug` (ROI, `marks.rows`, `reference-offset` in `application.yml`,
  and `Fixtures.currentDatafill` in the tests to match).
- **Current calibration** (2026-10-20): ROI `905,420 130×390`; marks F/¾/½/¼/E at rows
  `454, 541, 629, 720, 787` (found automatically; a hand measurement of E at 800 was
  wrong); reference offset 20 px from the float's top.
- **Home Assistant identity** is the prototype's (`unique_id: oiltank_level`, device
  `oiltank_monitor`), so the existing entity carries over. The local test runs left
  retained `oiltank_level` messages on the local broker (config, state 50, `offline`).
- **Reference repos:** the prototype `brulejr/monitor-oiltank` and the conventions source
  `brulejr/iot-pipeline` on GitHub. No `gh` CLI was available; files were read with
  `curl` from `raw.githubusercontent.com`.
