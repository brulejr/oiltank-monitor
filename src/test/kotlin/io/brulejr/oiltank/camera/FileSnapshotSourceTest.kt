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
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertEquals

class FileSnapshotSourceTest {

    @TempDir
    lateinit var directory: Path

    @Test
    fun `images are replayed in name order, cycling`() {
        directory.resolve("b.jpg").writeText("b")
        directory.resolve("a.JPEG").writeText("a")
        directory.resolve("notes.txt").writeText("ignored")
        val source = FileSnapshotSource(directory)

        assertEquals(listOf("a", "b", "a"), List(3) { source.capture().decodeToString() })
    }

    @Test
    fun `an empty directory is a failure`() {
        assertThrows<SnapshotException> { FileSnapshotSource(directory).capture() }
    }

    @Test
    fun `a missing directory is a failure`() {
        assertThrows<SnapshotException> { FileSnapshotSource(directory.resolve("missing")).capture() }
    }
}
