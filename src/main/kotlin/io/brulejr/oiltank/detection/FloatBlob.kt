/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.oiltank.detection

/** The float's bounding box in the full frame, inclusive, how many pixels it covers, and how it was found. */
data class FloatBlob(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val area: Int,
    val mode: DetectionMode,
)
