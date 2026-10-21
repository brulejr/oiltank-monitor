/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.oiltank.detection

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarkTrackerTest {

    private val datafill = Fixtures.currentDatafill
    private val tracker = MarkTracker(datafill.roi, datafill.marks)
    private val detector = DetectionConfig(datafill).floatDetector()

    private fun align(image: java.awt.image.BufferedImage) = tracker.align(image, detector.detect(image)!!)

    @Test
    fun `the marks are where they were measured by day`() {
        val alignment = align(Fixtures.image(Fixtures.currentFraming))

        assertTrue(abs(alignment.shift) <= 2, "shift ${alignment.shift}")
        assertTrue(alignment.matched >= 4, "matched ${alignment.matched}")
    }

    @Test
    fun `the marks are where they were measured by night`() {
        val alignment = align(Fixtures.image(Fixtures.currentFramingNight))

        assertTrue(abs(alignment.shift) <= 3, "shift ${alignment.shift}")
        assertTrue(alignment.matched >= 4, "matched ${alignment.matched}")
    }

    @Test
    fun `the marks are followed when the camera drifts`() {
        val alignment = align(Fixtures.shifted(Fixtures.currentFraming, 25))

        assertTrue(abs(alignment.shift - 25) <= 2, "shift ${alignment.shift}")
        assertEquals(datafill.marks.rows.map { it + alignment.shift }, alignment.marks.map { it.y })
    }

    /**
     * A drift of one mark gap (88 rows) lines most marks up with their neighbours at a small
     * apparent shift; only a mark missing where one should be gives it away. Exactly one
     * gap is the worst case. (Larger drifts move the masked fixture's own edges into the
     * region, which a real camera's frame would not have.)
     */
    @ParameterizedTest
    @ValueSource(ints = [88, -88])
    fun `a drift of one mark gap fails rather than read every mark one level off`(rows: Int) {
        val image = Fixtures.shifted(Fixtures.currentFraming, rows)

        assertThrows<DetectionException> { align(image) }
    }

    @Test
    fun `with tracking off the reference rows are used as they are`() {
        val untracked = MarkTracker(datafill.roi, datafill.marks.copy(tracking = DetectionDatafill.Tracking(enabled = false)))

        val alignment = untracked.align(Fixtures.image(Fixtures.currentFraming), FloatBlob(0, 608, 0, 620, 500, DetectionMode.COLOR))

        assertEquals(0, alignment.shift)
        assertEquals(datafill.marks.rows, alignment.marks.map { it.y })
    }
}
