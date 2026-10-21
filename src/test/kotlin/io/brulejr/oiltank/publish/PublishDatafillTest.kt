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
import org.junit.jupiter.api.assertThrows
import java.time.Duration

class PublishDatafillTest {

    @Test
    fun `qos is 0, 1 or 2`() {
        assertThrows<IllegalArgumentException> { PublishDatafill.Mqtt(qos = 3) }
    }

    @Test
    fun `the sensor id is a Home Assistant object id`() {
        assertThrows<IllegalArgumentException> { PublishDatafill.HomeAssistant(sensorId = "Oil Tank") }
    }

    @Test
    fun `the maximum backoff is not shorter than the initial one`() {
        assertThrows<IllegalArgumentException> { PublishDatafill.Retry(Duration.ofSeconds(10), Duration.ofSeconds(5)) }
    }
}
