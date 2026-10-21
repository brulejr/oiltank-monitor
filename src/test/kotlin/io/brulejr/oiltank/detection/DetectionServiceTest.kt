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
import io.brulejr.oiltank.camera.testJpeg
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayInputStream
import java.time.Duration
import java.time.Instant
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DetectionServiceTest {

    private val now = Instant.parse("2026-10-20T12:00:00Z")
    private val snapshot = Snapshot(testJpeg(), now)

    /**
     * A detector that reports the float's top at each of [tops] in turn, null being a miss,
     * all in [mode]. Marks are not tracked, so the reference rows apply: 1/2 is at 629.
     */
    private fun service(vararg tops: Int?, delta: Double = 0.003, mode: DetectionMode = DetectionMode.COLOR) =
        service(tops.map { top -> top?.let { it to mode } }, delta)

    private fun service(frames: List<Pair<Int, DetectionMode>?>, delta: Double = 0.003): DetectionService {
        val queue = ArrayDeque(frames)
        val detector = FloatDetector { queue.removeFirst()?.let { (top, mode) -> FloatBlob(0, top, 10, top + 10, 100, mode) } }
        val untracked = Fixtures.currentDatafill.marks.copy(tracking = DetectionDatafill.Tracking(enabled = false))
        val datafill = Fixtures.currentDatafill.copy(marks = untracked, hysteresisDelta = delta)
        return DetectionService(detector, MarkTracker(datafill.roi, datafill.marks), datafill, SimpleMeterRegistry())
    }

    @Test
    fun `the gauge is read below the float's top by the reference offset`() {
        // Top 609, plus the offset of 20: row 629, the 1/2 mark.
        val reading = service(609).process(snapshot)

        assertEquals(FloatReading(629, 0.5, 0.5, DetectionMode.COLOR, now), reading)
    }

    @Test
    fun `readings are smoothed across frames`() {
        // 1/2, then 3/4 (521 + 20 = 541).
        val service = service(609, 521)
        service.process(snapshot)

        val second = service.process(snapshot)

        assertEquals(0.75, second.raw)
        assertEquals(0.3 * 0.75 + 0.7 * 0.5, second.smoothed, 1e-9)
    }

    @Test
    fun `a reading is passed on only once it has moved by the delta`() {
        val service = service(delta = 0.01)
        fun reading(smoothed: Double) = FloatReading(0, smoothed, smoothed, DetectionMode.COLOR, now)

        assertTrue(service.shouldPass(reading(0.500)))
        assertFalse(service.shouldPass(reading(0.505)))
        // Measured from the last reading passed on, not the last one seen.
        assertTrue(service.shouldPass(reading(0.510)))
        assertEquals(2, service.status().passed)
    }

    @Test
    fun `a frame without a float is recorded and thrown`() {
        val service = service(null)

        assertThrows<DetectionException> { service.process(snapshot) }

        val status = service.status()
        assertEquals(1, status.misses)
        assertEquals("no float found in the region of interest", status.lastFailure)
        assertNull(status.lastReading)
    }

    @Test
    fun `a frame that does not decode is a miss`() {
        assertThrows<DetectionException> { service(609).process(Snapshot("not an image".toByteArray(), now)) }
    }

    @Test
    fun `readings either side of a day-night switch are compared`() {
        // 1/2 by day, then just below it by night (632: about 0.49).
        val service = service(listOf(609 to DetectionMode.COLOR, 612 to DetectionMode.INFRARED))
        service.process(snapshot)

        service.process(Snapshot(snapshot.jpeg, now + Duration.ofMinutes(10)))

        val comparison = assertNotNull(service.status().lastModeComparison)
        assertEquals(0.5, comparison.colorLevel)
        assertEquals(0.5 - 3.0 / 91 * 0.25, comparison.infraredLevel, 1e-9)
        assertTrue(comparison.withinTolerance)
    }

    @Test
    fun `a disagreement beyond the tolerance is flagged`() {
        // 1/2 by day, 3/4 by night.
        val service = service(listOf(609 to DetectionMode.COLOR, 521 to DetectionMode.INFRARED))
        service.process(snapshot)
        service.process(snapshot)

        assertFalse(assertNotNull(service.status().lastModeComparison).withinTolerance)
    }

    @Test
    fun `readings too far apart in time are not compared`() {
        val service = service(listOf(609 to DetectionMode.COLOR, 612 to DetectionMode.INFRARED))
        service.process(snapshot)

        service.process(Snapshot(snapshot.jpeg, now + Duration.ofHours(2)))

        assertNull(service.status().lastModeComparison)
    }

    @Test
    fun `the debug image is the latest frame, whether or not the float was found`() {
        val service = service(null)
        assertNull(service.debugImage())

        assertThrows<DetectionException> { service.process(Snapshot(Fixtures.bytes(Fixtures.currentFraming), now)) }

        val image = ImageIO.read(ByteArrayInputStream(assertNotNull(service.debugImage())))
        assertEquals(1920, image.width)
        assertEquals(1080, image.height)
    }
}
