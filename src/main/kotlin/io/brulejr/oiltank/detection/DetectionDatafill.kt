/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.oiltank.detection

import org.springframework.boot.context.properties.ConfigurationProperties
import java.awt.image.BufferedImage
import java.time.Duration

/**
 * How the float is found in a frame and turned into a fill fraction.
 *
 * [roi], the marks' rows and [referenceOffset] belong to one camera position, so they
 * have no defaults. Mark tracking absorbs small drift; after a bigger move, re-measure
 * them from `GET /api/detection/debug`. All positions are pixels in the full frame, with
 * y growing downwards.
 *
 * @property roi the box around the gauge tube. Only it is searched, which keeps out
 *   anything else of the float's colour. It must stay clear of the gauge's metal base,
 *   which glows under infrared.
 * @property color the float's colour, on OpenCV's HSV scales, for daylight frames.
 * @property infrared how the float is told from its surroundings in infrared frames.
 * @property minArea the smallest blob, in pixels, accepted as the float.
 * @property marks the gauge's graduations, and how they are followed as the camera drifts.
 * @property referenceOffset pixels from the top of the float down to the line the gauge
 *   is read at, where the float's wide top steps down to its skirt. The top edge is the
 *   one both modes agree on.
 * @property smoothingAlpha the weight of a new reading in the moving average.
 * @property hysteresisDelta the smallest change in the smoothed fraction passed on.
 * @property crossCheck how day and night readings are compared.
 */
@ConfigurationProperties("oiltank.detection")
data class DetectionDatafill(
    val roi: Roi,
    val color: ColorRange = ColorRange(),
    val infrared: Infrared = Infrared(),
    val minArea: Int = 150,
    val marks: Marks,
    val referenceOffset: Int,
    val smoothingAlpha: Double = 0.30,
    val hysteresisDelta: Double = 0.003,
    val crossCheck: CrossCheck = CrossCheck(),
) {
    data class Roi(val x: Int, val y: Int, val width: Int, val height: Int) {
        init {
            require(x >= 0 && y >= 0) { "oiltank.detection.roi.x and .y must not be negative" }
            require(width > 0 && height > 0) { "oiltank.detection.roi.width and .height must be positive" }
        }

        fun requireFits(image: BufferedImage) {
            if (x + width > image.width || y + height > image.height) {
                throw DetectionException("roi $x,$y ${width}x$height does not fit the ${image.width}x${image.height} frame")
            }
        }
    }

    /** Hue 0..180, saturation and value 0..255, as OpenCV scales them. */
    data class ColorRange(
        val hueMin: Int = 15,
        val hueMax: Int = 40,
        val saturationMin: Int = 80,
        val valueMin: Int = 60,
    ) {
        init {
            require(hueMin in 0..180 && hueMax in 0..180 && hueMin < hueMax) {
                "oiltank.detection.color.hue-min and .hue-max must be in 0..180, min below max"
            }
            require(saturationMin in 0..255 && valueMin in 0..255) {
                "oiltank.detection.color.saturation-min and .value-min must be in 0..255"
            }
        }
    }

    /**
     * @property brightnessMin the mean of red, green and blue, 0..255, at which a pixel counts as lit.
     * @property minWidth lit pixels a row needs to be part of the float: wider than the
     *   reflection running down the tube, narrower than the float.
     * @property maxGap rows of a graduation mark drawn across the float that may split it.
     */
    data class Infrared(
        val brightnessMin: Int = 200,
        val minWidth: Int = 45,
        val maxGap: Int = 12,
    ) {
        init {
            require(brightnessMin in 0..255) { "oiltank.detection.infrared.brightness-min must be in 0..255" }
            require(minWidth > 0) { "oiltank.detection.infrared.min-width must be positive" }
            require(maxGap >= 0) { "oiltank.detection.infrared.max-gap must not be negative" }
        }
    }

    /**
     * @property levels the fill fraction of each graduation, top to bottom.
     * @property rows where each graduation sits in the frame, top to bottom: the reference
     *   the tracker shifts.
     * @property tracking how the marks are followed as the camera drifts.
     */
    data class Marks(
        val levels: List<Double> = listOf(1.0, 0.75, 0.5, 0.25, 0.0),
        val rows: List<Int>,
        val tracking: Tracking = Tracking(),
    ) {
        init {
            require(levels.size >= 2) { "oiltank.detection.marks.levels needs at least two levels" }
            require(levels.all { it in 0.0..1.0 } && levels.zipWithNext().all { (a, b) -> b < a }) {
                "oiltank.detection.marks.levels must be in 0..1, decreasing from top to bottom"
            }
            require(rows.size == levels.size) { "oiltank.detection.marks.rows needs one row per level (${levels.size})" }
            require(rows.zipWithNext().all { (a, b) -> b > a }) { "oiltank.detection.marks.rows must increase from top to bottom" }
        }
    }

    /**
     * @property enabled when false the reference rows are used as they are.
     * @property maxShift the furthest, in rows, the marks may have drifted.
     * @property tolerance how close, in rows, a dark band must be to a shifted mark to match it.
     * @property minMatches marks that must be seen for the frame to be read; the float
     *   hides one or two.
     * @property minContrast how much darker than its neighbourhood, 0..255, a row must be to
     *   count as a band.
     * @property minSpacing the fewest rows between two bands.
     * @property floatRows rows below the float's top that its skirt and dark underside may
     *   occupy, where bands are not looked for.
     */
    data class Tracking(
        val enabled: Boolean = true,
        val maxShift: Int = 60,
        val tolerance: Int = 6,
        val minMatches: Int = 3,
        val minContrast: Int = 12,
        val minSpacing: Int = 40,
        val floatRows: Int = 75,
    ) {
        init {
            require(maxShift >= 0 && tolerance >= 0) { "oiltank.detection.marks.tracking.max-shift and .tolerance must not be negative" }
            require(minMatches >= 2) { "oiltank.detection.marks.tracking.min-matches must be at least 2" }
            require(minContrast > 0 && minSpacing > 0 && floatRows >= 0) {
                "oiltank.detection.marks.tracking.min-contrast and .min-spacing must be positive, .float-rows not negative"
            }
        }
    }

    /**
     * @property window the longest gap between a day reading and a night reading for them
     *   to be compared.
     * @property tolerance the most the two may differ before the comparison is flagged.
     */
    data class CrossCheck(
        val window: Duration = Duration.ofMinutes(30),
        val tolerance: Double = 0.03,
    ) {
        init {
            require(window.isPositive) { "oiltank.detection.cross-check.window must be positive" }
            require(tolerance >= 0.0) { "oiltank.detection.cross-check.tolerance must not be negative" }
        }
    }

    init {
        require(minArea > 0) { "oiltank.detection.min-area must be positive" }
        require(marks.tracking.minMatches <= marks.rows.size) {
            "oiltank.detection.marks.tracking.min-matches cannot exceed the number of marks (${marks.rows.size})"
        }
        require(smoothingAlpha > 0.0 && smoothingAlpha <= 1.0) { "oiltank.detection.smoothing-alpha must be in (0, 1]" }
        require(hysteresisDelta >= 0.0) { "oiltank.detection.hysteresis-delta must not be negative" }
    }
}
