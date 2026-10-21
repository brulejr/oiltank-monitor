/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.oiltank.actuator

import io.brulejr.oiltank.camera.CameraService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.actuate.info.Info
import org.springframework.boot.actuate.info.InfoContributor
import org.springframework.boot.health.contributor.HealthIndicator
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Every feature reports its health and describes itself on the info endpoint. */
@SpringBootTest(
    properties = [
        // Replays a fixture image rather than reaching a camera.
        "oiltank.camera.source=file",
        "oiltank.camera.file.directory=src/test/resources/gauge",
        // Nothing is published, so no broker is needed or reached.
        "oiltank.publish.enabled=false",
    ],
)
class ActuatorContributionTest {

    @Autowired
    lateinit var healthIndicators: Map<String, HealthIndicator>

    @Autowired
    lateinit var infoContributors: List<InfoContributor>

    @Autowired
    lateinit var cameraService: CameraService

    private val features = listOf("camera", "detection", "level", "publish")

    /**
     * Each stage is up only once a frame has passed through it. The poller sends one at
     * startup, but on its own thread, so one is sent here rather than raced.
     */
    @BeforeEach
    fun capture() {
        cameraService.captureNow()
    }

    @Test
    fun `every feature is up`() {
        features.forEach { feature ->
            val indicator = healthIndicators["${feature}HealthIndicator"]
            assertTrue(indicator != null, "no health indicator for $feature")
            assertEquals("UP", indicator.health()?.status?.code, "$feature is not up")
        }
    }

    @Test
    fun `every feature describes itself`() {
        val builder = Info.Builder()
        infoContributors.forEach { it.contribute(builder) }
        val details = builder.build().details

        features.forEach { feature -> assertTrue(feature in details, "no info for $feature") }
    }
}
