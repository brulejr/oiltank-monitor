/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.oiltank.publish

import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HomeAssistantMessagesTest {

    private val jsonMapper = JsonMapper.builder().build()
    private val messages = HomeAssistantMessages(PublishDatafill(), jsonMapper)

    @Test
    fun `the topics keep the prototype's`() {
        assertEquals("homeassistant/sensor/oiltank_level/config", messages.configTopic)
        assertEquals("homeassistant/sensor/oiltank_level/state", messages.stateTopic)
        assertEquals("homeassistant/sensor/oiltank_level/attributes", messages.attributesTopic)
        assertEquals("homeassistant/sensor/oiltank_level/availability", messages.availabilityTopic)
    }

    @Test
    fun `discovery keeps the prototype's identity, so the existing entity carries over`() {
        val discovery = messages.discovery()
        val config = jsonMapper.readTree(discovery.payload)

        assertTrue(discovery.retained)
        assertEquals("oiltank_level", config["unique_id"].asString())
        assertEquals("oiltank_monitor", config["device"]["identifiers"][0].asString())
        assertEquals("%", config["unit_of_measurement"].asString())
        assertEquals("measurement", config["state_class"].asString())
        assertEquals(messages.availabilityTopic, config["availability_topic"].asString())
        assertEquals(messages.attributesTopic, config["json_attributes_topic"].asString())
    }

    @Test
    fun `a level is its percentage as state, and the reading behind it as attributes`() {
        val (state, attributes) = messages.level(testLevel(49))
        val json = jsonMapper.readTree(attributes.payload)

        assertEquals(OutboundMessage(messages.stateTopic, "49", retained = true), state)
        assertEquals(49, json["percentage"].asInt())
        assertEquals("NORMAL", json["status"].asString())
        assertEquals("COLOR", json["mode"].asString())
        assertEquals("2026-10-21T12:00:00Z", json["timestampIso"].asString())
    }

    @Test
    fun `availability is retained, online and offline`() {
        assertEquals(OutboundMessage(messages.availabilityTopic, "online", retained = true), messages.online())
        assertEquals(OutboundMessage(messages.availabilityTopic, "offline", retained = true), messages.offline())
    }
}
