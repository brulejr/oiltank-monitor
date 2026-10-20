/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.oiltank.pipeline

import io.brulejr.oiltank.camera.Snapshot
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

    @Bean(PipelineChannels.PIPELINE_ERRORS)
    fun pipelineErrorChannel(): MessageChannel = DirectChannel()

    /**
     * Ends the chain until detection subscribes to [PipelineChannels.SNAPSHOT]; remove it
     * then, since a direct channel with two subscribers load-balances between them.
     */
    @Bean
    fun snapshotSinkFlow() = integrationFlow(PipelineChannels.SNAPSHOT) {
        log<Snapshot>(LoggingHandler.Level.DEBUG, "oiltank.pipeline") { message ->
            "snapshot of ${message.payload.jpeg.size} bytes captured at ${message.payload.capturedAt}, no detection yet"
        }
        nullChannel()
    }

    /**
     * Logs a failure raised anywhere along the chain. The poller routes it here, since
     * every channel is direct and the exception unwinds back to it.
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
