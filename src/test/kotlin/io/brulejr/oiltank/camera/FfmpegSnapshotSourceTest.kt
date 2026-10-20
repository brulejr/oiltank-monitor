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
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.time.Duration
import kotlin.io.path.readText
import kotlin.io.path.setPosixFilePermissions
import kotlin.io.path.writeText
import kotlin.test.assertEquals

/** Runs a stand-in shell script in place of ffmpeg, so each outcome can be staged. */
class FfmpegSnapshotSourceTest {

    @TempDir
    lateinit var directory: Path

    private val url = "rtsp://user:s3cret@cam:554/stream1"

    private fun fakeFfmpeg(body: String): String {
        val script = directory.resolve("ffmpeg")
        script.writeText("#!/bin/sh\n$body\n")
        script.setPosixFilePermissions(PosixFilePermissions.fromString("rwx------"))
        return script.toString()
    }

    private fun source(ffmpeg: String, timeout: Duration = Duration.ofSeconds(5)) = FfmpegSnapshotSource(ffmpeg, url, timeout)

    @Test
    fun `stdout is the frame`() {
        val args = directory.resolve("args")
        val ffmpeg = fakeFfmpeg("echo \"$@\" > $args\nprintf frame")

        assertEquals("frame", source(ffmpeg).capture().decodeToString())
        assertEquals(
            "-hide_banner -loglevel error -rtsp_transport tcp -i $url -frames:v 1 -f mjpeg pipe:1",
            args.readText().trim(),
        )
    }

    @Test
    fun `a non-zero exit fails with stderr, credentials redacted`() {
        val ffmpeg = fakeFfmpeg("echo \"$url: Connection refused\" >&2\nexit 1")

        val failure = assertThrows<SnapshotException> { source(ffmpeg).capture() }

        assertEquals("ffmpeg exited with 1: rtsp://***@cam:554/stream1: Connection refused", failure.message)
    }

    @Test
    fun `no output is a failure`() {
        val failure = assertThrows<SnapshotException> { source(fakeFfmpeg("exit 0")).capture() }

        assertEquals("ffmpeg produced no image", failure.message)
    }

    @Test
    fun `a capture that overruns the timeout is killed`() {
        val failure = assertThrows<SnapshotException> {
            source(fakeFfmpeg("exec sleep 10"), Duration.ofMillis(200)).capture()
        }

        assertEquals("ffmpeg did not finish within PT0.2S", failure.message)
    }

    @Test
    fun `a missing executable is a failure`() {
        assertThrows<SnapshotException> { source(directory.resolve("absent").toString()).capture() }
    }
}
