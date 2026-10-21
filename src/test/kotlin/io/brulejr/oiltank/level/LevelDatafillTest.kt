/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.oiltank.level

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class LevelDatafillTest {

    @Test
    fun `thresholds are percentages`() {
        assertThrows<IllegalArgumentException> { LevelDatafill(lowPercent = -1) }
        assertThrows<IllegalArgumentException> { LevelDatafill(highPercent = 101) }
    }

    @Test
    fun `low is below high`() {
        assertThrows<IllegalArgumentException> { LevelDatafill(lowPercent = 50, highPercent = 50) }
    }
}
