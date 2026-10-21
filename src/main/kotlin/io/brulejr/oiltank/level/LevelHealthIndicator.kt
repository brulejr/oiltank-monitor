/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.oiltank.level

import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator

/**
 * Up once a level is known, unknown before. A low tank is not a fault of the
 * application, so its status is a detail here, never DOWN; whether readings are
 * arriving at all is detection's and the camera's to report.
 */
class LevelHealthIndicator(private val service: LevelService) : HealthIndicator {

    override fun health(): Health {
        val level = service.latest() ?: return Health.unknown().build()
        return Health.up()
            .withDetail("percent", level.percent)
            .withDetail("status", level.status)
            .withDetail("capturedAt", level.reading.capturedAt.toString())
            .build()
    }
}
