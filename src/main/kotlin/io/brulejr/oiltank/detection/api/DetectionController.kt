/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.oiltank.detection.api

import io.brulejr.oiltank.detection.DetectionService
import io.brulejr.oiltank.detection.DetectionStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** The latest reading, and the image to calibrate against. */
@RestController
@RequestMapping("/api/detection")
class DetectionController(private val service: DetectionService) {

    @GetMapping
    fun status(): DetectionStatus = service.status()

    /** The latest frame with the calibration drawn on it, or 404 before the first; see [DetectionService.debugImage]. */
    @GetMapping("/debug", produces = [MediaType.IMAGE_JPEG_VALUE])
    fun debug(): ResponseEntity<ByteArray> = service.debugImage()
        ?.let { ResponseEntity.ok(it) }
        ?: ResponseEntity.notFound().build()
}
