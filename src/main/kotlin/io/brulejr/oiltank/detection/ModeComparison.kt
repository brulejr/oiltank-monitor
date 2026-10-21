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
 * The latest pair of readings taken either side of a switch between the camera's day and
 * night modes. The tank barely changes in that time, so the two should agree; when they
 * do not, one mode's calibration has drifted.
 *
 * @property colorLevel the daylight reading's raw level.
 * @property infraredLevel the infrared reading's raw level.
 * @property difference how far apart they are.
 * @property withinTolerance whether [difference] is within `oiltank.detection.cross-check.tolerance`.
 * @property at when the second of the pair was captured.
 */
data class ModeComparison(
    val colorLevel: Double,
    val infraredLevel: Double,
    val difference: Double,
    val withinTolerance: Boolean,
    val at: Instant,
)
