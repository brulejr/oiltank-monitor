/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.oiltank.detection

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class DayNightFloatDetectorTest {

    private val roi = Fixtures.currentDatafill.roi
    private val detector = DayNightFloatDetector(
        roi,
        color = { FloatBlob(0, 0, 0, 0, 1, DetectionMode.COLOR) },
        infrared = { FloatBlob(0, 0, 0, 0, 1, DetectionMode.INFRARED) },
    )

    @Test
    fun `a daylight frame goes to the colour detector`() {
        assertEquals(DetectionMode.COLOR, detector.detect(Fixtures.image(Fixtures.currentFraming))?.mode)
    }

    @Test
    fun `a dim colour frame at dawn is still colour`() {
        assertEquals(DetectionMode.COLOR, detector.detect(Fixtures.image(Fixtures.currentFramingDawn))?.mode)
    }

    @Test
    fun `an infrared frame goes to the infrared detector`() {
        assertEquals(DetectionMode.INFRARED, detector.detect(Fixtures.image(Fixtures.currentFramingNight))?.mode)
    }
}
