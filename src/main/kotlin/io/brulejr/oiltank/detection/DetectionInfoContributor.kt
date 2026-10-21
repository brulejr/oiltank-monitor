/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.oiltank.detection

import org.springframework.boot.actuate.info.Info
import org.springframework.boot.actuate.info.InfoContributor

/** Where the float is looked for, how it is recognised, and how its position is read. */
class DetectionInfoContributor(private val datafill: DetectionDatafill) : InfoContributor {

    override fun contribute(builder: Info.Builder) {
        builder.withDetail(
            "detection",
            mapOf(
                "roi" to datafill.roi,
                "color" to datafill.color,
                "infrared" to datafill.infrared,
                "minArea" to datafill.minArea,
                "marks" to datafill.marks,
                "referenceOffset" to datafill.referenceOffset,
                "crossCheck" to mapOf(
                    "window" to datafill.crossCheck.window.toString(),
                    "tolerance" to datafill.crossCheck.tolerance,
                ),
                "smoothingAlpha" to datafill.smoothingAlpha,
                "hysteresisDelta" to datafill.hysteresisDelta,
            ),
        )
    }
}
