/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.oiltank.camera

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class RedactionTest {

    @Test
    fun `credentials are replaced`() {
        assertEquals("rtsp://***@cam:554/stream1", redactCredentials("rtsp://user:s3cret@cam:554/stream1"))
    }

    @Test
    fun `a URL without credentials is unchanged`() {
        assertEquals("rtsp://cam:554/stream1", redactCredentials("rtsp://cam:554/stream1"))
    }

    @Test
    fun `a URL that does not parse is withheld`() {
        assertEquals("<unparseable url>", redactCredentials("rtsp://user:pa ss@cam/stream"))
    }

    @Test
    fun `every occurrence in text is redacted`() {
        val url = "rtsp://user:s3cret@cam/stream1"

        assertEquals(
            "rtsp://***@cam/stream1: Connection refused (rtsp://***@cam/stream1)",
            redactUrlIn("$url: Connection refused ($url)", url),
        )
    }
}
