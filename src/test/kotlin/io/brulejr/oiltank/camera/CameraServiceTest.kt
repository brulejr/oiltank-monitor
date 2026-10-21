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
import org.springframework.integration.channel.DirectChannel
import org.springframework.integration.channel.QueueChannel
import org.springframework.messaging.MessageHandler
import org.springframework.messaging.MessagingException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

class CameraServiceTest {

    private val now = Instant.parse("2026-10-20T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val channel = QueueChannel()
    private val errors = QueueChannel()

    private fun service(source: SnapshotSource) = CameraService(source, channel, errors, clock)

    @Test
    fun `a capture is kept as the latest snapshot`() {
        val jpeg = testJpeg()
        val service = service { jpeg }

        val snapshot = service.capture()

        assertSame(jpeg, snapshot.jpeg)
        assertEquals(now, snapshot.capturedAt)
        assertSame(snapshot, service.latest())
        assertEquals(CameraStatus(1, 0, now, now, null), service.status())
    }

    @Test
    fun `bytes that do not decode are rejected`() {
        val service = service { "not an image".toByteArray() }

        val failure = assertThrows<SnapshotException> { service.capture() }

        assertEquals("captured 12 bytes that do not decode as an image", failure.message)
        assertNull(service.latest())
        assertEquals(CameraStatus(0, 1, now, null, failure.message), service.status())
    }

    @Test
    fun `a source failure is recorded and rethrown`() {
        val service = service { throw SnapshotException("camera unreachable") }

        assertThrows<SnapshotException> { service.capture() }

        assertEquals("camera unreachable", service.status().lastFailure)
    }

    @Test
    fun `a successful capture clears the last failure and keeps the earlier snapshot until then`() {
        var fail = false
        val service = service { if (fail) throw SnapshotException("camera unreachable") else testJpeg() }
        val first = service.capture()

        fail = true
        assertThrows<SnapshotException> { service.capture() }
        assertSame(first, service.latest())

        fail = false
        service.capture()
        assertNull(service.status().lastFailure)
        assertEquals(2, service.status().captures)
    }

    @Test
    fun `capturing now sends the snapshot down the pipeline`() {
        val service = service { testJpeg() }

        val snapshot = service.captureNow()

        assertSame(snapshot, assertNotNull(channel.receive(0)).payload)
    }

    @Test
    fun `a failure further down the pipeline goes to the error channel, not the caller`() {
        val failing = DirectChannel().apply { subscribe(MessageHandler { throw MessagingException(it, "no float") }) }
        val service = CameraService({ testJpeg() }, failing, errors, clock)

        service.captureNow()

        assertEquals("no float", (assertNotNull(errors.receive(0)).payload as MessagingException).message)
        assertNull(service.status().lastFailure)
    }
}
