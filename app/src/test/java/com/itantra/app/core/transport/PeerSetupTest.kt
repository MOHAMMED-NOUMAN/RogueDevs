package com.itantra.app.core.transport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PeerSetupTest {

    @Test
    fun `both phones derive the same valid Wi-Fi Direct group from the pairing code`() {
        val code = "4827".encodeToByteArray()
        val host = WifiDirectPeer.fromPairingCode(code, PeerRole.LISTEN)
        val joiner = WifiDirectPeer.fromPairingCode(code, PeerRole.DIAL)

        assertEquals(host.networkName, joiner.networkName)
        assertEquals(host.passphrase, joiner.passphrase)
        // Android only accepts group names "DIRECT-xy..." and WPA2 passphrases of 8..63 chars.
        assertTrue(host.networkName, Regex("DIRECT-[0-9a-f]{2}-.+").matches(host.networkName))
        assertTrue(host.networkName.length <= 32)
        assertTrue(host.passphrase.length in 8..63)
    }

    @Test
    fun `different pairing codes give different groups`() {
        val a = WifiDirectPeer.fromPairingCode("1111".encodeToByteArray(), PeerRole.LISTEN)
        val b = WifiDirectPeer.fromPairingCode("2222".encodeToByteArray(), PeerRole.LISTEN)

        assertNotEquals(a.networkName, b.networkName)
        assertNotEquals(a.passphrase, b.passphrase)
    }

    @Test
    fun `placeholder SOS payload has the beacon size`() {
        assertEquals(SOS_BEACON_PAYLOAD_SIZE, placeholderSosPayload(senderId = 7, sequence = 1).size)
    }
}
