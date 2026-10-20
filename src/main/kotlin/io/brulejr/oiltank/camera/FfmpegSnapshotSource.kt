/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.oiltank.camera

import java.io.IOException
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * Captures one frame from an RTSP stream by running ffmpeg, which writes it to stdout as
 * a JPEG. A new process per frame: simple, and at one frame every half minute the second
 * or two of RTSP negotiation each time does not matter.
 *
 * TCP transport, since the default UDP drops packets on a busy network and a frame with
 * missing slices can still decode.
 */
class FfmpegSnapshotSource(
    private val ffmpegPath: String,
    private val rtspUrl: String,
    private val timeout: Duration,
) : SnapshotSource {

    override fun capture(): ByteArray {
        val process = try {
            ProcessBuilder(command()).start()
        } catch (e: IOException) {
            throw SnapshotException("cannot run $ffmpegPath: ${e.message}", e)
        }
        process.outputStream.close()
        // Both streams are drained while ffmpeg runs, or a full pipe buffer would stall it.
        val stdout = CompletableFuture.supplyAsync { process.inputStream.readAllBytes() }
        val stderr = CompletableFuture.supplyAsync { process.errorStream.readAllBytes().decodeToString() }

        if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            process.destroyForcibly()
            throw SnapshotException("ffmpeg did not finish within $timeout")
        }
        val exitCode = process.exitValue()
        if (exitCode != 0) {
            val reason = redactUrlIn(stderr.join().trim(), rtspUrl).ifEmpty { "no output" }
            throw SnapshotException("ffmpeg exited with $exitCode: $reason")
        }
        return stdout.join().also { if (it.isEmpty()) throw SnapshotException("ffmpeg produced no image") }
    }

    private fun command() = listOf(
        ffmpegPath,
        "-hide_banner", "-loglevel", "error",
        "-rtsp_transport", "tcp",
        "-i", rtspUrl,
        "-frames:v", "1",
        "-f", "mjpeg",
        "pipe:1",
    )
}
