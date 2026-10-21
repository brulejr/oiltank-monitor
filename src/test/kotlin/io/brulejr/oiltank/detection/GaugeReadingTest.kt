/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.oiltank.detection

import io.brulejr.oiltank.camera.Snapshot
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.nio.file.Path
import java.time.Instant
import kotlin.test.assertEquals

/**
 * The regression suite for detection: each real frame, read end to end with its own
 * calibration, must come out at the level the gauge showed. Add a case for every new
 * frame at a known level.
 *
 * Every frame so far reads 1/2, and each framing's reference offset was fitted to its
 * own frames (in the current framing, one offset serves day and night). So for now these
 * guard against regressions in finding the float; they cannot yet show the reading is
 * accurate at other levels.
 */
class GaugeReadingTest {

    /** About 2 mm of float travel on this gauge; the marks themselves are 2-3 px thick. */
    private val tolerance = 0.03

    /** Reads [frame] with the detector and tracker the application builds, which pick the mode themselves. */
    private fun read(frame: Path, datafill: DetectionDatafill) = read(Fixtures.bytes(frame), datafill)

    private fun read(jpeg: ByteArray, datafill: DetectionDatafill): FloatReading {
        val config = DetectionConfig(datafill)
        val service = DetectionService(config.floatDetector(), config.markTracker(), datafill, SimpleMeterRegistry())
        return service.process(Snapshot(jpeg, Instant.now()))
    }

    @Test
    fun `current framing by day reads one half`() {
        val reading = read(Fixtures.currentFraming, Fixtures.currentDatafill)

        assertEquals(DetectionMode.COLOR, reading.mode)
        assertEquals(0.5, reading.raw, tolerance)
    }

    @Test
    fun `current framing by night reads one half`() {
        val reading = read(Fixtures.currentFramingNight, Fixtures.currentDatafill)

        assertEquals(DetectionMode.INFRARED, reading.mode)
        assertEquals(0.5, reading.raw, tolerance)
    }

    @ParameterizedTest
    @CsvSource("2026-10-20-half.jpg, 25", "2026-10-20-half.jpg, -25", "2026-10-20-half-night.jpg, 25", "2026-10-20-half-night.jpg, -25")
    fun `a camera that has drifted still reads one half`(frame: String, rows: Int) {
        val image = Fixtures.shifted(Fixtures.currentFraming.resolveSibling(frame), rows)

        val reading = read(Fixtures.jpeg(image), Fixtures.currentDatafill)

        assertEquals(0.5, reading.raw, tolerance)
    }

    @Test
    fun `old framing reads one half`() {
        val reading = read(Fixtures.oldFraming, Fixtures.oldDatafill)

        assertEquals(DetectionMode.COLOR, reading.mode)
        assertEquals(0.5, reading.raw, tolerance)
    }
}
