package com.focuslock.service.vpn

/**
 * Bounds-checked reader for raw DNS messages (query or response).
 * Returns null on any malformed input; callers fall back to uncached behavior.
 */
object DnsTtlParser {

    private const val HEADER_LEN = 12

    /** Query type (e.g. 1 = A, 28 = AAAA) of the first question, or null. */
    fun queryType(message: ByteArray, offset: Int, length: Int): Int? {
        if (length < HEADER_LEN + 5) return null
        val end = offset + length
        val qdCount = u16(message, offset + 4) ?: return null
        if (qdCount == 0) return null
        var pos = offset + HEADER_LEN
        repeat(qdCount) {
            pos = skipName(message, pos, end) ?: return null
            if (pos + 4 > end) return null
            val qtype = u16(message, pos) ?: return null
            pos += 4
            if (it == 0) return qtype
        }
        return null
    }

    /** Minimum TTL in seconds across all answer RRs, or null when absent/unparseable. */
    fun minAnswerTtl(message: ByteArray, offset: Int, length: Int): Long? {
        if (length < HEADER_LEN) return null
        val end = offset + length
        val qdCount = u16(message, offset + 4) ?: return null
        val anCount = u16(message, offset + 6) ?: return null
        if (anCount == 0) return null
        var pos = offset + HEADER_LEN
        repeat(qdCount) {
            pos = skipName(message, pos, end) ?: return null
            if (pos + 4 > end) return null
            pos += 4
        }
        var minTtl: Long? = null
        repeat(anCount) {
            pos = skipName(message, pos, end) ?: return null
            if (pos + 10 > end) return null
            val ttl = u32(message, pos + 4) ?: return null
            val rdLen = u16(message, pos + 8) ?: return null
            pos += 10
            if (pos + rdLen > end) return null
            pos += rdLen
            minTtl = if (minTtl == null) ttl else minOf(minTtl!!, ttl)
        }
        return minTtl
    }

    /**
     * Copy of the message slice with every answer RR TTL capped at [capSeconds].
     * Used when serving a cached response: answers go out with their remaining
     * lifetime, never the stale original. Null on malformed input.
     */
    fun withCappedAnswerTtls(message: ByteArray, offset: Int, length: Int, capSeconds: Long): ByteArray? {
        if (length < HEADER_LEN) return null
        val end = offset + length
        val qdCount = u16(message, offset + 4) ?: return null
        val anCount = u16(message, offset + 6) ?: return null
        if (anCount == 0) return message.copyOfRange(offset, end)
        var pos = offset + HEADER_LEN
        repeat(qdCount) {
            pos = skipName(message, pos, end) ?: return null
            if (pos + 4 > end) return null
            pos += 4
        }
        // Answer RR layout from pos: TYPE(2) CLASS(2) TTL(4) RDLENGTH(2) RDATA.
        // After the two advances below, pos is the record end, so the TTL field
        // starts 6 bytes before (record end) minus rdLen: TTL(4) + RDLENGTH(2).
        val out = message.copyOf()
        repeat(anCount) {
            pos = skipName(message, pos, end) ?: return null
            if (pos + 10 > end) return null
            val ttl = u32(message, pos + 4) ?: return null
            val rdLen = u16(message, pos + 8) ?: return null
            pos += 10
            if (pos + rdLen > end) return null
            pos += rdLen
            val ttlAt = pos - rdLen - 6
            val capped = minOf(ttl, capSeconds)
            out[ttlAt] = ((capped shr 24) and 0xFF).toByte()
            out[ttlAt + 1] = ((capped shr 16) and 0xFF).toByte()
            out[ttlAt + 2] = ((capped shr 8) and 0xFF).toByte()
            out[ttlAt + 3] = (capped and 0xFF).toByte()
        }
        return out.copyOfRange(offset, end)
    }

    private fun u16(b: ByteArray, at: Int): Int? {
        if (at < 0 || at + 2 > b.size) return null
        return ((b[at].toInt() and 0xFF) shl 8) or (b[at + 1].toInt() and 0xFF)
    }

    private fun u32(b: ByteArray, at: Int): Long? {
        if (at < 0 || at + 4 > b.size) return null
        return ((b[at].toLong() and 0xFF) shl 24) or
            ((b[at + 1].toLong() and 0xFF) shl 16) or
            ((b[at + 2].toLong() and 0xFF) shl 8) or
            (b[at + 3].toLong() and 0xFF)
    }

    /** Skips a possibly-compressed domain name; returns position after it, or null. */
    private fun skipName(b: ByteArray, pos: Int, end: Int): Int? {
        var p = pos
        var jumps = 0
        while (true) {
            if (p >= end || p >= b.size) return null
            val len = b[p].toInt() and 0xFF
            if (len == 0) return p + 1
            if (len and 0xC0 == 0xC0) {
                if (p + 2 > end) return null
                return p + 2
            }
            if (jumps++ > 128) return null
            p += 1 + len
        }
    }
}
