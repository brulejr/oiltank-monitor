/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.oiltank.detection

import java.time.Instant

/**
 * What detection has made of the frames since startup.
 *
 * @property detections frames in which the float was found and read.
 * @property misses frames that yielded no reading: no float, too few marks, or no image.
 * @property passed readings passed on, having moved by at least the hysteresis delta.
 * @property lastAttemptAt when a frame was last processed, or null if none has been.
 * @property lastReading the latest reading, passed on or not.
 * @property lastAlignment where the marks were in the latest frame read.
 * @property lastModeComparison the latest day/night pair, or null before the first switch.
 * @property lastFailure why the latest frame yielded no reading, or null if it did.
 */
data class DetectionStatus(
    val detections: Long,
    val misses: Long,
    val passed: Long,
    val lastAttemptAt: Instant?,
    val lastReading: FloatReading?,
    val lastAlignment: MarkAlignment?,
    val lastModeComparison: ModeComparison?,
    val lastFailure: String?,
)
