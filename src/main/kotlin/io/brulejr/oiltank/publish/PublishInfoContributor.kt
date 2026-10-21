/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.oiltank.publish

import org.springframework.boot.actuate.info.Info
import org.springframework.boot.actuate.info.InfoContributor

/** Where levels are published and how Home Assistant sees them. The credentials are left out. */
class PublishInfoContributor(
    private val datafill: PublishDatafill,
    private val messages: HomeAssistantMessages,
) : InfoContributor {

    override fun contribute(builder: Info.Builder) {
        builder.withDetail(
            "publish",
            mapOf(
                "enabled" to datafill.enabled,
                "broker" to datafill.mqtt.url,
                "clientId" to datafill.mqtt.clientId,
                "qos" to datafill.mqtt.qos,
                "retainState" to datafill.retainState,
                "sensorId" to datafill.homeAssistant.sensorId,
                "deviceId" to datafill.homeAssistant.deviceId,
                "topics" to listOf(messages.configTopic, messages.stateTopic, messages.attributesTopic, messages.availabilityTopic),
            ),
        )
    }
}
