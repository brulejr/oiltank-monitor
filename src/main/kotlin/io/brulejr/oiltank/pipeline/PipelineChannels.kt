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
 * [capture API]   ─┴─> snapshot ──> (detection, not yet built)
 *
 * [any stage failure] ──> pipelineErrors ──> logged
 * ```
 *
 * Detection, level and publish join the chain as they are built; see docs/DESIGN.md.
 */
object PipelineChannels {
    const val SNAPSHOT = "snapshotChannel"
    const val PIPELINE_ERRORS = "pipelineErrorChannel"
}
