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

/**
 * Finds the float in an infrared frame. Under the camera's IR light the float glows
 * white, but so does a narrow reflection running the length of the tube, so brightness
 * alone is not enough: the float is where the bright region is *wide*.
 *
 * Each row of [roi] counts its pixels of at least [settings] brightness; rows at least
 * the minimum width wide are the float's. A graduation mark drawn across the float
 * splits it into bands, so bands separated by no more than the maximum gap are joined,
 * and the first band from the top is taken: its top edge is the reference, matching
 * the top edge of the yellow blob in daylight.
 */
class InfraredFloatDetector(
    private val roi: DetectionDatafill.Roi,
    private val settings: DetectionDatafill.Infrared,
    private val minArea: Int,
) : FloatDetector {

    override fun detect(image: BufferedImage): FloatBlob? {
        roi.requireFits(image)
        val bright = Array(roi.height) { y ->
            (0 until roi.width).filter { x -> brightness(image.getRGB(roi.x + x, roi.y + y)) >= settings.brightnessMin }
        }
        val wide = bright.indices.filter { bright[it].size >= settings.minWidth }
        if (wide.isEmpty()) return null
        var last = wide.first()
        for (y in wide.drop(1)) {
            if (y - last > settings.maxGap + 1) break
            last = y
        }
        val band = wide.first()..last
        val rows = band.filter { bright[it].size >= settings.minWidth }
        val area = rows.sumOf { bright[it].size }
        if (area < minArea) return null
        return FloatBlob(
            left = roi.x + rows.minOf { bright[it].first() },
            top = roi.y + band.first,
            right = roi.x + rows.maxOf { bright[it].last() },
            bottom = roi.y + band.last,
            area = area,
            mode = DetectionMode.INFRARED,
        )
    }

    private fun brightness(rgb: Int) = ((rgb shr 16 and 0xff) + (rgb shr 8 and 0xff) + (rgb and 0xff)) / 3
}
