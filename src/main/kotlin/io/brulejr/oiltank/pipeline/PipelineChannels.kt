/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.oiltank.pipeline

/**
 * Names of the channels that connect the pipeline stages.
 *
 * ```
 * [camera poller] ─┐
 * [capture API]   ─┴─> snapshot ──> detection ──> floatReading ──> (level, not yet built)
 *                                       │
 *                          (filtered by hysteresis)
 *
 * [any stage failure] ──> pipelineErrors ──> logged
 * ```
 *
 * Level and publish join the chain as they are built; see docs/DESIGN.md.
 */
object PipelineChannels {
    const val SNAPSHOT = "snapshotChannel"
    const val FLOAT_READING = "floatReadingChannel"
    const val PIPELINE_ERRORS = "pipelineErrorChannel"
}
