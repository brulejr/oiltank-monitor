/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.oiltank.camera

import org.springframework.boot.actuate.info.Info
import org.springframework.boot.actuate.info.InfoContributor

/** Where frames come from and how often. The stream URL is shown without its credentials. */
class CameraInfoContributor(private val datafill: CameraDatafill) : InfoContributor {

    override fun contribute(builder: Info.Builder) {
        val source = when (datafill.source) {
            SnapshotSourceType.FFMPEG -> mapOf(
                "type" to "ffmpeg",
                "rtspUrl" to redactCredentials(datafill.rtspUrl.orEmpty()),
                "ffmpegPath" to datafill.ffmpegPath,
                "captureTimeout" to datafill.captureTimeout.toString(),
            )
            SnapshotSourceType.FILE -> mapOf("type" to "file", "directory" to datafill.file.directory.toString())
        }
        builder.withDetail(
            "camera",
            mapOf(
                "source" to source,
                "snapshotInterval" to datafill.snapshotInterval.toString(),
            ),
        )
    }
}
