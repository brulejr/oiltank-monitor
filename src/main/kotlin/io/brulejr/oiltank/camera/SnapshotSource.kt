/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.oiltank.camera

/**
 * Where frames come from. [CameraService] checks what it returns, so an implementation
 * only has to produce bytes or throw [SnapshotException] saying why it could not.
 */
fun interface SnapshotSource {
    fun capture(): ByteArray
}
