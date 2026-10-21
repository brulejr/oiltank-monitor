/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.oiltank.level.api

import io.brulejr.oiltank.detection.DetectionMode
import io.brulejr.oiltank.detection.FloatReading
import io.brulejr.oiltank.level.LevelDatafill
import io.brulejr.oiltank.level.LevelService
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.time.Instant
import kotlin.test.assertEquals

class LevelControllerTest {

    private val service = LevelService(LevelDatafill(), SimpleMeterRegistry())
    private val controller = LevelController(service)

    @Test
    fun `no level before the first reading`() {
        assertEquals(HttpStatus.NOT_FOUND, controller.level().statusCode)
    }

    @Test
    fun `the latest level`() {
        service.toLevel(FloatReading(629, 0.5, 0.5, DetectionMode.COLOR, Instant.EPOCH))

        val response = controller.level()

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(50, response.body?.percent)
    }
}
