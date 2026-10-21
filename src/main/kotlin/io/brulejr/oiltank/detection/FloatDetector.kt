/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.oiltank.detection

import java.awt.image.BufferedImage

/** Finds the float in a frame, or returns null when there is none to find. */
fun interface FloatDetector {
    fun detect(image: BufferedImage): FloatBlob?
}
