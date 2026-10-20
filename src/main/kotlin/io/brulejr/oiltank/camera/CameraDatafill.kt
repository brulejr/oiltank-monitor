/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.oiltank.camera

import org.springframework.boot.context.properties.ConfigurationProperties
import java.nio.file.Path
import java.time.Duration

/**
 * @property enabled when false the feature is absent: nothing polls, and the camera API
 *   and indicators are not registered.
 * @property source where frames come from.
 * @property rtspUrl the camera stream, required for [SnapshotSourceType.FFMPEG]. May carry
 *   credentials, which are never logged or reported.
 * @property snapshotInterval the delay between the end of one capture and the start of the
 *   next. A tank drains over weeks, so anything short of minutes is plenty.
 * @property captureTimeout how long ffmpeg may take before it is killed. RTSP negotiation
 *   alone takes a second or two.
 * @property ffmpegPath the ffmpeg executable, looked up on `PATH` unless absolute.
 * @property file settings for [SnapshotSourceType.FILE].
 */
@ConfigurationProperties("oiltank.camera")
data class CameraDatafill(
    val enabled: Boolean = true,
    val source: SnapshotSourceType = SnapshotSourceType.FFMPEG,
    val rtspUrl: String? = null,
    val snapshotInterval: Duration = Duration.ofSeconds(30),
    val captureTimeout: Duration = Duration.ofSeconds(15),
    val ffmpegPath: String = "ffmpeg",
    val file: FileSource = FileSource(),
) {
    /** @property directory the images to replay, in file-name order, cycling back to the first. */
    data class FileSource(val directory: Path? = null)

    init {
        if (source == SnapshotSourceType.FFMPEG) {
            require(!rtspUrl.isNullOrBlank()) {
                "oiltank.camera.rtsp-url must be set when oiltank.camera.source is ffmpeg (set OILTANK_CAMERA_RTSP_URL)"
            }
        }
        if (source == SnapshotSourceType.FILE) {
            require(file.directory != null) { "oiltank.camera.file.directory must be set when oiltank.camera.source is file" }
        }
        require(snapshotInterval >= Duration.ofSeconds(5)) { "oiltank.camera.snapshot-interval must be at least 5s" }
        require(captureTimeout.isPositive) { "oiltank.camera.capture-timeout must be positive" }
        require(ffmpegPath.isNotBlank()) { "oiltank.camera.ffmpeg-path must not be blank" }
    }
}
