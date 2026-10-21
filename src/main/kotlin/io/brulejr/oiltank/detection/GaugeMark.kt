/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.oiltank.detection

/** A graduation on the gauge: the row it sits at in the frame, and the fill fraction it stands for. */
data class GaugeMark(val y: Int, val level: Double)
