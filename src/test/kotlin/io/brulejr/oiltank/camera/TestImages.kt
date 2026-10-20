/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.oiltank.camera

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/** A small, valid JPEG for tests that need a frame but not a particular picture. */
fun testJpeg(): ByteArray = ByteArrayOutputStream().use { out ->
    ImageIO.write(BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB), "jpg", out)
    out.toByteArray()
}
