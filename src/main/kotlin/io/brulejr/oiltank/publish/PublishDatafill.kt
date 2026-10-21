/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.oiltank.publish

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * @property enabled when false, levels are counted and dropped, and nothing connects to
 *   the broker. The Spring tests turn it off.
 * @property mqtt the broker Home Assistant listens on.
 * @property retainState whether the state and attributes are retained, so Home Assistant
 *   shows the last level straight after it restarts. Discovery and availability are
 *   always retained.
 * @property retry how a broker outage is ridden out.
 * @property homeAssistant how the sensor appears in Home Assistant.
 */
@ConfigurationProperties("oiltank.publish")
data class PublishDatafill(
    val enabled: Boolean = true,
    val mqtt: Mqtt = Mqtt(),
    val retainState: Boolean = true,
    val retry: Retry = Retry(),
    val homeAssistant: HomeAssistant = HomeAssistant(),
) {
    /**
     * @property username blank means none, so an unset environment variable can stand for it.
     * @property connectTimeout how long a connection attempt may take before the publish
     *   counts as failed.
     * @property completionTimeout how long a publish may wait for the broker to confirm it.
     */
    data class Mqtt(
        val url: String = "tcp://localhost:1883",
        val clientId: String = "oiltank-monitor",
        val username: String? = null,
        val password: String? = null,
        val qos: Int = 1,
        val connectTimeout: Duration = Duration.ofSeconds(5),
        val completionTimeout: Duration = Duration.ofSeconds(10),
    ) {
        init {
            require(url.isNotBlank()) { "oiltank.publish.mqtt.url must not be blank" }
            require(clientId.isNotBlank()) { "oiltank.publish.mqtt.client-id must not be blank" }
            require(qos in 0..2) { "oiltank.publish.mqtt.qos must be 0, 1 or 2" }
            require(connectTimeout.seconds >= 1) { "oiltank.publish.mqtt.connect-timeout must be at least 1s" }
            require(completionTimeout.isPositive) { "oiltank.publish.mqtt.completion-timeout must be positive" }
        }
    }

    /**
     * @property initialBackoff the wait before the first retry once a publish fails.
     * @property maxBackoff the longest wait between retries. The wait doubles after each
     *   failed retry up to this.
     */
    data class Retry(
        val initialBackoff: Duration = Duration.ofSeconds(1),
        val maxBackoff: Duration = Duration.ofMinutes(1),
    ) {
        init {
            require(initialBackoff.isPositive) { "oiltank.publish.retry.initial-backoff must be positive" }
            require(maxBackoff >= initialBackoff) { "oiltank.publish.retry.max-backoff must not be shorter than initial-backoff" }
        }
    }

    /**
     * The defaults are the prototype's: keeping [sensorId] and [deviceId] keeps the
     * existing Home Assistant entity, and its history, when this replaces it.
     *
     * @property sensorId the entity's `unique_id`, and its topics' path segment.
     */
    data class HomeAssistant(
        val discoveryPrefix: String = "homeassistant",
        val sensorId: String = "oiltank_level",
        val sensorName: String = "Oil Tank Level",
        val deviceId: String = "oiltank_monitor",
        val deviceName: String = "Oil Tank Monitor",
        val deviceManufacturer: String = "brulejr",
        val deviceModel: String = "oiltank-monitor",
    ) {
        init {
            require(discoveryPrefix.isNotBlank()) { "oiltank.publish.home-assistant.discovery-prefix must not be blank" }
            require(sensorId.matches(Regex("[a-z0-9_]+"))) { "oiltank.publish.home-assistant.sensor-id must be [a-z0-9_]+" }
            require(deviceId.isNotBlank()) { "oiltank.publish.home-assistant.device-id must not be blank" }
        }
    }
}
