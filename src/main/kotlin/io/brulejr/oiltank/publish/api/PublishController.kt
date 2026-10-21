/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.oiltank.publish.api

import io.brulejr.oiltank.publish.OutboundMessage
import io.brulejr.oiltank.publish.PublishService
import io.brulejr.oiltank.publish.PublishStats
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** What has been published, and re-announcing the sensor to Home Assistant. */
@RestController
@RequestMapping("/api/publish")
class PublishController(private val service: PublishService) {

    @GetMapping
    fun stats(): PublishStats = service.stats()

    /** The last message delivered to each topic. */
    @GetMapping("/messages")
    fun messages(): Map<String, OutboundMessage> = service.lastSent()

    /** Sends the discovery config again, returning the stats after it. */
    @PostMapping("/discovery")
    fun discovery(): PublishStats {
        service.publishDiscovery()
        return service.stats()
    }
}
