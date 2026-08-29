package app.p2scam

internal object P2sEndpoint {
    const val USERNAME = "bblp"
    const val PORT = 322
    const val PATH = "/streaming/live/1"

    /**
     * Accepts a plain hostname/IP, or a pasted RTSP(S) URL, and returns only
     * the host portion suitable for the P2S stream URL.
     */
    fun normalizeHost(raw: String): String? {
        var value = raw.trim()
        if (value.isEmpty()) return null

        value = value
            .removePrefixIgnoreCase("rtsps://")
            .removePrefixIgnoreCase("rtsp://")
            .substringBefore('/')
            .substringBefore('?')
            .trim()

        if (value.isEmpty() || value.any(Char::isWhitespace) || '@' in value) return null

        // Bracketed IPv6, optionally with :322.
        if (value.startsWith("[")) {
            val close = value.indexOf(']')
            if (close <= 1) return null
            val host = value.substring(0, close + 1)
            val tail = value.substring(close + 1)
            return when {
                tail.isEmpty() -> host
                tail == ":$PORT" -> host
                else -> null
            }
        }

        val colonCount = value.count { it == ':' }
        if (colonCount > 1) {
            // Bare IPv6 literal. Uri.parse() needs brackets around it.
            return "[$value]"
        }

        if (colonCount == 1) {
            val host = value.substringBeforeLast(':')
            val port = value.substringAfterLast(':').toIntOrNull() ?: return null
            if (host.isEmpty() || port != PORT) return null
            value = host
        }

        if (!value.matches(Regex("[A-Za-z0-9._-]+"))) return null
        return value
    }

    fun streamUrl(host: String): String = "rtsps://$host:$PORT$PATH"

    private fun String.removePrefixIgnoreCase(prefix: String): String =
        if (startsWith(prefix, ignoreCase = true)) substring(prefix.length) else this
}
