/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.oiltank.camera

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.integration.channel.NullChannel
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals

class CameraHealthIndicatorTest {

    private val capturedAt = Instant.parse("2026-10-20T12:00:00Z")
    private val datafill = CameraDatafill(
        source = SnapshotSourceType.FILE,
        file = CameraDatafill.FileSource(Path.of("unused")),
        snapshotInterval = Duration.ofSeconds(30),
    )

    private fun clockAt(instant: Instant) = Clock.fixed(instant, ZoneOffset.UTC)

    /** Health is judged [elapsed] after the service's captures. */
    private fun status(service: CameraService, elapsed: Duration = Duration.ZERO) =
        CameraHealthIndicator(service, datafill, clockAt(capturedAt + elapsed)).health().status.code

    private fun service(source: SnapshotSource) = CameraService(source, NullChannel(), NullChannel(), clockAt(capturedAt))

    @Test
    fun `unknown before the first capture`() {
        assertEquals("UNKNOWN", status(service { testJpeg() }))
    }

    @Test
    fun `up after a recent capture`() {
        val service = service { testJpeg() }
        service.capture()

        assertEquals("UP", status(service, Duration.ofSeconds(90)))
    }

    @Test
    fun `down once captures are older than three intervals`() {
        val service = service { testJpeg() }
        service.capture()

        assertEquals("DOWN", status(service, Duration.ofSeconds(91)))
    }

    @Test
    fun `down after a failed capture`() {
        val service = service { throw SnapshotException("camera unreachable") }
        assertThrows<SnapshotException> { service.capture() }

        assertEquals("DOWN", status(service))
    }
}
