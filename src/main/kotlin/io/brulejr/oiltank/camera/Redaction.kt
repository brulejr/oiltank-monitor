/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.oiltank.camera

import java.net.URI
import java.net.URISyntaxException

/**
 * [url] with any credentials replaced by three asterisks, so it can be logged or
 * reported: `user:secret@` in an RTSP URL becomes `***@`. (Written out in full, the
 * redacted URL would open a nested comment here.)
 * A URL that does not parse is withheld entirely, since where its credentials sit is unknown.
 */
fun redactCredentials(url: String): String = try {
    val uri = URI(url)
    if (uri.rawUserInfo == null) url else url.replaceFirst("${uri.rawUserInfo}@", "***@")
} catch (_: URISyntaxException) {
    "<unparseable url>"
}

/** [text] with every occurrence of [url] redacted, for output such as ffmpeg's that may echo it. */
fun redactUrlIn(text: String, url: String): String = text.replace(url, redactCredentials(url))
