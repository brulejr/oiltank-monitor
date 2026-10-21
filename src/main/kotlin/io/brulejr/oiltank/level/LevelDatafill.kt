/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.oiltank.level

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * @property lowPercent below this the tank is [LevelStatus.LOW].
 * @property highPercent above this the tank is [LevelStatus.HIGH].
 */
@ConfigurationProperties("oiltank.level")
data class LevelDatafill(
    val lowPercent: Int = 20,
    val highPercent: Int = 95,
) {
    init {
        require(lowPercent in 0..100 && highPercent in 0..100) { "oiltank.level.low-percent and .high-percent must be in 0..100" }
        require(lowPercent < highPercent) { "oiltank.level.low-percent must be below oiltank.level.high-percent" }
    }
}
