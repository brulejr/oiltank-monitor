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
 * Where the gauge's marks are in one frame.
 *
 * @property marks every mark, at its reference row moved by [shift], including any the
 *   float hides.
 * @property shift rows the marks have moved from their reference: the camera's drift.
 * @property matched marks seen in this frame within the tolerance of their shifted row.
 * @property bands every dark band found, matched or not, for the debug image.
 */
data class MarkAlignment(
    val marks: List<GaugeMark>,
    val shift: Int,
    val matched: Int,
    val bands: List<Int>,
)
