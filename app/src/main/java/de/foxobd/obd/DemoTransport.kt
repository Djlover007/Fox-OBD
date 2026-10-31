package de.foxobd.obd

import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Simuliertes Auto mit ELM327 – zum Testen der App ohne Adapter.
 * Antwortet wie ein echter Adapter mit ATE0/ATS0/ATH0 auf CAN (ISO 15765-4).
 */
class DemoTransport : ObdTransport {
    override val name = "Demo-Fahrzeug"

    private val start = System.currentTimeMillis()
    private var dtcs = mutableListOf("0301", "0420")   // P0301, P0420
    private var pending = mutableListOf("0171")         // P0171

    private var header = "7DF"

    /** Simulierte VW-Steuergeräte (Caddy 4, 2.0 TDI): Teilenummer, Systemname, Fehler. */
    private class DemoEcu(val part: String, val system: String, val dtcs: MutableList<String>)

    private val ecus = mutableMapOf(
        "7E0" to DemoEcu("04L906021DT", "R4 2.0l TDI", mutableListOf("242F00" + "09", "040100" + "08")),
        "7E1" to DemoEcu("0D9300012A", "DQ250-6F", mutableListOf()),
        "713" to DemoEcu("2K0907379BH", "ESP MK100", mutableListOf()),
        "70E" to DemoEcu("5Q0937084AH", "BCM MQBA0", mutableListOf("903300" + "08")),
        "715" to DemoEcu("2K0959655B", "AirbagVW10", mutableListOf("900128" + "2F")),
        "714" to DemoEcu("2K0920871A", "KOMBI", mutableListOf()),
        "710" to DemoEcu("2K0907530AE", "GW-Caddy", mutableListOf()),
    )

    override suspend fun connect() { delay(600) }

    override suspend fun send(command: String, timeoutMs: Long): String {
        delay(40)
        val t = (System.currentTimeMillis() - start) / 1000.0
        val wave = (sin(t / 3) + 1) / 2                  // 0..1
        val cmd = command.uppercase().replace(" ", "")
        val body = when {
            cmd.startsWith("ATSH") -> { header = cmd.removePrefix("ATSH"); "OK" }
            header != "7DF" && !cmd.startsWith("AT") -> uds(cmd)
            cmd == "ATZ" -> "\r\rELM327 v1.5"
            cmd == "ATI" -> "ELM327 v1.5"
            cmd == "ATRV" -> "12.%dV".format(4 + (wave * 3).roundToInt())
            cmd == "ATDP" -> "AUTO, ISO 15765-4 (CAN 11/500)"
            cmd == "ATDPN" -> "A6"
            cmd.startsWith("AT") -> "OK"
            cmd == "0100" -> "SEARCHING...\r4100BE3FA813"
            cmd == "010C" -> pid("0C", (800 + wave * 2600).roundToInt() * 4, 2)
            cmd == "010D" -> pid("0D", (wave * 110).roundToInt(), 1)
            cmd == "0105" -> pid("05", minOf(90, 20 + t.toInt()) + 40, 1)
            cmd == "010F" -> pid("0F", 25 + 40, 1)
            cmd == "0104" -> pid("04", (20 + wave * 60).roundToInt() * 255 / 100, 1)
            cmd == "0111" -> pid("11", (10 + wave * 50).roundToInt() * 255 / 100, 1)
            cmd == "010B" -> pid("0B", (35 + wave * 120).roundToInt(), 1)
            cmd == "0110" -> pid("10", (300 + wave * 4000).roundToInt(), 2)
            cmd == "012F" -> pid("2F", 160, 1)
            cmd == "0142" -> pid("42", 14100 + (wave * 200).roundToInt(), 2)
            cmd == "015C" -> pid("5C", minOf(95, 15 + t.toInt()) + 40, 1)
            cmd == "0123" -> pid("23", (3000 + wave * 12000).roundToInt(), 2)
            cmd == "015E" -> pid("5E", (16 + wave * 120).roundToInt(), 2)
            cmd == "0133" -> pid("33", 99, 1)
            cmd == "017A" -> "417A01" + "%04X".format((wave * 1500 + 300).roundToInt())
            cmd == "017C" -> "417C01" + "%04X".format(((250 + wave * 300 + 40) * 10).roundToInt())
            cmd == "0131" -> pid("31", 1234, 2)
            cmd == "0902" -> "014\r0:490201575657\r1:5A5A5A314B5A41\r2:4D303030303031"
            cmd == "03" -> dtcResponse("43", dtcs)
            cmd == "07" -> dtcResponse("47", pending)
            cmd == "0A" -> dtcResponse("4A", emptyList())
            cmd == "04" -> { dtcs.clear(); pending.clear(); "44" }
            else -> "NO DATA"
        }
        return body + "\r\r"
    }

    private fun uds(cmd: String): String {
        val ecu = ecus[header] ?: return "NO DATA"
        return when {
            cmd == "22F187" -> frames("62F187" + ascii(ecu.part))
            cmd == "22F197" -> frames("62F197" + ascii(ecu.system))
            cmd == "22F189" -> frames("62F189" + ascii("0003"))
            cmd == "1902FF" -> frames("5902FF" + ecu.dtcs.joinToString(""))
            cmd == "14FFFFFF" -> { ecu.dtcs.clear(); "54" }
            cmd.startsWith("22") -> "7F2231"   // DID unbekannt
            else -> "7F" + cmd.take(2) + "11"
        }
    }

    private fun ascii(text: String) = text.map { "%02X".format(it.code) }.joinToString("")

    /** Formatiert eine Antwort wie der ELM327 bei ATS0/ATH0, ggf. als ISO-TP-Mehrfachrahmen. */
    private fun frames(hex: String): String {
        val len = hex.length / 2
        if (len <= 7) return hex
        val sb = StringBuilder("%03X".format(len))
        var pos = 0
        var index = 0
        while (pos < hex.length) {
            val take = if (index == 0) 12 else 14
            sb.append("\r").append(Integer.toHexString(index % 16).uppercase()).append(':').append(hex.substring(pos, minOf(hex.length, pos + take)))
            pos += take
            index++
        }
        return sb.toString()
    }

    private fun pid(code: String, value: Int, bytes: Int) =
        "41$code" + if (bytes == 1) "%02X".format(value and 0xFF) else "%04X".format(value and 0xFFFF)

    private fun dtcResponse(prefix: String, codes: List<String>) =
        prefix + "%02X".format(codes.size) + codes.joinToString("")

    override fun close() {}
}
