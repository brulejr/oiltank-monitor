/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.oiltank

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(
    properties = [
        // Replays a fixture image rather than reaching a camera.
        "oiltank.camera.source=file",
        "oiltank.camera.file.directory=src/test/resources/snapshots",
    ],
)
class OiltankMonitorApplicationTests {

    @Test
    fun contextLoads() {
    }
}
