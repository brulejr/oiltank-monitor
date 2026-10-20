/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.oiltank.camera

import java.time.Instant

/**
 * One frame captured from the camera, already checked to decode as an image.
 *
 * A plain class rather than a data class: equality over a [ByteArray] is identity, so a
 * generated `equals` would mislead.
 */
class Snapshot(val jpeg: ByteArray, val capturedAt: Instant)
