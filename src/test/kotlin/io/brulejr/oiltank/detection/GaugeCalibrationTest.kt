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

class GaugeCalibrationTest {

    private val calibration = GaugeCalibration(
        listOf(GaugeMark(454, 1.0), GaugeMark(541, 0.75), GaugeMark(629, 0.5), GaugeMark(720, 0.25), GaugeMark(787, 0.0)),
    )

    @Test
    fun `a mark reads as its level`() {
        assertEquals(0.75, calibration.levelAt(541.0))
        assertEquals(0.5, calibration.levelAt(629.0))
    }

    @Test
    fun `between marks is interpolated within that segment`() {
        // Halfway between 3/4 (541) and 1/2 (629).
        assertEquals(0.625, calibration.levelAt(585.0), 1e-9)
    }

    @Test
    fun `beyond the end marks is clamped`() {
        assertEquals(1.0, calibration.levelAt(400.0))
        assertEquals(0.0, calibration.levelAt(900.0))
    }
}
