/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.oiltank.publish.mqtt

import io.brulejr.oiltank.publish.OutboundMessage
import io.brulejr.oiltank.publish.PublishDatafill
import io.brulejr.oiltank.publish.PublisherPort
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.nio.charset.StandardCharsets

/**
 * Publishes over MQTT with a plain Paho client, connecting on demand.
 *
 * Every connection registers [will] as the last will and then sends [birth] first, so
 * after each reconnect Home Assistant gets the discovery config and sees the sensor
 * online again. [close] sends [will] itself, since the broker only publishes a last will
 * when a connection is lost, not when it is closed cleanly.
 *
 * Paho's own automatic reconnection is off: [io.brulejr.oiltank.publish.PublishService]
 * decides when to try again. Nothing is persisted by the client either; what failed is
 * held by the service.
 */
class MqttPublisher(
    private val datafill: PublishDatafill.Mqtt,
    private val will: OutboundMessage,
    private val birth: () -> List<OutboundMessage>,
) : PublisherPort, AutoCloseable {

    private val client = MqttClient(datafill.url, datafill.clientId, MemoryPersistence()).apply {
        timeToWait = datafill.completionTimeout.toMillis()
    }

    private val options = MqttConnectOptions().apply {
        isAutomaticReconnect = false
        isCleanSession = true
        connectionTimeout = datafill.connectTimeout.seconds.toInt()
        datafill.username?.takeIf { it.isNotBlank() }?.let { userName = it }
        datafill.password?.takeIf { it.isNotBlank() }?.let { password = it.toCharArray() }
        setWill(will.topic, will.payload.toByteArray(StandardCharsets.UTF_8), datafill.qos, will.retained)
    }

    @Synchronized
    override fun publish(message: OutboundMessage) {
        if (!client.isConnected) {
            client.connect(options)
            birth().forEach(::send)
        }
        send(message)
    }

    @Synchronized
    override fun close() {
        runCatching {
            if (client.isConnected) {
                send(will)
                client.disconnect()
            }
        }
        client.close()
    }

    private fun send(message: OutboundMessage) =
        client.publish(message.topic, message.payload.toByteArray(StandardCharsets.UTF_8), datafill.qos, message.retained)
}
