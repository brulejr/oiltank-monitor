/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.oiltank.detection

/**
 * Converts a row in the frame to a fill fraction by interpolating between the gauge's
 * own [marks], top to bottom. The marks are graduated for the tank's volume, so this also
 * corrects for the tank's shape, and per-segment interpolation absorbs the perspective
 * that spaces them unevenly in the image. Rows beyond the end marks are clamped.
 */
class GaugeCalibration(private val marks: List<GaugeMark>) {

    fun levelAt(y: Double): Double {
        if (y <= marks.first().y) return marks.first().level
        if (y >= marks.last().y) return marks.last().level
        val (upper, lower) = marks.zipWithNext().first { (_, lower) -> y <= lower.y }
        val t = (y - upper.y) / (lower.y - upper.y)
        return upper.level + t * (lower.level - upper.level)
    }
}
