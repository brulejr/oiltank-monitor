/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.oiltank.detection

import java.time.Instant

/**
 * One frame's reading of the gauge.
 *
 * @property floatY the row the gauge was read at: the float's top plus the reference offset.
 * @property raw the fill fraction, 0..1, interpolated between the marks.
 * @property smoothed the moving average of [raw] across frames.
 * @property mode how the float was found in this frame.
 * @property capturedAt when the frame was captured.
 */
data class FloatReading(
    val floatY: Int,
    val raw: Double,
    val smoothed: Double,
    val mode: DetectionMode,
    val capturedAt: Instant,
)
