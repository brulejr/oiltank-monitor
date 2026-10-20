/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.oiltank.camera.api

import io.brulejr.oiltank.camera.CameraService
import io.brulejr.oiltank.camera.CameraStatus
import io.brulejr.oiltank.camera.SnapshotException
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * The latest frame, and captures on demand. Absent, like the rest of the camera feature,
 * when `oiltank.camera.enabled` is false.
 */
@RestController
@RequestMapping("/api/camera")
@ConditionalOnBooleanProperty("oiltank.camera.enabled", matchIfMissing = true)
class CameraController(private val service: CameraService) {

    @GetMapping
    fun status(): CameraStatus = service.status()

    /** The latest frame as a JPEG, or 404 before the first capture. */
    @GetMapping("/snapshot", produces = [MediaType.IMAGE_JPEG_VALUE])
    fun snapshot(): ResponseEntity<ByteArray> = service.latest()
        ?.let { ResponseEntity.ok().lastModified(it.capturedAt).body(it.jpeg) }
        ?: ResponseEntity.notFound().build()

    /** Captures a frame now and sends it down the pipeline, returning the camera's status after it. */
    @PostMapping("/snapshot")
    fun capture(): CameraStatus {
        service.captureNow()
        return service.status()
    }

    /** The camera, not this service, failed: 502, with the reason. */
    @ExceptionHandler(SnapshotException::class)
    fun captureFailed(e: SnapshotException): ResponseEntity<Map<String, String?>> =
        ResponseEntity.status(HttpStatus.BAD_GATEWAY).contentType(MediaType.APPLICATION_JSON).body(mapOf("message" to e.message))
}
