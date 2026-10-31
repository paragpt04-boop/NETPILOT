package org.jsus.netpilot

import org.junit.Assert.*
import org.junit.Test

class RouterProtocolTest {
    @Test fun routerOsNewEmptyReplyDoesNotBreakSession() {
        // Encoded reply: !empty, !done (empty query in RouterOS >= 7.18).
        val reply=byteArrayOf(6)+"!empty".toByteArray(Charsets.UTF_8)+byteArrayOf(0,5)+
            "!done".toByteArray(Charsets.UTF_8)+byteArrayOf(0)
        assertTrue(RouterOsReader.readTestReplies(reply).isEmpty())
    }
    @Test fun regularRecordStillParses() {
        val reply=byteArrayOf(3)+"!re".toByteArray(Charsets.UTF_8)+byteArrayOf(10)+
            "=name=JSUS".toByteArray(Charsets.UTF_8)+byteArrayOf(0,5)+
            "!done".toByteArray(Charsets.UTF_8)+byteArrayOf(0)
        assertEquals("JSUS",RouterOsReader.readTestReplies(reply).single()["name"])
    }
}
