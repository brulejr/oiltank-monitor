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
import java.awt.Color
import java.awt.image.BufferedImage
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InfraredFloatDetectorTest {

    private val datafill = Fixtures.currentDatafill
    private val detector = InfraredFloatDetector(datafill.roi, datafill.infrared, datafill.minArea)

    /** A grey frame with the tube's narrow reflection down the middle of the region. */
    private fun frame(draw: java.awt.Graphics2D.() -> Unit = {}) = BufferedImage(1920, 1080, BufferedImage.TYPE_INT_RGB).apply {
        createGraphics().run {
            color = Color(90, 90, 90); fillRect(0, 0, 1920, 1080)
            color = Color(240, 240, 240); fillRect(958, 420, 24, 390)
            draw(); dispose()
        }
    }

    @Test
    fun `the float is found in the night frame`() {
        val blob = assertNotNull(detector.detect(Fixtures.image(Fixtures.currentFramingNight)))

        // Measured by hand: the float's flange starts at row 612.
        assertTrue(blob.top in 608..614, "top ${blob.top}")
        assertEquals(DetectionMode.INFRARED, blob.mode)
    }

    @Test
    fun `the reflection down the tube alone is not a float`() {
        assertNull(detector.detect(frame()))
    }

    @Test
    fun `a mark across the float does not split it`() {
        val blob = assertNotNull(
            detector.detect(
                frame {
                    fillRect(930, 610, 80, 20)
                    color = Color(20, 20, 20); fillRect(930, 630, 80, 8)
                    color = Color(240, 240, 240); fillRect(930, 638, 80, 12)
                },
            ),
        )

        assertEquals(610, blob.top)
        assertEquals(649, blob.bottom)
    }

    @Test
    fun `the topmost band is taken when two are far apart`() {
        val blob = assertNotNull(detector.detect(frame { fillRect(930, 500, 80, 10); fillRect(930, 700, 80, 10) }))

        assertEquals(500, blob.top)
    }
}
