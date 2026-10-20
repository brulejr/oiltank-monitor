/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.oiltank.camera

import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator
import java.time.Clock
import java.time.Duration

/**
 * Up while the latest capture succeeded and is recent: younger than three snapshot
 * intervals, so one slow capture does not flap it. Down once a capture fails or the
 * captures stop, and unknown until the first capture completes.
 */
class CameraHealthIndicator(
    private val service: CameraService,
    private val datafill: CameraDatafill,
    private val clock: Clock,
) : HealthIndicator {

    override fun health(): Health {
        val status = service.status()
        val lastCaptureAt = status.lastCaptureAt
        val builder = when {
            status.lastFailure != null -> Health.down()
            // Not yet attempted, or the first attempt is still running.
            lastCaptureAt == null -> Health.unknown()
            Duration.between(lastCaptureAt, clock.instant()) > datafill.snapshotInterval.multipliedBy(3) -> Health.down()
            else -> Health.up()
        }
        return builder
            .withDetail("captures", status.captures)
            .withDetail("failures", status.failures)
            .apply { status.lastCaptureAt?.let { withDetail("lastCaptureAt", it.toString()) } }
            .apply { status.lastFailure?.let { withDetail("lastFailure", it) } }
            .build()
    }
}
