package de.foxobd.obd

import kotlinx.coroutines.delay

/**
 * Steuergerät im VW-Konzern (UDS über CAN, ISO 15765).
 * [tx] = Anfrage-ID, [rx] = Antwort-ID. Die IDs gelten für die meisten VW-Modelle ab ca. 2012
 * (u. a. Caddy 4, Golf 7, Passat B8). Einzelne Fahrzeuge können abweichen.
 */
data class Ecu(val address: String, val name: String, val tx: String, val rx: String)

object VwEcus {
    val all = listOf(
        Ecu("01", "Motorelektronik", "7E0", "7E8"),
        Ecu("02", "Getriebeelektronik", "7E1", "7E9"),
        Ecu("03", "Bremsenelektronik (ABS/ESP)", "713", "77D"),
        Ecu("08", "Klimaanlage", "746", "7B0"),
        Ecu("09", "Bordnetzsteuergerät", "70E", "778"),
        Ecu("15", "Airbag", "715", "77F"),
        Ecu("16", "Lenksäulenelektronik", "70C", "776"),
        Ecu("17", "Kombiinstrument", "714", "77E"),
        Ecu("19", "Diagnose-Interface (Gateway)", "710", "77A"),
        Ecu("44", "Lenkhilfe", "712", "77C"),
        Ecu("5F", "Infotainment / Radio", "773", "7DD"),
    )
}

/** Fehlereintrag aus UDS-Service 0x19: 2 Byte Code + 1 Byte Fehlerart + Statusbyte. */
data class UdsDtc(val code: String, val failureType: Int, val status: Int) {
    val active: Boolean get() = status and 0x01 != 0          // testFailed
    val confirmed: Boolean get() = status and 0x08 != 0       // confirmedDTC
    val display: String get() = "%s %02X".format(code, failureType)
    val description: String get() = DtcDescriptions.lookup(code)
    val statusLabel: String
        get() = when {
            active -> "aktiv"
            confirmed -> "gespeichert"
            else -> "sporadisch"
        }
}

class UdsNegativeResponse(val service: Int, val nrc: Int) :
    ObdException("Steuergerät lehnt ab: Service %02X, Code %02X (%s)".format(service, nrc, nrcText(nrc)))

private fun nrcText(nrc: Int) = when (nrc) {
    0x10 -> "allgemein abgelehnt"
    0x11 -> "Service nicht unterstützt"
    0x12 -> "Unterfunktion nicht unterstützt"
    0x13 -> "falsche Länge"
    0x22 -> "Bedingungen nicht erfüllt"
    0x31 -> "außerhalb des Bereichs"
    0x33 -> "Zugriff verweigert"
    0x7E, 0x7F -> "in dieser Sitzung nicht möglich"
    else -> "unbekannt"
}

/**
 * UDS-Diagnose über den ELM327. Nur lesende Dienste plus Fehlerspeicher löschen –
 * Codieren/Anpassen (0x2E, 0x31, 0x27) ist absichtlich nicht enthalten.
 */
class UdsClient(private val elm: ElmClient) {

    /** Stellt den Adapter auf ein bestimmtes Steuergerät ein. */
    suspend fun select(ecu: Ecu) {
        elm.raw("ATSH${ecu.tx}")        // Anfrage-ID
        elm.raw("ATCRA${ecu.rx}")       // nur Antworten dieses Steuergeräts
        elm.raw("ATFCSH${ecu.tx}")      // Flow-Control an das richtige Steuergerät
        elm.raw("ATFCSD300000")         // Flow-Control: weiter senden, keine Pause
        elm.raw("ATFCSM1")              // eigene Flow-Control-Einstellungen verwenden
        elm.raw("ATST96")               // etwas längeres Timeout (~600 ms) für langsame Steuergeräte
    }

    /** Zurück auf Standard-OBD (Broadcast 7DF). */
    suspend fun reset() {
        elm.raw("ATFCSM0")
        elm.raw("ATCRA")
        elm.raw("ATSH7DF")
        elm.raw("ATST32")
    }

    /**
     * Schickt eine Anfrage und liefert die positive Antwort (Hex) oder null, wenn das Steuergerät
     * nicht antwortet (nicht verbaut).
     */
    suspend fun request(hex: String, timeoutMs: Long = 4000): String? {
        val service = hex.substring(0, 2).toInt(16)
        val positive = "%02X".format(service + 0x40)
        repeat(3) { attempt ->
            val raw = elm.raw(hex, timeoutMs)
            val blocks = ElmParser.payloads(raw, hex)
            blocks.firstOrNull { it.startsWith(positive) }?.let { return it }

            val negative = blocks.firstOrNull { it.startsWith("7F") && it.length >= 6 }
            if (negative != null) {
                val nrc = negative.substring(4, 6).toInt(16)
                if (nrc != 0x78) throw UdsNegativeResponse(service, nrc)
                // 0x78 = "Antwort folgt" – kurz warten und erneut fragen
                delay(500L * (attempt + 1))
            } else {
                return null   // NO DATA o. Ä.: Steuergerät nicht verbaut
            }
        }
        return null
    }

    /** Liest einen Datenwert (Service 0x22) als Text, z. B. Teilenummer. */
    suspend fun readAscii(did: String): String? {
        val r = runCatching { request("22$did") }.getOrNull() ?: return null
        val data = ElmParser.hexToBytes(r.substring(6))
        return data.filter { it in 0x20..0x7E }.map { it.toChar() }.joinToString("").trim().ifEmpty { null }
    }

    /** Fehlerspeicher lesen (Service 0x19 02, alle Statusbits). */
    suspend fun readDtcs(): List<UdsDtc> {
        val r = request("1902FF") ?: return emptyList()
        val data = ElmParser.hexToBytes(r.substring(4))   // nach "5902"
        if (data.isEmpty()) return emptyList()
        val records = data.copyOfRange(1, data.size)      // erstes Byte = Verfügbarkeitsmaske
        return (0 until records.size / 4).map { i ->
            val o = i * 4
            UdsDtc(Dtc.decode(records[o], records[o + 1]), records[o + 2], records[o + 3])
        }
    }

    /** Fehlerspeicher löschen (Service 0x14, alle Gruppen). */
    suspend fun clearDtcs(): Boolean = request("14FFFFFF", 6000) != null

    companion object {
        const val DID_PART_NUMBER = "F187"   // VW-Teilenummer, z. B. 04L906026
        const val DID_SW_VERSION = "F189"
        const val DID_SYSTEM_NAME = "F197"   // z. B. "R4 2.0l TDI"
    }
}
