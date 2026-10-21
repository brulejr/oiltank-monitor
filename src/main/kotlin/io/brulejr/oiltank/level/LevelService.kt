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
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt

/**
 * Stage 3: turns a gauge reading into what a person cares about, a percentage and whether
 * it is low, and keeps the latest for the API.
 */
class LevelService(private val datafill: LevelDatafill, meterRegistry: MeterRegistry) {

    private val latest = AtomicReference<TankLevel?>()

    init {
        // NaN until the first level, rather than a misleading 0.
        Gauge.builder("oiltank.level.percent") { latest.get()?.percent?.toDouble() ?: Double.NaN }
            .baseUnit("percent")
            .register(meterRegistry)
    }

    /** The level [reading] stands for, kept as the latest. */
    fun toLevel(reading: FloatReading): TankLevel {
        val percent = (reading.smoothed * 100).roundToInt().coerceIn(0, 100)
        val status = when {
            percent < datafill.lowPercent -> LevelStatus.LOW
            percent > datafill.highPercent -> LevelStatus.HIGH
            else -> LevelStatus.NORMAL
        }
        return TankLevel(percent, status, reading).also { latest.set(it) }
    }

    /** The latest level, or null before the first reading. */
    fun latest(): TankLevel? = latest.get()
}
