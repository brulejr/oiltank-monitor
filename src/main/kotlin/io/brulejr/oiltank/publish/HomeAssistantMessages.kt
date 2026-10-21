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
import tools.jackson.databind.json.JsonMapper

/**
 * The MQTT messages that make the tank a Home Assistant sensor, through MQTT discovery:
 * all under `<discovery-prefix>/sensor/<sensor-id>/`.
 *
 * - `config`: the discovery payload, retained, so Home Assistant finds the sensor after
 *   either side restarts.
 * - `state`: the percentage.
 * - `attributes`: the reading behind it. `percentage` and `timestampIso` keep the
 *   prototype's names, so templates written against it still work.
 * - `availability`: `online` while connected; the broker publishes `offline` as the
 *   last will if the connection drops, and it is sent on a clean shutdown too.
 */
class HomeAssistantMessages(
    private val datafill: PublishDatafill,
    private val jsonMapper: JsonMapper,
) {

    private val ha = datafill.homeAssistant
    private val base = "${ha.discoveryPrefix}/sensor/${ha.sensorId}"

    val configTopic = "$base/config"
    val stateTopic = "$base/state"
    val attributesTopic = "$base/attributes"
    val availabilityTopic = "$base/availability"

    fun discovery() = OutboundMessage(
        configTopic,
        jsonMapper.writeValueAsString(
            mapOf(
                "name" to ha.sensorName,
                "unique_id" to ha.sensorId,
                "state_topic" to stateTopic,
                "json_attributes_topic" to attributesTopic,
                "availability_topic" to availabilityTopic,
                "unit_of_measurement" to "%",
                "state_class" to "measurement",
                "icon" to "mdi:gauge",
                "device" to mapOf(
                    "identifiers" to listOf(ha.deviceId),
                    "name" to ha.deviceName,
                    "manufacturer" to ha.deviceManufacturer,
                    "model" to ha.deviceModel,
                ),
            ),
        ),
        retained = true,
    )

    fun online() = OutboundMessage(availabilityTopic, ONLINE, retained = true)

    fun offline() = OutboundMessage(availabilityTopic, OFFLINE, retained = true)

    /** The state and attributes for [level], in that order. */
    fun level(level: TankLevel): List<OutboundMessage> = listOf(
        OutboundMessage(stateTopic, level.percent.toString(), datafill.retainState),
        OutboundMessage(
            attributesTopic,
            jsonMapper.writeValueAsString(
                mapOf(
                    "percentage" to level.percent,
                    "status" to level.status,
                    "raw" to level.reading.raw,
                    "smoothed" to level.reading.smoothed,
                    "mode" to level.reading.mode,
                    "timestampIso" to level.reading.capturedAt.toString(),
                ),
            ),
            datafill.retainState,
        ),
    )

    private companion object {
        /** Home Assistant's default availability payloads. */
        const val ONLINE = "online"
        const val OFFLINE = "offline"
    }
}
