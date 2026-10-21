/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.oiltank.detection

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.readBytes

/**
 * Real frames from the camera, with the calibration measured on each. All were taken
 * with the gauge reading 1/2: in the current camera position by day and by night
 * (infrared), and in an earlier position by day.
 */
object Fixtures {

    val currentFraming = Path.of("src/test/resources/gauge/2026-10-20-half.jpg")
    val currentFramingNight = Path.of("src/test/resources/gauge/2026-10-20-half-night.jpg")
    val oldFraming = Path.of("src/test/resources/gauge/old-framing/2025-11-15-half.jpg")

    /** The values in application.yml. */
    val currentDatafill = DetectionDatafill(
        roi = DetectionDatafill.Roi(905, 420, 130, 390),
        marks = DetectionDatafill.Marks(rows = listOf(454, 541, 629, 720, 787)),
        referenceOffset = 20,
    )

    /**
     * Measured on the November frame, before the camera was re-aimed. The camera looked up
     * more steeply then, showing more of the float's top, so its top edge sat further
     * above the reading line: 61 px rather than today's 20. Like the marks, the offset
     * belongs to the camera position.
     */
    val oldDatafill = DetectionDatafill(
        roi = DetectionDatafill.Roi(1190, 300, 150, 420),
        marks = DetectionDatafill.Marks(rows = listOf(340, 425, 517, 613, 690)),
        referenceOffset = 61,
    )

    fun bytes(path: Path): ByteArray = path.readBytes()

    fun image(path: Path): BufferedImage = ImageIO.read(path.toFile())

    /** [path]'s frame moved down by [rows] (up, if negative), as if the camera had tilted; the gap is filled grey. */
    fun shifted(path: Path, rows: Int): BufferedImage {
        val source = image(path)
        return BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_RGB).apply {
            createGraphics().run {
                color = Color.GRAY; fillRect(0, 0, width, height)
                drawImage(source, 0, rows, null)
                dispose()
            }
        }
    }

    fun jpeg(image: BufferedImage): ByteArray = ByteArrayOutputStream().use { out ->
        ImageIO.write(image, "jpg", out)
        out.toByteArray()
    }
}
