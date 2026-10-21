/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.oiltank.detection

import io.brulejr.oiltank.camera.Snapshot
import io.brulejr.oiltank.pipeline.PipelineChannels
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.integration.dsl.integrationFlow
import org.springframework.integration.handler.LoggingHandler

/**
 * Reads the gauge in each snapshot and passes on the smoothed reading, but only once it
 * has moved by the hysteresis delta, so frame-to-frame noise goes no further.
 */
@Configuration
class DetectionConfig(private val datafill: DetectionDatafill) {

    @Bean
    fun floatDetector(): FloatDetector = DayNightFloatDetector(
        datafill.roi,
        color = ColorFloatDetector(datafill.roi, datafill.color, datafill.minArea),
        infrared = InfraredFloatDetector(datafill.roi, datafill.infrared, datafill.minArea),
    )

    @Bean
    fun markTracker() = MarkTracker(datafill.roi, datafill.marks)

    @Bean
    fun detectionService(detector: FloatDetector, tracker: MarkTracker, meterRegistry: MeterRegistry) =
        DetectionService(detector, tracker, datafill, meterRegistry)

    @Bean
    fun detectionHealthIndicator(service: DetectionService) = DetectionHealthIndicator(service)

    @Bean
    fun detectionInfoContributor() = DetectionInfoContributor(datafill)

    /** A frame without a float throws, and the failure reaches the pipeline error log. */
    @Bean
    fun detectionFlow(service: DetectionService) = integrationFlow(PipelineChannels.SNAPSHOT) {
        transform<Snapshot> { service.process(it) }
        log<FloatReading>(LoggingHandler.Level.DEBUG, "oiltank.detection") { message ->
            val reading = message.payload
            "float read at y=${reading.floatY} (${reading.mode}): raw=%.3f smoothed=%.3f".format(reading.raw, reading.smoothed)
        }
        filter<FloatReading>({ service.shouldPass(it) })
        channel(PipelineChannels.FLOAT_READING)
    }
}
