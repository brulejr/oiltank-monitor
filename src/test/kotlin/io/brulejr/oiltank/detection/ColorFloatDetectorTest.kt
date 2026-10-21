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
import java.awt.Color
import java.awt.image.BufferedImage
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ColorFloatDetectorTest {

    private fun detector(datafill: DetectionDatafill) = ColorFloatDetector(datafill.roi, datafill.color, datafill.minArea)

    private fun plain(width: Int = 1920, height: Int = 1080) = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB).apply {
        createGraphics().run { color = Color(200, 200, 200); fillRect(0, 0, width, height); dispose() }
    }

    @Test
    fun `the float is found in the current framing`() {
        val blob = assertNotNull(detector(Fixtures.currentDatafill).detect(Fixtures.image(Fixtures.currentFraming)))

        // Measured by hand: the wide part of the float spans rows 608 to 620.
        assertTrue(blob.top in 604..612, "top ${blob.top}")
        assertTrue(blob.bottom in 616..624, "bottom ${blob.bottom}")
    }

    @Test
    fun `the float is found in the old framing`() {
        val blob = assertNotNull(detector(Fixtures.oldDatafill).detect(Fixtures.image(Fixtures.oldFraming)))

        // Measured by hand: rows 456 to 495.
        assertTrue(blob.top in 452..460, "top ${blob.top}")
        assertTrue(blob.bottom in 491..499, "bottom ${blob.bottom}")
    }

    @Test
    fun `the old framing's float is outside the current region`() {
        assertNull(detector(Fixtures.currentDatafill).detect(Fixtures.image(Fixtures.oldFraming)))
    }

    @Test
    fun `an infrared frame has no yellow`() {
        assertNull(detector(Fixtures.currentDatafill).detect(Fixtures.image(Fixtures.currentFramingNight)))
    }

    @Test
    fun `nothing is found in a frame without yellow`() {
        assertNull(detector(Fixtures.currentDatafill).detect(plain()))
    }

    @Test
    fun `isolated yellow pixels are not a float`() {
        val image = plain()
        listOf(950 to 500, 980 to 600, 1000 to 700).forEach { (x, y) -> image.setRGB(x, y, Color.YELLOW.rgb) }

        assertNull(detector(Fixtures.currentDatafill).detect(image))
    }

    @Test
    fun `a region that does not fit the frame is a failure`() {
        assertThrows<DetectionException> { detector(Fixtures.currentDatafill).detect(plain(640, 480)) }
    }
}
