/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.oiltank.level

import io.brulejr.oiltank.detection.DetectionMode
import io.brulejr.oiltank.detection.FloatReading
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LevelServiceTest {

    private val registry = SimpleMeterRegistry()
    private val service = LevelService(LevelDatafill(lowPercent = 20, highPercent = 95), registry)

    private fun reading(smoothed: Double) = FloatReading(0, smoothed, smoothed, DetectionMode.COLOR, Instant.EPOCH)

    private fun percent() = registry.get("oiltank.level.percent").gauge().value()

    @Test
    fun `the smoothed fraction becomes a rounded percentage`() {
        assertEquals(51, service.toLevel(reading(0.506)).percent)
        assertEquals(49, service.toLevel(reading(0.494)).percent)
    }

    @Test
    fun `the level is judged against the thresholds, which are themselves normal`() {
        assertEquals(LevelStatus.LOW, service.toLevel(reading(0.19)).status)
        assertEquals(LevelStatus.NORMAL, service.toLevel(reading(0.20)).status)
        assertEquals(LevelStatus.NORMAL, service.toLevel(reading(0.95)).status)
        assertEquals(LevelStatus.HIGH, service.toLevel(reading(0.96)).status)
    }

    @Test
    fun `the latest level is kept and reported as a gauge`() {
        assertNull(service.latest())
        assertTrue(percent().isNaN(), "no level yet, so no value")

        val level = service.toLevel(reading(0.5))

        assertSame(level, service.latest())
        assertEquals(50.0, percent())
    }
}
