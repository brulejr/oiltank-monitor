/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.oiltank.level

import org.springframework.boot.actuate.info.Info
import org.springframework.boot.actuate.info.InfoContributor

/** The thresholds a level is judged against. */
class LevelInfoContributor(private val datafill: LevelDatafill) : InfoContributor {

    override fun contribute(builder: Info.Builder) {
        builder.withDetail(
            "level",
            mapOf(
                "lowPercent" to datafill.lowPercent,
                "highPercent" to datafill.highPercent,
            ),
        )
    }
}
