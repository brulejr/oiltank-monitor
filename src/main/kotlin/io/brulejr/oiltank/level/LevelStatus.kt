/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.oiltank.level

/** Where the level stands against the configured thresholds. */
enum class LevelStatus {
    /** Below `oiltank.level.low-percent`: time to order oil. */
    LOW,
    NORMAL,

    /** Above `oiltank.level.high-percent`: just filled, or overfilled. */
    HIGH,
}
