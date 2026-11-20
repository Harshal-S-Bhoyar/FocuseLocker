package com.focuslock.service.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DnsTtlParserTest {

    private fun nameBytes(vararg labels: String): ByteArray {
        val out = mutableListOf<Byte>()
        labels.forEach { l ->
            out.add(l.length.toByte())
            l.forEach { out.add(it.code.toByte()) }
        }
        out.add(0)
        return out.toByteArray()
    }

    private fun header(txid: Int = 0x1234, flags: Int = 0x8180, qd: Int = 1, an: Int = 1): ByteArray =
        byteArrayOf(
            (txid shr 8).toByte(), txid.toByte(),
            (flags shr 8).toByte(), flags.toByte(),
            0, qd.toByte(), 0, an.toByte(), 0, 0, 0, 0
        )

    private fun answer(ttl: Long, type: Int = 1, rdata: ByteArray = byteArrayOf(1, 2, 3, 4)): ByteArray {
        val head = byteArrayOf(
            0xC0.toByte(), 0x0C,
            (type shr 8).toByte(), type.toByte(), 0, 1,
            (ttl shr 24).toByte(), (ttl shr 16).toByte(), (ttl shr 8).toByte(), ttl.toByte(),
            0, rdata.size.toByte()
        )
        return head + rdata
    }

    private fun query(qtype: Int = 1): ByteArray {
        val q = nameBytes("example", "com") + byteArrayOf(0, qtype.toByte(), 0, 1)
        return header(an = 0) + q
    }

    private fun response(vararg answers: ByteArray): ByteArray {
        val q = nameBytes("example", "com") + byteArrayOf(0, 1, 0, 1)
        val h = header(an = answers.size)
        var out = h + q
        answers.forEach { out += it }
        return out
    }

    @Test
    fun `single answer returns its ttl`() {
        val msg = response(answer(300))
        assertEquals(300L, DnsTtlParser.minAnswerTtl(msg, 0, msg.size))
    }

    @Test
    fun `multi answer returns minimum ttl`() {
        val msg = response(answer(300), answer(60), answer(120))
        assertEquals(60L, DnsTtlParser.minAnswerTtl(msg, 0, msg.size))
    }

    @Test
    fun `cname chain returns minimum across types`() {
        val cname = answer(100, type = 5, rdata = byteArrayOf(3, 0x61, 0x62, 0x63, 0))
        val msg = response(cname, answer(200))
        assertEquals(100L, DnsTtlParser.minAnswerTtl(msg, 0, msg.size))
    }

    @Test
    fun `zero answers returns null`() {
        val msg = response()
        assertNull(DnsTtlParser.minAnswerTtl(msg, 0, msg.size))
    }

    @Test
    fun `truncated message returns null`() {
        val msg = response(answer(300))
        assertNull(DnsTtlParser.minAnswerTtl(msg, 0, 20))
        assertNull(DnsTtlParser.minAnswerTtl(msg, 0, msg.size - 3))
    }

    @Test
    fun `zero ttl passes through for clamp layer`() {
        val msg = response(answer(0))
        assertEquals(0L, DnsTtlParser.minAnswerTtl(msg, 0, msg.size))
    }

    @Test
    fun `query type extracted for A and AAAA`() {
        val a = query(1)
        assertEquals(1, DnsTtlParser.queryType(a, 0, a.size))
        val aaaa = query(28)
        assertEquals(28, DnsTtlParser.queryType(aaaa, 0, aaaa.size))
    }

    @Test
    fun `query type null on malformed`() {
        assertNull(DnsTtlParser.queryType(byteArrayOf(1, 2, 3), 0, 3))
    }

    @Test
    fun `offset respected`() {
        val msg = response(answer(90))
        val padded = byteArrayOf(9, 9, 9, 9) + msg
        assertEquals(90L, DnsTtlParser.minAnswerTtl(padded, 4, msg.size))
    }
}
