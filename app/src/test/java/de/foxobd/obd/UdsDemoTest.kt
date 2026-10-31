package de.foxobd.obd

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spielt einen kompletten Ablauf gegen das Demo-Fahrzeug durch (inkl. Mehrfachrahmen). */
class UdsDemoTest {

    private fun ecu(addr: String) = VwEcus.all.first { it.address == addr }

    @Test fun scanEngineAndAirbag() = runBlocking {
        val elm = ElmClient(DemoTransport())
        elm.connectAndInit()
        assertTrue(elm.isCan)

        val uds = UdsClient(elm)
        uds.select(ecu("01"))
        assertEquals("04L906021DT", uds.readAscii(UdsClient.DID_PART_NUMBER))
        val engine = uds.readDtcs()
        assertEquals(listOf("P242F", "P0401"), engine.map { it.code })
        assertTrue(engine[0].active)

        uds.select(ecu("15"))
        val airbag = uds.readDtcs()
        assertEquals("B1001 28", airbag.single().display)

        uds.select(ecu("44"))   // im Demo nicht verbaut
        assertNull(uds.readAscii(UdsClient.DID_PART_NUMBER))

        uds.select(ecu("01"))
        assertTrue(uds.clearDtcs())
        assertTrue(uds.readDtcs().isEmpty())

        uds.reset()
        assertTrue(elm.read(Pids.SPEED) != null)
    }
}
