/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.oiltank.detection

/** A frame yielded no reading: it would not decode, or no float was found in it. */
class DetectionException(message: String) : RuntimeException(message)
