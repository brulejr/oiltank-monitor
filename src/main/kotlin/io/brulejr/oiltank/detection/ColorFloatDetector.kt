/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.oiltank.detection

import java.awt.Color
import java.awt.image.BufferedImage

/**
 * Finds the float by colour: the pixels inside [roi] within [color], cleaned of specks
 * by a morphological opening, and the largest 8-connected blob among them.
 *
 * Plain Kotlin rather than OpenCV. The region is a few tens of thousands of pixels and
 * the steps are simple, so native libraries would add a platform dependency for nothing.
 */
class ColorFloatDetector(
    private val roi: DetectionDatafill.Roi,
    private val color: DetectionDatafill.ColorRange,
    private val minArea: Int,
) : FloatDetector {

    override fun detect(image: BufferedImage): FloatBlob? {
        roi.requireFits(image)
        val mask = open(mask(image))
        return largestBlob(mask)?.takeIf { it.area >= minArea }
    }

    private fun mask(image: BufferedImage): BooleanArray {
        val hsb = FloatArray(3)
        return BooleanArray(roi.width * roi.height) { i ->
            val rgb = image.getRGB(roi.x + i % roi.width, roi.y + i / roi.width)
            Color.RGBtoHSB(rgb shr 16 and 0xff, rgb shr 8 and 0xff, rgb and 0xff, hsb)
            val hue = hsb[0] * 180
            hue >= color.hueMin && hue <= color.hueMax &&
                hsb[1] * 255 >= color.saturationMin && hsb[2] * 255 >= color.valueMin
        }
    }

    /** Erosion then dilation, 3x3: removes isolated pixels without shrinking the float. */
    private fun open(mask: BooleanArray) = morph(morph(mask, erode = true), erode = false)

    /** Erosion keeps a pixel only if its whole neighbourhood is set; dilation sets it if any of it is. */
    private fun morph(mask: BooleanArray, erode: Boolean): BooleanArray = BooleanArray(mask.size) { i ->
        val x = i % roi.width
        val y = i / roi.width
        var result = erode
        for (dy in -1..1) for (dx in -1..1) {
            val nx = x + dx
            val ny = y + dy
            if (nx !in 0 until roi.width || ny !in 0 until roi.height) continue
            if (mask[ny * roi.width + nx] != erode) result = !erode
        }
        result
    }

    private fun largestBlob(mask: BooleanArray): FloatBlob? {
        val seen = BooleanArray(mask.size)
        val stack = IntArray(mask.size)
        var best: FloatBlob? = null
        for (start in mask.indices) {
            if (!mask[start] || seen[start]) continue
            var left = Int.MAX_VALUE; var top = Int.MAX_VALUE; var right = -1; var bottom = -1; var area = 0
            var size = 0
            stack[size++] = start
            seen[start] = true
            while (size > 0) {
                val i = stack[--size]
                val x = i % roi.width
                val y = i / roi.width
                area++
                left = minOf(left, x); right = maxOf(right, x); top = minOf(top, y); bottom = maxOf(bottom, y)
                for (dy in -1..1) for (dx in -1..1) {
                    val nx = x + dx
                    val ny = y + dy
                    if (nx !in 0 until roi.width || ny !in 0 until roi.height) continue
                    val n = ny * roi.width + nx
                    if (mask[n] && !seen[n]) {
                        seen[n] = true
                        stack[size++] = n
                    }
                }
            }
            if (best == null || area > best.area) {
                best = FloatBlob(roi.x + left, roi.y + top, roi.x + right, roi.y + bottom, area, DetectionMode.COLOR)
            }
        }
        return best
    }
}
