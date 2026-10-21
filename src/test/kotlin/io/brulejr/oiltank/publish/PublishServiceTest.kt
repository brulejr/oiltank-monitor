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
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PublishServiceTest {

    /** Records what it is given, and fails while [down]. */
    private class FakePublisher : PublisherPort {
        var down = false
        val sent = mutableListOf<OutboundMessage>()

        override fun publish(message: OutboundMessage) {
            if (down) throw IllegalStateException("broker unreachable")
            sent += message
        }
    }

    private class MutableClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneId.of("UTC")
        override fun withZone(zone: ZoneId?) = this
        override fun instant() = now
    }

    private val publisher = FakePublisher()
    private val clock = MutableClock(Instant.parse("2026-10-21T12:00:00Z"))
    private val messages = HomeAssistantMessages(PublishDatafill(), JsonMapper.builder().build())

    private fun service(datafill: PublishDatafill = PublishDatafill()) = PublishService(publisher, messages, datafill, clock)

    @Test
    fun `a level is published as state then attributes`() {
        service().publish(testLevel(49))

        assertEquals(listOf(messages.stateTopic, messages.attributesTopic), publisher.sent.map { it.topic })
    }

    @Test
    fun `while the broker is down the latest message per topic is held`() {
        val service = service()
        publisher.down = true

        service.publish(testLevel(49))
        service.publish(testLevel(48))

        val stats = service.stats()
        assertTrue(stats.circuitOpen)
        assertEquals(2, stats.pending)
        assertEquals(2, stats.superseded)
    }

    @Test
    fun `held messages go out once the backoff has passed and the broker is back`() {
        val service = service()
        publisher.down = true
        service.publish(testLevel(48))
        publisher.down = false

        service.retryIfDue()
        assertTrue(publisher.sent.isEmpty(), "not yet due")

        clock.now += Duration.ofSeconds(1)
        service.retryIfDue()

        assertEquals("48", publisher.sent.first { it.topic == messages.stateTopic }.payload)
        assertFalse(service.stats().circuitOpen)
    }

    @Test
    fun `the backoff doubles with each failed retry, up to the maximum`() {
        val service = service(PublishDatafill(retry = PublishDatafill.Retry(Duration.ofSeconds(1), Duration.ofSeconds(3))))
        publisher.down = true
        service.publish(testLevel(48))

        val waits = (1..3).map {
            val due = service.stats().nextAttemptAt!!
            val wait = Duration.between(clock.now, due)
            clock.now = due
            service.retryIfDue()
            wait.seconds
        }

        assertEquals(listOf(1L, 2L, 3L), waits)
    }

    @Test
    fun `disabled, nothing is sent`() {
        val service = service(PublishDatafill(enabled = false))

        service.publish(testLevel(49))

        assertTrue(publisher.sent.isEmpty())
        assertEquals(2, service.stats().dropped)
    }

    @Test
    fun `the last message per topic is kept for the API`() {
        val service = service()
        service.publish(testLevel(49))
        service.publish(testLevel(48))

        assertEquals("48", service.lastSent().getValue(messages.stateTopic).payload)
    }
}
