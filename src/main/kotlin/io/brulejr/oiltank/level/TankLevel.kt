/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.oiltank.level

import io.brulejr.oiltank.detection.FloatReading

/**
 * How full the tank is.
 *
 * @property percent the smoothed fill fraction as a whole percentage, 0..100.
 * @property status where [percent] stands against the thresholds.
 * @property reading the gauge reading it came from.
 */
data class TankLevel(
    val percent: Int,
    val status: LevelStatus,
    val reading: FloatReading,
)
