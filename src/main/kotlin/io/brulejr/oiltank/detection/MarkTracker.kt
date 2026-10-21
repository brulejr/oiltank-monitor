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
import kotlin.math.roundToInt

/**
 * Follows the gauge's marks as the camera drifts, so a small bump does not need
 * recalibrating.
 *
 * The marks are dark bars across the tube, so each shows as a row darker than the rows
 * around it near the tube's edges. Those bands are found outside the float's own rows,
 * whose dark underside and bright edges would otherwise pass for marks. The bands are
 * then matched against the configured reference rows under a single vertical shift.
 * Marks are moved, never discovered: a stray band can at worst fail to match, and too
 * few matches fail the frame rather than read it against the wrong marks.
 *
 * The marks are evenly spaced, so a drift of about one gap would line most of them up
 * with their neighbours: every mark read one level off. Matching enough marks does not
 * rule that out, so every mark that should be visible (inside the region, clear of its
 * edges, outside the float's rows) must also be found. In a false alignment the end
 * marks land where there is no band.
 */
class MarkTracker(
    private val roi: DetectionDatafill.Roi,
    private val settings: DetectionDatafill.Marks,
) {

    private val reference = settings.rows.zip(settings.levels) { y, level -> GaugeMark(y, level) }

    fun align(image: BufferedImage, float: FloatBlob): MarkAlignment {
        val tracking = settings.tracking
        if (!tracking.enabled) return MarkAlignment(reference, shift = 0, matched = 0, bands = emptyList())

        val hidden = hiddenRows(float)
        val bands = bands(image, hidden)
        val (shift, pairs) = (-tracking.maxShift..tracking.maxShift)
            .map { shift -> shift to pairs(bands, shift) }
            .maxWith(compareBy<Pair<Int, List<Pair<Int, Int>>>> { it.second.size }.thenBy { (shift, pairs) -> -residual(pairs, shift) })
        if (pairs.size < tracking.minMatches) {
            throw DetectionException(
                "only ${pairs.size} of ${reference.size} marks found near their reference rows " +
                    "(bands at $bands); has the camera moved? Recalibrate from /api/detection/debug",
            )
        }
        val unseen = reference.filter { mark -> pairs.none { it.first == mark.y } }
            .map { it.copy(y = it.y + shift) }
            .filter { it.y !in hidden && it.y in visibleRows() }
        if (unseen.isNotEmpty()) {
            throw DetectionException(
                "marks ${unseen.map { "${it.level} at row ${it.y}" }} should be visible but were not found " +
                    "(bands at $bands); has the camera moved? Recalibrate from /api/detection/debug",
            )
        }
        // Refined to the mean offset of the marks actually seen.
        val refined = pairs.map { (ref, band) -> band - ref }.average().roundToInt()
        return MarkAlignment(reference.map { it.copy(y = it.y + refined) }, refined, pairs.size, bands)
    }

    /** The float's rows: its rim above its top, then its flange, skirt and dark underside. */
    private fun hiddenRows(float: FloatBlob) = (float.top - FLOAT_MARGIN_ABOVE)..(float.top + settings.tracking.floatRows)

    /** Rows far enough inside the region for a band there to be measured against rows on both sides. */
    private fun visibleRows() = (roi.y + BASELINE_HALF_WINDOW)..(roi.y + roi.height - 1 - BASELINE_HALF_WINDOW)

    /** Each reference mark paired with the nearest band within the tolerance of where [shift] puts it. */
    private fun pairs(bands: List<Int>, shift: Int): List<Pair<Int, Int>> = reference.mapNotNull { mark ->
        bands.minByOrNull { abs(it - (mark.y + shift)) }
            ?.takeIf { abs(it - (mark.y + shift)) <= settings.tracking.tolerance }
            ?.let { mark.y to it }
    }

    private fun residual(pairs: List<Pair<Int, Int>>, shift: Int) = pairs.sumOf { (ref, band) -> abs(band - ref - shift) }

    /**
     * Rows noticeably darker than their neighbourhood, at least the minimum spacing apart,
     * strongest first. Brightness is averaged over two bands of columns near the tube's
     * edges: the middle holds the mark labels, the float's stem and, under infrared, a
     * reflection.
     */
    private fun bands(image: BufferedImage, hidden: IntRange): List<Int> {
        val tracking = settings.tracking
        val columns = (columnRange(0.15, 0.35) + columnRange(0.65, 0.85))
        val rows = (roi.y until roi.y + roi.height).filter { it !in hidden }
        val brightness = rows.associateWith { y ->
            columns.sumOf { x -> image.getRGB(x, y).let { (it shr 16 and 0xff) + (it shr 8 and 0xff) + (it and 0xff) } / 3.0 } / columns.size
        }
        val darkness = rows.associateWith { y ->
            val around = rows.filter { abs(it - y) <= BASELINE_HALF_WINDOW }
            around.sumOf { brightness.getValue(it) } / around.size - brightness.getValue(y)
        }
        val picked = mutableListOf<Int>()
        for ((y, contrast) in darkness.entries.sortedByDescending { it.value }) {
            if (contrast < tracking.minContrast) break
            if (picked.all { abs(it - y) >= tracking.minSpacing }) picked += y
        }
        return picked.sorted()
    }

    private fun columnRange(from: Double, to: Double) = (roi.x + (roi.width * from).toInt()) until (roi.x + (roi.width * to).toInt())

    private companion object {
        /** Rows above the float's top that may still belong to it (its rim, blurred). */
        const val FLOAT_MARGIN_ABOVE = 10

        /** Half the window each row's brightness is compared against; about a third of a mark gap. */
        const val BASELINE_HALF_WINDOW = 15
    }
}
