/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.oiltank.camera

/** A frame could not be captured, or what was captured is not an image. */
class SnapshotException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
