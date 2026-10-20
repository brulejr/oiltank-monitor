/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.oiltank.camera

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.readBytes

/**
 * Replays the JPEG images in [directory], one per capture, in file-name order and
 * cycling back to the first. Lets the pipeline run, and be tested, without a camera.
 *
 * The directory is listed on every capture, so images can be added while it runs.
 */
class FileSnapshotSource(private val directory: Path) : SnapshotSource {

    private val next = AtomicInteger()

    override fun capture(): ByteArray {
        val images = images()
        if (images.isEmpty()) throw SnapshotException("no .jpg or .jpeg images in $directory")
        return images[Math.floorMod(next.getAndIncrement(), images.size)].readBytes()
    }

    private fun images(): List<Path> {
        if (!Files.isDirectory(directory)) throw SnapshotException("$directory is not a directory")
        return Files.list(directory).use { paths ->
            paths.filter { it.isRegularFile() && it.extension.lowercase() in setOf("jpg", "jpeg") }.sorted().toList()
        }
    }
}
