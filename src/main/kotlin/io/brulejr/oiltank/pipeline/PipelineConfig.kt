/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.oiltank.pipeline

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.integration.channel.DirectChannel
import org.springframework.integration.dsl.integrationFlow
import org.springframework.integration.handler.LoggingHandler
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.MessagingException

/** Cross-cutting wiring shared by every stage: the channels that join them, and the log of their failures. */
@Configuration
class PipelineConfig {

    @Bean(PipelineChannels.SNAPSHOT)
    fun snapshotChannel(): MessageChannel = DirectChannel()

    @Bean(PipelineChannels.FLOAT_READING)
    fun floatReadingChannel(): MessageChannel = DirectChannel()

    @Bean(PipelineChannels.TANK_LEVEL)
    fun tankLevelChannel(): MessageChannel = DirectChannel()

    @Bean(PipelineChannels.PIPELINE_ERRORS)
    fun pipelineErrorChannel(): MessageChannel = DirectChannel()

    /**
     * Logs a failure raised anywhere along the chain. The poller routes it here, since
     * every channel is direct and the exception unwinds back to it, and so does
     * [io.brulejr.oiltank.camera.CameraService.captureNow] for a capture made through the API.
     *
     * Only the cause's message is logged, not the stack trace: an unreachable camera
     * fails every poll, and the stage's own health indicator already records it.
     */
    @Bean
    fun pipelineErrorFlow() = integrationFlow(PipelineChannels.PIPELINE_ERRORS) {
        log<MessagingException>(LoggingHandler.Level.WARN, "oiltank.errors") { message ->
            val failure = message.payload.cause ?: message.payload
            "${failure.javaClass.simpleName}: ${failure.message}"
        }
        nullChannel()
    }
}
