/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.oiltank.pipeline

import io.brulejr.oiltank.publish.HomeAssistantMessages
import io.brulejr.oiltank.publish.OutboundMessage
import io.brulejr.oiltank.publish.PublishService
import io.brulejr.oiltank.publish.PublisherPort
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import tools.jackson.databind.json.JsonMapper
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * One real frame through all four stages, from the camera's poller to the MQTT messages
 * Home Assistant would receive.
 *
 * Every other Spring test turns publishing off, which stops a level at the publish
 * stage's door. This one leaves it on and swaps only the MQTT client for a recorder, so
 * the stages are joined exactly as in production. The broker URL points at a closed
 * port as well: if the recorder were ever bypassed, publishing would fail here rather
 * than reach a broker on this machine.
 */
@SpringBootTest(
    properties = [
        // Replays a fixture image rather than reaching a camera.
        "oiltank.camera.source=file",
        "oiltank.camera.file.directory=src/test/resources/gauge",
        // The poller's first capture, at startup, is the only one during the test.
        "oiltank.camera.snapshot-interval=1h",
        // Publishing on, to the recorder below; see the class comment.
        "oiltank.publish.enabled=true",
        "oiltank.publish.mqtt.url=tcp://127.0.0.1:1",
    ],
)
class PipelineEndToEndTest {

    @TestConfiguration
    class RecordingPublisherConfig {

        @Bean
        @Primary
        fun recordingPublisher() = RecordingPublisher()
    }

    /** Keeps every message delivered, in order, for the test to take. */
    class RecordingPublisher : PublisherPort {
        val delivered = LinkedBlockingQueue<OutboundMessage>()

        override fun publish(message: OutboundMessage) {
            delivered.put(message)
        }
    }

    @Autowired
    lateinit var publisher: RecordingPublisher

    @Autowired
    lateinit var messages: HomeAssistantMessages

    @Autowired
    lateinit var publishService: PublishService

    @Autowired
    lateinit var jsonMapper: JsonMapper

    @Test
    fun `a frame from the camera reaches Home Assistant as the level it shows`() {
        val state = next()
        val attributes = next()

        // Every fixture frame shows the gauge at 1/2; GaugeReadingTest allows ±0.03.
        assertEquals(messages.stateTopic, state.topic)
        assertTrue(state.retained)
        val percent = state.payload.toInt()
        assertTrue(percent in 47..53, "read $percent %, expected about 50")

        assertEquals(messages.attributesTopic, attributes.topic)
        val details = jsonMapper.readTree(attributes.payload)
        assertEquals(percent, details["percentage"].asInt())
        assertEquals("NORMAL", details["status"].asString())

        val stats = publishService.stats()
        assertEquals(2, stats.published)
        assertEquals(0, stats.failures)
    }

    /** The next message delivered, waiting for the poller's thread to bring it. */
    private fun next(): OutboundMessage =
        assertNotNull(publisher.delivered.poll(30, TimeUnit.SECONDS), "nothing was published")
}
