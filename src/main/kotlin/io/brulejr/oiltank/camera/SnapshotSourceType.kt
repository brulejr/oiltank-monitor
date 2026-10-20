/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.oiltank.camera

/** Which [SnapshotSource] the camera feature builds; bound case-insensitively from `oiltank.camera.source`. */
enum class SnapshotSourceType {
    /** The RTSP camera, through ffmpeg. */
    FFMPEG,

    /** Images replayed from a directory, so the pipeline runs without a camera. */
    FILE,
}
