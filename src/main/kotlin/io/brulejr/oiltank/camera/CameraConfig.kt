/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.oiltank.camera

import io.brulejr.oiltank.pipeline.PipelineChannels
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.integration.dsl.Pollers
import org.springframework.integration.dsl.integrationFlow
import org.springframework.messaging.MessageChannel
import java.time.Clock

/**
 * Captures a frame every snapshot interval and hands it to [PipelineChannels.SNAPSHOT].
 * The poller is the schedule: a fixed delay, so a slow capture pushes the next one back
 * rather than overlapping it.
 */
@Configuration
@ConditionalOnBooleanProperty("oiltank.camera.enabled", matchIfMissing = true)
class CameraConfig(private val datafill: CameraDatafill) {

    private val clock = Clock.systemUTC()

    @Bean
    fun snapshotSource(): SnapshotSource = when (datafill.source) {
        SnapshotSourceType.FFMPEG -> FfmpegSnapshotSource(datafill.ffmpegPath, datafill.rtspUrl!!, datafill.captureTimeout)
        SnapshotSourceType.FILE -> FileSnapshotSource(datafill.file.directory!!)
    }

    @Bean
    fun cameraService(
        source: SnapshotSource,
        @Qualifier(PipelineChannels.SNAPSHOT) snapshotChannel: MessageChannel,
        @Qualifier(PipelineChannels.PIPELINE_ERRORS) errorChannel: MessageChannel,
    ) = CameraService(source, snapshotChannel, errorChannel, clock)

    @Bean
    fun cameraHealthIndicator(service: CameraService) = CameraHealthIndicator(service, datafill, clock)

    @Bean
    fun cameraInfoContributor() = CameraInfoContributor(datafill)

    /** A failed capture goes to the pipeline error log, and the next poll starts clean. */
    @Bean
    fun cameraFlow(service: CameraService) = integrationFlow(
        { service.capture() },
        { poller(Pollers.fixedDelay(datafill.snapshotInterval).errorChannel(PipelineChannels.PIPELINE_ERRORS)) },
    ) {
        channel(PipelineChannels.SNAPSHOT)
    }
}
