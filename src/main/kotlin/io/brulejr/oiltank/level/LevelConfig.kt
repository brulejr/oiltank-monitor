/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.oiltank.level

import io.brulejr.oiltank.detection.FloatReading
import io.brulejr.oiltank.pipeline.PipelineChannels
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.integration.dsl.integrationFlow
import org.springframework.integration.handler.LoggingHandler

/** Turns each reading detection passes on into a tank level for publishing. */
@Configuration
class LevelConfig(private val datafill: LevelDatafill) {

    @Bean
    fun levelService(meterRegistry: MeterRegistry) = LevelService(datafill, meterRegistry)

    @Bean
    fun levelHealthIndicator(service: LevelService) = LevelHealthIndicator(service)

    @Bean
    fun levelInfoContributor() = LevelInfoContributor(datafill)

    @Bean
    fun levelFlow(service: LevelService) = integrationFlow(PipelineChannels.FLOAT_READING) {
        transform<FloatReading> { service.toLevel(it) }
        log<TankLevel>(LoggingHandler.Level.DEBUG, "oiltank.level") { message ->
            "tank at ${message.payload.percent}% (${message.payload.status})"
        }
        channel(PipelineChannels.TANK_LEVEL)
    }
}
