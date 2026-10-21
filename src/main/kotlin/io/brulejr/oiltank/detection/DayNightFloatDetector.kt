/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.oiltank.detection

import java.awt.image.BufferedImage
import kotlin.math.abs

/**
 * Picks the detector for the camera's mode. The camera's infrared frames are exactly
 * grey (red, green and blue equal), while a daylight frame never is, so the mean channel
 * spread over the region tells them apart without a threshold to tune.
 */
class DayNightFloatDetector(
    private val roi: DetectionDatafill.Roi,
    private val color: FloatDetector,
    private val infrared: FloatDetector,
) : FloatDetector {

    override fun detect(image: BufferedImage): FloatBlob? {
        roi.requireFits(image)
        return if (isGrey(image)) infrared.detect(image) else color.detect(image)
    }

    private fun isGrey(image: BufferedImage): Boolean {
        var spread = 0L
        for (y in roi.y until roi.y + roi.height) for (x in roi.x until roi.x + roi.width) {
            val rgb = image.getRGB(x, y)
            val r = rgb shr 16 and 0xff
            val g = rgb shr 8 and 0xff
            val b = rgb and 0xff
            spread += abs(r - g) + abs(g - b)
        }
        return spread.toDouble() / (roi.width * roi.height) < GREY_SPREAD
    }

    private companion object {
        /** Mean |R-G| + |G-B| below which a region counts as grey; daylight frames measure 5 or more. */
        const val GREY_SPREAD = 1.0
    }
}
