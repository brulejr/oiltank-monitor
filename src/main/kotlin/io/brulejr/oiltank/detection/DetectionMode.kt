/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.oiltank.detection

/** How the float was found, which follows from the camera's day or night mode. */
enum class DetectionMode {
    /** A colour frame: the float is the yellow blob. */
    COLOR,

    /** An infrared frame, which has no colour: the float is the widest bright band. */
    INFRARED,
}
