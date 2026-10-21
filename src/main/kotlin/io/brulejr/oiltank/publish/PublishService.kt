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
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Stage 4: publishes each tank level to Home Assistant, riding out broker outages.
 *
 * A message that cannot be delivered is held, and so is everything after it until the
 * broker is back, at most one per topic: a newer message for a topic replaces the one
 * held, and moves to the back of the line. Holding matters here because detection
 * passes a level on only when it changes, which can be hours apart; dropping one would
 * leave Home Assistant stale until the next.
 *
 * While anything is held the circuit is open: nothing new is tried, and the held
 * messages are retried in the order they arrived, after a backoff that doubles with
 * each failed retry. The first success closes it. What is held lives in memory, like
 * everything else here; a restart loses it, and the next level brings fresh state.
 */
class PublishService(
    private val publisher: PublisherPort,
    private val messages: HomeAssistantMessages,
    private val datafill: PublishDatafill,
    private val clock: Clock,
) {

    private val log = LoggerFactory.getLogger("oiltank.publish")

    // Guarded by this. Insertion-ordered, so held messages go out in arrival order.
    private val held = LinkedHashMap<String, OutboundMessage>()
    private val lastSent = LinkedHashMap<String, OutboundMessage>()
    private var circuitOpen = false
    private var backoff: Duration = datafill.retry.initialBackoff
    private var nextAttemptAt: Instant? = null
    private var published = 0L
    private var superseded = 0L
    private var failures = 0L
    private var dropped = 0L
    private var lastPublishedAt: Instant? = null
    private var lastFailure: String? = null

    /** Publishes [level]'s state and attributes. */
    fun publish(level: TankLevel) = messages.level(level).forEach(::publish)

    /** Sends the discovery config again, as after a reconnect; for when Home Assistant has lost it. */
    fun publishDiscovery() = publish(messages.discovery())

    /** Delivers [message] now if the broker is healthy, otherwise holds it. */
    @Synchronized
    fun publish(message: OutboundMessage) {
        if (!datafill.enabled) {
            dropped++
            return
        }
        if (circuitOpen) {
            hold(message)
            return
        }
        try {
            send(message)
        } catch (e: Exception) {
            hold(message)
            trip(e)
        }
    }

    /** Retries what is held once its backoff has passed. Called on a timer. */
    @Synchronized
    fun retryIfDue() {
        val due = nextAttemptAt ?: return
        if (circuitOpen && !clock.instant().isBefore(due)) flush()
    }

    /**
     * Tries every held message now, in arrival order, stopping at the first failure.
     * Returns true when nothing is left held.
     */
    @Synchronized
    fun flush(): Boolean {
        val iterator = held.values.iterator()
        while (iterator.hasNext()) {
            val message = iterator.next()
            try {
                send(message)
            } catch (e: Exception) {
                trip(e)
                return false
            }
            iterator.remove()
        }
        if (circuitOpen) {
            log.info("Publishing recovered; every held message is delivered")
            circuitOpen = false
            backoff = datafill.retry.initialBackoff
            nextAttemptAt = null
        }
        return true
    }

    /** The last message delivered to each topic, for the API. */
    @Synchronized
    fun lastSent(): Map<String, OutboundMessage> = LinkedHashMap(lastSent)

    @Synchronized
    fun stats() = PublishStats(
        published = published,
        pending = held.size,
        superseded = superseded,
        failures = failures,
        dropped = dropped,
        circuitOpen = circuitOpen,
        lastPublishedAt = lastPublishedAt,
        lastFailure = lastFailure,
        nextAttemptAt = nextAttemptAt,
    )

    private fun send(message: OutboundMessage) {
        publisher.publish(message)
        published++
        lastPublishedAt = clock.instant()
        lastSent[message.topic] = message
        log.debug("published {} to {}{}", message.payload, message.topic, if (message.retained) " (retained)" else "")
    }

    private fun hold(message: OutboundMessage) {
        // Removed first so a replacement joins the back of the line.
        if (held.remove(message.topic) != null) superseded++
        held[message.topic] = message
    }

    private fun trip(e: Exception) {
        failures++
        lastFailure = e.message ?: e.javaClass.simpleName
        if (circuitOpen) {
            backoff = minOf(backoff.multipliedBy(2), datafill.retry.maxBackoff)
        } else {
            circuitOpen = true
            backoff = datafill.retry.initialBackoff
            log.warn(
                "Publishing to {} failed ({}); holding messages, latest per topic, until it recovers",
                datafill.mqtt.url, lastFailure,
            )
        }
        nextAttemptAt = clock.instant().plus(backoff)
    }
}
