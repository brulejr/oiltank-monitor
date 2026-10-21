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
import kotlin.test.assertEquals

class DetectionDatafillTest {

    private val roi = DetectionDatafill.Roi(0, 0, 10, 10)
    private val marks = DetectionDatafill.Marks(rows = listOf(454, 541, 629, 720, 787))

    @Test
    fun `one row per level`() {
        val failure = assertThrows<IllegalArgumentException> { DetectionDatafill.Marks(rows = listOf(454, 541)) }

        assertEquals("oiltank.detection.marks.rows needs one row per level (5)", failure.message)
    }

    @Test
    fun `rows run top to bottom`() {
        assertThrows<IllegalArgumentException> { DetectionDatafill.Marks(levels = listOf(1.0, 0.0), rows = listOf(800, 450)) }
    }

    @Test
    fun `levels decrease top to bottom`() {
        assertThrows<IllegalArgumentException> { DetectionDatafill.Marks(levels = listOf(0.0, 1.0), rows = listOf(450, 800)) }
    }

    @Test
    fun `no more matches required than there are marks`() {
        assertThrows<IllegalArgumentException> {
            DetectionDatafill(
                roi,
                marks = DetectionDatafill.Marks(levels = listOf(1.0, 0.0), rows = listOf(450, 800)),
                referenceOffset = 0,
            )
        }
    }

    @Test
    fun `the hue range is ordered`() {
        assertThrows<IllegalArgumentException> { DetectionDatafill.ColorRange(hueMin = 40, hueMax = 15) }
    }

    @Test
    fun `smoothing alpha must be positive`() {
        assertThrows<IllegalArgumentException> { DetectionDatafill(roi, marks = marks, referenceOffset = 0, smoothingAlpha = 0.0) }
    }
}
