/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.oiltank.publish

import io.brulejr.oiltank.detection.DetectionMode
import io.brulejr.oiltank.detection.FloatReading
import io.brulejr.oiltank.level.LevelStatus
import io.brulejr.oiltank.level.TankLevel
import java.time.Instant

/** A level of [percent], read by day at a fixed time, for tests that publish one. */
fun testLevel(percent: Int, status: LevelStatus = LevelStatus.NORMAL) = TankLevel(
    percent,
    status,
    FloatReading(629, percent / 100.0, percent / 100.0, DetectionMode.COLOR, Instant.parse("2026-10-21T12:00:00Z")),
)
