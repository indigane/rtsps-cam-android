package app.p2scam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class P2sEndpointTest {
    @Test
    fun normalizesIpv4() {
        assertEquals("192.168.1.50", P2sEndpoint.normalizeHost("192.168.1.50"))
    }

    @Test
    fun normalizesPastedP2sUrl() {
        assertEquals(
            "192.168.1.50",
            P2sEndpoint.normalizeHost("rtsps://192.168.1.50:322/streaming/live/1"),
        )
    }

    @Test
    fun acceptsHostname() {
        assertEquals("p2s.local", P2sEndpoint.normalizeHost("p2s.local"))
    }

    @Test
    fun acceptsIpv6() {
        assertEquals("[fe80::1234]", P2sEndpoint.normalizeHost("fe80::1234"))
        assertEquals("[fe80::1234]", P2sEndpoint.normalizeHost("[fe80::1234]:322"))
    }

    @Test
    fun rejectsWrongPortAndCredentialsInHostField() {
        assertNull(P2sEndpoint.normalizeHost("192.168.1.50:554"))
        assertNull(P2sEndpoint.normalizeHost("bblp:secret@192.168.1.50"))
    }

    @Test
    fun buildsExpectedStreamUrl() {
        assertEquals(
            "rtsps://192.168.1.50:322/streaming/live/1",
            P2sEndpoint.streamUrl("192.168.1.50"),
        )
    }
}
