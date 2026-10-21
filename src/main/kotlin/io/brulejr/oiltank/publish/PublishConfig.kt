/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.oiltank.publish

import io.brulejr.oiltank.level.TankLevel
import io.brulejr.oiltank.pipeline.PipelineChannels
import io.brulejr.oiltank.publish.mqtt.MqttPublisher
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.integration.dsl.integrationFlow
import org.springframework.scheduling.TaskScheduler
import tools.jackson.databind.json.JsonMapper
import java.time.Clock
import java.time.Duration

/** Publishes each tank level to Home Assistant over MQTT, holding what fails until the broker is back. */
@Configuration
class PublishConfig(private val datafill: PublishDatafill) {

    @Bean
    fun homeAssistantMessages(jsonMapper: JsonMapper) = HomeAssistantMessages(datafill, jsonMapper)

    /** Each connection announces the sensor and its availability before anything else. */
    @Bean(destroyMethod = "close")
    fun mqttPublisher(messages: HomeAssistantMessages) =
        MqttPublisher(datafill.mqtt, will = messages.offline()) { listOf(messages.discovery(), messages.online()) }

    @Bean
    fun publishService(publisher: PublisherPort, messages: HomeAssistantMessages) =
        PublishService(publisher, messages, datafill, Clock.systemUTC())

    @Bean
    fun publishHealthIndicator(service: PublishService) = PublishHealthIndicator(service, datafill)

    @Bean
    fun publishInfoContributor(messages: HomeAssistantMessages) = PublishInfoContributor(datafill, messages)

    /**
     * Checks every second whether held messages are due another try. The backoff itself
     * is the service's, so this only needs to be finer than the shortest backoff.
     */
    @Bean
    fun publishRetryRunner(scheduler: TaskScheduler, service: PublishService) = ApplicationRunner {
        scheduler.scheduleWithFixedDelay({ service.retryIfDue() }, RETRY_TICK)
    }

    @Bean
    fun publishFlow(service: PublishService) = integrationFlow(PipelineChannels.TANK_LEVEL) {
        handle<TankLevel> { level, _ -> level.also { service.publish(it) } }
        nullChannel()
    }

    private companion object {
        val RETRY_TICK: Duration = Duration.ofSeconds(1)
    }
}
