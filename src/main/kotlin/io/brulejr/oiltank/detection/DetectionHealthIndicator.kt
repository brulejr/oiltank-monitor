/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.oiltank.detection

import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator

/**
 * Up while the latest frame yielded a reading, down when it did not, and unknown until
 * the first frame. Down rather than merely noted: a frame without a float or its marks
 * most likely means the camera moved or the lighting changed, and needs recalibrating.
 *
 * The marks' drift and the latest day/night comparison are details, not status: a
 * growing shift or a comparison outside tolerance is a reason to recalibrate soon, not
 * a failure to read.
 */
class DetectionHealthIndicator(private val service: DetectionService) : HealthIndicator {

    override fun health(): Health {
        val status = service.status()
        val builder = when {
            status.lastAttemptAt == null -> Health.unknown()
            status.lastFailure != null -> Health.down()
            else -> Health.up()
        }
        return builder
            .withDetail("detections", status.detections)
            .withDetail("misses", status.misses)
            .withDetail("passed", status.passed)
            .apply { status.lastReading?.let { withDetail("lastReading", it) } }
            .apply { status.lastAlignment?.let { withDetail("markShift", it.shift).withDetail("marksMatched", it.matched) } }
            .apply { status.lastModeComparison?.let { withDetail("dayNightComparison", it) } }
            .apply { status.lastFailure?.let { withDetail("lastFailure", it) } }
            .build()
    }
}
