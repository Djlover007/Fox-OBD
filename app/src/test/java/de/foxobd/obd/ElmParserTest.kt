package de.foxobd.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ElmParserTest {

    @Test fun rpm() {
        assertEquals(1726.0, ElmParser.parsePid("410C1AF8\r\r", Pids.RPM)!!, 0.01)
    }

    @Test fun rpmWithSpacesAndSearching() {
        assertEquals(1726.0, ElmParser.parsePid("SEARCHING...\r41 0C 1A F8 \r", Pids.RPM)!!, 0.01)
    }

    @Test fun coolant() {
        assertEquals(50.0, ElmParser.parsePid("41055A", Pids.COOLANT)!!, 0.01)
    }

    @Test fun dieselPids() {
        assertEquals(1500.0, ElmParser.parsePid("41233A98", Pids.RAIL_PRESSURE)!!, 0.01)
        assertEquals(120.0, ElmParser.parsePid("417A0104B0", Pids.DPF_DELTA)!!, 0.01)
        assertEquals(-20.0, ElmParser.parsePid("417A01FF38", Pids.DPF_DELTA)!!, 0.01)
    }

    @Test fun noData() {
        assertNull(ElmParser.parsePid("NO DATA\r", Pids.RPM))
        assertNotNull(ElmParser.error("NO DATA\r"))
    }

    @Test fun dtcDecode() {
        assertEquals("P0301", Dtc.decode(0x03, 0x01))
        assertEquals("C0035", Dtc.decode(0x40, 0x35))
        assertEquals("B1234", Dtc.decode(0x92, 0x34))
        assertEquals("U0100", Dtc.decode(0xC1, 0x00))
    }

    @Test fun dtcsCan() {
        val dtcs = ElmParser.parseDtcs("430203010420\r", "03", isCan = true, kind = Dtc.Kind.STORED)
        assertEquals(listOf("P0301", "P0420"), dtcs.map { it.code })
    }

    @Test fun dtcsCanNone() {
        assertTrue(ElmParser.parseDtcs("4300\r", "03", isCan = true, kind = Dtc.Kind.STORED).isEmpty())
    }

    @Test fun dtcsKLinePadded() {
        val dtcs = ElmParser.parseDtcs("4301330000000000\r", "03", isCan = false, kind = Dtc.Kind.STORED)
        assertEquals(listOf("P0133"), dtcs.map { it.code })
    }

    @Test fun dtcsCanMultiFrame() {
        val raw = "00C\r0:43050301042001\r1:71030004330000\r"
        val dtcs = ElmParser.parseDtcs(raw, "03", isCan = true, kind = Dtc.Kind.STORED)
        assertEquals(listOf("P0301", "P0420", "P0171", "P0300", "P0433"), dtcs.map { it.code })
    }

    @Test fun vinCan() {
        val raw = "014\r0:490201575657\r1:5A5A5A314B5A41\r2:4D303030303031\r"
        assertEquals("WVWZZZ1KZAM000001", ElmParser.parseVin(raw))
    }

    @Test fun canDetection() {
        assertTrue(ElmParser.isCanProtocol("A6\r"))
        assertTrue(ElmParser.isCanProtocol("6"))
        assertTrue(!ElmParser.isCanProtocol("A3"))
    }
}
