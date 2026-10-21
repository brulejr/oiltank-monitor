/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.oiltank.level.api

import io.brulejr.oiltank.level.LevelService
import io.brulejr.oiltank.level.TankLevel
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** How full the tank is. */
@RestController
@RequestMapping("/api/level")
class LevelController(private val service: LevelService) {

    /** The latest level, or 404 before the first reading. */
    @GetMapping
    fun level(): ResponseEntity<TankLevel> = service.latest()
        ?.let { ResponseEntity.ok(it) }
        ?: ResponseEntity.notFound().build()
}
