/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.oiltank.detection

import io.brulejr.oiltank.camera.Snapshot
import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import java.awt.BasicStroke
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.time.Duration
import java.time.Instant
import javax.imageio.ImageIO
import kotlin.math.abs

/**
 * Stage 2: finds the float in a snapshot, finds the gauge's marks around it, reads the
 * gauge where the float sits, and smooths the readings across frames. Keeps the latest
 * frame and what was found in it, found or not, so the debug image shows why a frame
 * failed as well as where a reading came from.
 *
 * Also compares the readings either side of each switch between the camera's day and
 * night modes, which should agree, as a running check on the calibration.
 *
 * Synchronized: a capture through the API can overlap a scheduled one, and the
 * moving average and hysteresis state must see one frame at a time.
 */
class DetectionService(
    private val detector: FloatDetector,
    private val tracker: MarkTracker,
    private val datafill: DetectionDatafill,
    meterRegistry: MeterRegistry,
) {

    private val detectionTime = Timer.builder("oiltank.float.detection.time").register(meterRegistry)
    private val rawLevel = DistributionSummary.builder("oiltank.float.level.raw").register(meterRegistry)
    private val smoothedLevel = DistributionSummary.builder("oiltank.float.level.smoothed").register(meterRegistry)
    private val modeDifference = DistributionSummary.builder("oiltank.float.mode.difference").register(meterRegistry)
    private val missCounter = meterRegistry.counter("oiltank.float.detection.misses")

    private var detections = 0L
    private var misses = 0L
    private var passed = 0L
    private var lastAttemptAt: Instant? = null
    private var lastReading: FloatReading? = null
    private var lastPassed: FloatReading? = null
    private var lastAlignment: MarkAlignment? = null
    private var lastModeComparison: ModeComparison? = null
    private var lastFailure: String? = null
    private var lastImage: BufferedImage? = null
    private var lastBlob: FloatBlob? = null

    /** The reading in [snapshot]. Throws [DetectionException] when there is none, after recording why. */
    @Synchronized
    fun process(snapshot: Snapshot): FloatReading {
        lastAttemptAt = snapshot.capturedAt
        lastBlob = null
        lastAlignment = null
        try {
            val image = decode(snapshot.jpeg).also { lastImage = it }
            val (blob, alignment) = detectionTime.recordCallable {
                val blob = detector.detect(image) ?: throw DetectionException("no float found in the region of interest")
                lastBlob = blob
                blob to tracker.align(image, blob)
            }!!
            lastAlignment = alignment
            val floatY = blob.top + datafill.referenceOffset
            val raw = GaugeCalibration(alignment.marks).levelAt(floatY.toDouble())
            val smoothed = lastReading?.let { datafill.smoothingAlpha * raw + (1 - datafill.smoothingAlpha) * it.smoothed } ?: raw
            val reading = FloatReading(floatY, raw, smoothed, blob.mode, snapshot.capturedAt)
            compareModes(reading)
            rawLevel.record(raw)
            smoothedLevel.record(smoothed)
            lastReading = reading
            lastFailure = null
            detections++
            return reading
        } catch (e: DetectionException) {
            misses++
            missCounter.increment()
            lastFailure = e.message
            throw e
        }
    }

    /**
     * True when [reading] has moved far enough from the last reading passed on to be
     * worth passing on itself. The first always is.
     */
    @Synchronized
    fun shouldPass(reading: FloatReading): Boolean {
        val previous = lastPassed
        if (previous != null && abs(reading.smoothed - previous.smoothed) < datafill.hysteresisDelta) return false
        lastPassed = reading
        passed++
        return true
    }

    @Synchronized
    fun status() = DetectionStatus(
        detections, misses, passed, lastAttemptAt, lastReading, lastAlignment, lastModeComparison, lastFailure,
    )

    /**
     * The latest frame as a JPEG, marked up for calibration, or null before the first:
     * the region of interest (cyan), the dark bands found (orange ticks at its edges), the
     * marks used (magenta), the float found (green) and the row read (red). Lines only,
     * no text, since text rendering needs fonts a slim container image may not have.
     */
    @Synchronized
    fun debugImage(): ByteArray? {
        val frame = lastImage ?: return null
        val image = BufferedImage(frame.width, frame.height, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        try {
            g.drawImage(frame, 0, 0, null)
            g.stroke = BasicStroke(2f)
            val roi = datafill.roi
            g.color = Color.CYAN
            g.drawRect(roi.x, roi.y, roi.width, roi.height)
            lastAlignment?.let { alignment ->
                g.color = Color.ORANGE
                alignment.bands.forEach {
                    g.drawLine(roi.x - 30, it, roi.x - 5, it)
                    g.drawLine(roi.x + roi.width + 5, it, roi.x + roi.width + 30, it)
                }
                g.color = Color.MAGENTA
                alignment.marks.forEach { g.drawLine(roi.x - 20, it.y, roi.x + roi.width + 20, it.y) }
            }
            lastBlob?.let { blob ->
                g.color = Color.GREEN
                g.drawRect(blob.left, blob.top, blob.right - blob.left, blob.bottom - blob.top)
                g.color = Color.RED
                val y = blob.top + datafill.referenceOffset
                g.drawLine(roi.x - 40, y, roi.x + roi.width + 40, y)
            }
        } finally {
            g.dispose()
        }
        return ByteArrayOutputStream().use { out ->
            ImageIO.write(image, "jpg", out)
            out.toByteArray()
        }
    }

    /** Records a day/night pair when [reading] follows a reading in the other mode closely enough. */
    private fun compareModes(reading: FloatReading) {
        val previous = lastReading ?: return
        if (previous.mode == reading.mode) return
        if (Duration.between(previous.capturedAt, reading.capturedAt) > datafill.crossCheck.window) return
        val (color, infrared) = if (reading.mode == DetectionMode.COLOR) reading to previous else previous to reading
        val difference = abs(color.raw - infrared.raw)
        modeDifference.record(difference)
        lastModeComparison = ModeComparison(
            colorLevel = color.raw,
            infraredLevel = infrared.raw,
            difference = difference,
            withinTolerance = difference <= datafill.crossCheck.tolerance,
            at = reading.capturedAt,
        )
    }

    private fun decode(jpeg: ByteArray): BufferedImage = try {
        ImageIO.read(ByteArrayInputStream(jpeg))
    } catch (_: IOException) {
        null
    } ?: throw DetectionException("snapshot does not decode as an image")
}
