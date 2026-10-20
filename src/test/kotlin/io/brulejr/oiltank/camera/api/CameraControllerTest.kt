/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.oiltank.camera.api

import io.brulejr.oiltank.camera.CameraService
import io.brulejr.oiltank.camera.SnapshotException
import io.brulejr.oiltank.camera.testJpeg
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.integration.channel.NullChannel
import java.time.Clock
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class CameraControllerTest {

    private val jpeg = testJpeg()
    private val controller = CameraController(CameraService({ jpeg }, NullChannel(), Clock.systemUTC()))

    @Test
    fun `no snapshot before the first capture`() {
        assertEquals(HttpStatus.NOT_FOUND, controller.snapshot().statusCode)
    }

    @Test
    fun `the latest snapshot after a capture`() {
        assertEquals(1, controller.capture().captures)

        val response = controller.snapshot()

        assertEquals(HttpStatus.OK, response.statusCode)
        assertContentEquals(jpeg, response.body)
    }

    @Test
    fun `a capture failure is a bad gateway`() {
        val response = controller.captureFailed(SnapshotException("camera unreachable"))

        assertEquals(HttpStatus.BAD_GATEWAY, response.statusCode)
        assertEquals(mapOf("message" to "camera unreachable"), response.body)
    }
}
