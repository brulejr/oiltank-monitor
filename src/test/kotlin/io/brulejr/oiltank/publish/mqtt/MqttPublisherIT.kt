/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.oiltank.publish.mqtt

import io.brulejr.oiltank.publish.HomeAssistantMessages
import io.brulejr.oiltank.publish.PublishDatafill
import io.brulejr.oiltank.publish.testLevel
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Publishes to a real broker on localhost:1883, and is skipped when none is running.
 * Everything goes under a throwaway discovery prefix, so a Home Assistant listening on
 * the same broker never sees it, and its retained messages are cleared afterwards.
 */
class MqttPublisherIT {

    private val url = "tcp://localhost:1883"
    private val prefix = "oiltank-test-${UUID.randomUUID().toString().take(8)}"
    private val datafill = PublishDatafill(homeAssistant = PublishDatafill.HomeAssistant(discoveryPrefix = prefix))
    private val messages = HomeAssistantMessages(datafill, JsonMapper.builder().build())
    private val received = ConcurrentHashMap<String, String>()
    private lateinit var observer: MqttClient

    @BeforeEach
    fun connect() {
        val reachable = runCatching { Socket().use { it.connect(InetSocketAddress("localhost", 1883), 500) } }.isSuccess
        assumeTrue(reachable, "no MQTT broker on localhost:1883")
        observer = MqttClient(url, "oiltank-test-observer-$prefix", MemoryPersistence()).apply {
            connect()
            subscribe("$prefix/#") { topic, message -> received[topic] = String(message.payload) }
        }
    }

    @AfterEach
    fun clearRetained() {
        if (!::observer.isInitialized) return
        received.keys.forEach { observer.publish(it, MqttMessage(ByteArray(0)).apply { isRetained = true }) }
        observer.disconnect()
        observer.close()
    }

    private fun publisher() = MqttPublisher(datafill.mqtt.copy(clientId = "oiltank-test-$prefix"), messages.offline()) {
        listOf(messages.discovery(), messages.online())
    }

    private fun awaitTopic(topic: String, payload: String) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (received[topic] != payload && System.nanoTime() < deadline) Thread.sleep(20)
        assertEquals(payload, received[topic], topic)
    }

    @Test
    fun `the first publish announces the sensor, then sends the level`() {
        publisher().use { publisher ->
            messages.level(testLevel(49)).forEach(publisher::publish)

            awaitTopic(messages.stateTopic, "49")
            awaitTopic(messages.availabilityTopic, "online")
            assertTrue(received.getValue(messages.configTopic).contains("\"unique_id\":\"oiltank_level\""))
        }
    }

    @Test
    fun `closing marks the sensor offline`() {
        val publisher = publisher()
        publisher.publish(messages.level(testLevel(49)).first())
        awaitTopic(messages.availabilityTopic, "online")

        publisher.close()

        awaitTopic(messages.availabilityTopic, "offline")
    }
}
