# Detection

Detection turns a frame into a fill fraction between 0 (empty) and 1 (full): it
finds the float in the gauge tube, works out where the gauge's marks are in this
frame, and reads the float's position between them. It is plain Kotlin over
`BufferedImage`, with no OpenCV, and takes a few milliseconds a frame.

Why it works this way, with the findings from real frames behind each step:
[Design](DESIGN.md#detection).

## Finding the float

Everything happens inside a configured box around the tube,
`oiltank.detection.roi`. It is needed: a yellow board elsewhere in the frame is the
same colour as the float.

The camera works in three conditions, and the detector picks its method from the
frame:

| Condition | How the frame looks | How the float is found |
| --- | --- | --- |
| Day | Full colour | The float is the only yellow thing on the gauge: an HSV threshold, taking the topmost blob |
| Dawn and dusk | Dim colour | The same threshold. It is led by saturation with a low brightness floor, because the float stays strongly saturated as the light fades |
| Night | Infrared, exactly grey | The float glows. Rows bright across at least `infrared.min-width` are the float's; the reflection down the tube is narrower |

A frame with no colour in the box at all is treated as infrared. Either way, a
blob smaller than `min-area` is not a float, and a frame without one is a failure:
it is logged and shown in health, and nothing is published.

Each reading records which method was used (`COLOR` or `INFRARED`) as its `mode`.

## Reading the gauge

The float's top edge is the reference, since it is where the day and night
methods agree. The gauge is read `reference-offset` rows below it, where the
float's wide part steps down to its skirt.

That row is converted to a fraction by interpolating between the marks
`marks.levels` (F, ¾, ½, ¼, E) at `marks.rows`. The marks are not evenly spaced in
the image (the camera looks up at the gauge), so a straight line would not fit, and
since a gauge is graduated for its tank, interpolating between its marks already
allows for the shape of a horizontal tank. A float beyond the end marks reads 0 or
1.

The fraction is then smoothed (an exponential moving average, `smoothing-alpha`),
and passed on only if it moved by at least `hysteresis-delta` since the last one
passed on. The first reading after startup always passes; smoothing starts afresh
from it and settles within a few minutes.

## Mark tracking

The camera drifts. Rather than trusting fixed rows, each frame looks for the
marks, which show as dark bands across the tube, and finds the one vertical shift
that lines the configured `marks.rows` up with the most of them. Every mark moves by
that shift, including those the float hides.

The configured rows are always the reference and are never replaced by what is
found, since the float's own edges can pass for a mark. A frame is failed rather
than read against the wrong marks when:

- fewer than `marks.tracking.min-matches` marks line up ("has the camera moved?"), or
- a mark that should be visible has no band, which is how an alignment one whole
  mark off shows itself.

Drift of up to `marks.tracking.max-shift` rows is absorbed (±25 has been tested,
by day and by night). A bigger move, or one sideways out of the box, needs
recalibrating.

## Calibrating

The box, the mark rows and the reference offset belong to one camera position. To
measure them, after the camera is installed or has moved:

1. Take a reading, then fetch the debug image:

   ```bash
   curl -s -X POST localhost:5001/api/camera/snapshot > /dev/null
   curl -s localhost:5001/api/detection/debug -o debug.jpg
   ```

   It is the latest frame, whether or not a float was found, with lines drawn on it:

   | Colour | Shows |
   | --- | --- |
   | Cyan | The box (`roi`) |
   | Orange ticks | The dark bands found, either side of the tube |
   | Magenta | The marks used, after tracking |
   | Green | The float's bounding box |
   | Red | The row read |

2. Read pixel coordinates off the image (1920×1080, y growing downwards), and set
   them in `application.yml` under `oiltank.detection`:
   - `roi`: a box around the tube, ending above the gauge's metal base, which glows
     under infrared;
   - `marks.rows`: one row per mark, top to bottom. The orange ticks are a good guide;
   - `reference-offset`: from the float's top (green) down to the line the gauge is
     read at.
3. Restart, take a reading, and check the debug image and `GET /api/detection`.
4. Update `Fixtures.currentDatafill` in the tests to match, and replace the frames in
   `src/test/resources/gauge/` with ones from the new position (see below).

Check a night frame too: the infrared settings (`brightness-min`, `min-width`,
`max-gap`) depend on the camera's distance from the gauge.

## Day/night cross-check

The tank barely changes in half an hour, so when a reading follows one in the other
mode within `cross-check.window`, the two raw readings should agree. Their
difference is reported as `dayNightComparison` in health, flagged when it exceeds
`cross-check.tolerance`. It is a reason to recalibrate, not a failed reading.

## Test frames

`GaugeReadingTest` is the regression suite for detection: real frames in
`src/test/resources/gauge/`, each named with the level the gauge showed, read end
to end with the configured detector and checked to within ±0.03.

| Frame | Conditions | Reads |
| --- | --- | --- |
| `2026-10-20-half.jpg` | Day | 0.506 |
| `2026-10-21-half-dawn.jpg` | Dawn, dim colour | 0.497 |
| `2026-10-20-half-night.jpg` | Night, infrared | 0.492 to 0.497 |
| `old-framing/2025-11-15-half.jpg` | An earlier camera position | |

All of them show ½, so the reference offset was fitted to them rather than
validated: frames at other levels are still needed. To add one:

1. Save the frame when the gauge sits on a known mark:
   `curl -s localhost:5001/api/camera/snapshot -o frame.jpg`.
2. **Mask it.** The repository must not carry pictures of the house: make
   everything outside a box around the gauge tube flat grey (128,128,128), keeping
   the frame 1920×1080 so its coordinates match the configuration.
3. Add it to `src/test/resources/gauge/`, named `<date>-<level>[-conditions].jpg`, and
   a case to `GaugeReadingTest`.

`gauge/` holds only frames matching the current calibration, because the Spring
tests replay it with the real configuration. Frames from other camera positions go
in a subdirectory.

## Endpoints and health

| Endpoint | What it gives |
| --- | --- |
| `GET /api/detection` | Detections, misses and readings passed on; the last reading, mark alignment, day/night comparison and failure |
| `GET /api/detection/debug` | The latest frame with the calibration drawn on it; 404 before the first |

`detection` health is UP when the latest frame gave a reading and DOWN with the
reason otherwise, with `markShift`, `marksMatched` and `dayNightComparison` in its
details. The calibration itself is on `/mgmt/info`.

## Settings

Under `oiltank.detection`; the values in `application.yml` are the current
camera position's, measured on 2026-10-20.

| Key | Current | Purpose |
| --- | --- | --- |
| `roi.x` / `y` / `width` / `height` | `905` / `420` / `130` / `390` | The box around the tube, in frame pixels |
| `color.hue-min` / `hue-max` | `15` / `40` | The float's yellow, on OpenCV's 0–180 hue scale |
| `color.saturation-min` / `value-min` | `120` / `30` | 0–255; saturation-led, so it holds at dawn and dusk |
| `infrared.brightness-min` | `200` | How bright a pixel must be to count as glowing |
| `infrared.min-width` | `45` | Pixels; wider than the reflection, narrower than the float |
| `infrared.max-gap` | `12` | Rows; joins the float across a mark's black line |
| `min-area` | `150` | Pixels; anything smaller is not the float |
| `marks.levels` | `[1.00, 0.75, 0.50, 0.25, 0.00]` | The marks' levels, top to bottom |
| `marks.rows` | `[454, 541, 629, 720, 787]` | Their rows: the tracker's reference |
| `marks.tracking.enabled` / `max-shift` / `tolerance` | `true` / `60` / `6` | How far the marks may drift, and how close a band must be to match |
| `marks.tracking.min-matches` | `3` | Fewer fails the frame |
| `marks.tracking.min-contrast` / `min-spacing` / `float-rows` | `12` / `40` / `75` | How a band is found, and the rows below the float's top not searched |
| `reference-offset` | `20` | Rows from the float's top to where the gauge is read |
| `smoothing-alpha` | `0.30` | Weight of each new reading, in (0, 1] |
| `hysteresis-delta` | `0.003` | Smallest change passed on |
| `cross-check.window` / `tolerance` | `30m` / `0.03` | The day/night comparison |

Every value is validated at startup, and an inconsistent one (mark rows out of
order, a hue range the wrong way round) stops the application with a message
naming the key.
