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
import java.nio.file.Path
import java.time.Duration
import kotlin.test.assertEquals

class CameraDatafillTest {

    @Test
    fun `ffmpeg needs a stream URL`() {
        val failure = assertThrows<IllegalArgumentException> { CameraDatafill(rtspUrl = " ") }

        assertEquals(
            "oiltank.camera.rtsp-url must be set when oiltank.camera.source is ffmpeg (set OILTANK_CAMERA_RTSP_URL)",
            failure.message,
        )
    }

    @Test
    fun `file needs a directory but no stream URL`() {
        assertThrows<IllegalArgumentException> { CameraDatafill(source = SnapshotSourceType.FILE) }

        CameraDatafill(source = SnapshotSourceType.FILE, file = CameraDatafill.FileSource(Path.of("images")))
    }

    @Test
    fun `the interval is at least five seconds`() {
        assertThrows<IllegalArgumentException> {
            CameraDatafill(rtspUrl = "rtsp://cam/stream", snapshotInterval = Duration.ofSeconds(4))
        }
    }

    @Test
    fun `the capture timeout is positive`() {
        assertThrows<IllegalArgumentException> { CameraDatafill(rtspUrl = "rtsp://cam/stream", captureTimeout = Duration.ZERO) }
    }
}
