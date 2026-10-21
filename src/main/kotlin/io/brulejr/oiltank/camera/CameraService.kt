/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.oiltank.camera

import org.springframework.integration.support.MessageBuilder
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.MessagingException
import org.springframework.messaging.support.ErrorMessage
import java.io.ByteArrayInputStream
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.imageio.ImageIO

/**
 * Stage 1: captures a frame from the [SnapshotSource], checks it decodes, and keeps the
 * latest one for the API. Every capture is counted, and the reason for the latest
 * failure kept, so the health indicator can tell a working camera from a silent one.
 */
class CameraService(
    private val source: SnapshotSource,
    private val snapshotChannel: MessageChannel,
    private val errorChannel: MessageChannel,
    private val clock: Clock,
) {

    private val captures = AtomicLong()
    private val failures = AtomicLong()
    private val lastAttemptAt = AtomicReference<Instant?>()
    private val lastFailure = AtomicReference<String?>()
    private val latest = AtomicReference<Snapshot?>()

    /** A new frame. Throws [SnapshotException] when there is none, after recording why. */
    fun capture(): Snapshot {
        lastAttemptAt.set(clock.instant())
        try {
            val jpeg = source.capture()
            if (!decodes(jpeg)) throw SnapshotException("captured ${jpeg.size} bytes that do not decode as an image")
            return Snapshot(jpeg, clock.instant()).also {
                latest.set(it)
                lastFailure.set(null)
                captures.incrementAndGet()
            }
        } catch (e: SnapshotException) {
            failures.incrementAndGet()
            lastFailure.set(e.message)
            throw e
        }
    }

    /**
     * Captures a frame outside the schedule and sends it down the pipeline, as a poll
     * would. A failure further down is the later stage's, not the camera's: like the
     * poller, this hands it to the error channel instead of throwing it, and that stage's
     * health records it.
     */
    fun captureNow(): Snapshot = capture().also { snapshot ->
        try {
            snapshotChannel.send(MessageBuilder.withPayload(snapshot).build())
        } catch (e: MessagingException) {
            errorChannel.send(ErrorMessage(e))
        }
    }

    /** The latest frame captured, or null before the first. */
    fun latest(): Snapshot? = latest.get()

    fun status() = CameraStatus(
        captures = captures.get(),
        failures = failures.get(),
        lastAttemptAt = lastAttemptAt.get(),
        lastCaptureAt = latest.get()?.capturedAt,
        lastFailure = lastFailure.get(),
    )

    private fun decodes(jpeg: ByteArray): Boolean = try {
        jpeg.isNotEmpty() && ImageIO.read(ByteArrayInputStream(jpeg)) != null
    } catch (_: IOException) {
        false
    }
}
