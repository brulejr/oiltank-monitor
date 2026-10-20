/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.oiltank.camera

import java.time.Instant

/**
 * What the camera has captured since startup.
 *
 * @property captures frames captured and passed on.
 * @property failures attempts that produced no usable frame.
 * @property lastAttemptAt when a capture last started, or null if none has.
 * @property lastCaptureAt when a frame was last captured, or null if none has been.
 * @property lastFailure why the latest attempt failed, or null if it succeeded.
 */
data class CameraStatus(
    val captures: Long,
    val failures: Long,
    val lastAttemptAt: Instant?,
    val lastCaptureAt: Instant?,
    val lastFailure: String?,
)
