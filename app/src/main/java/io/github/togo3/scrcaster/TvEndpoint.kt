package io.github.togo3.scrcaster

import io.github.togo3.scrcaster.connection.ConnectionEndpoint

/** Compatibility entry point for callers of the original TV address parser. */
internal fun parseTvEndpoint(text: String): Pair<String, Int>? =
    ConnectionEndpoint.parse(text)?.let { it.host to it.port }
