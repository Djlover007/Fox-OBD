package de.foxobd.obd

/**
 * Reine Text-Logik ohne Android-Abhängigkeiten – dadurch gut testbar (siehe ElmParserTest).
 * Erwartet Antworten mit ATS0 (keine Leerzeichen) und ATH0 (keine Header).
 */
object ElmParser {

    private val errorMarkers = listOf(
        "NO DATA", "UNABLE TO CONNECT", "CAN ERROR", "BUS ERROR", "BUS INIT", "BUFFER FULL",
        "STOPPED", "ERROR", "?",
    )

    /** Antwortzeilen ohne Echo, "SEARCHING...", Leerzeichen; Großbuchstaben. */
    fun lines(raw: String, command: String? = null): List<String> =
        raw.replace("SEARCHING...", "")
            .split('\r', '\n')
            .map { it.replace(" ", "").trim().uppercase() }
            .filter { it.isNotEmpty() }
            .filter { command == null || it != command.replace(" ", "").uppercase() }

    /** Textzeilen unverändert (für ATI, ATDP …), ohne Leerzeilen und Prompt-Reste. */
    fun textLines(raw: String): List<String> =
        raw.split('\r', '\n').map { it.trim() }.filter { it.isNotEmpty() }

    /** Liefert eine lesbare Fehlermeldung, falls der Adapter einen Fehler meldet, sonst null. */
    fun error(raw: String): String? {
        val up = raw.uppercase()
        val compact = up.replace(" ", "")
        return errorMarkers.firstOrNull { marker ->
            if (marker == "?") lines(up).any { it == "?" } else compact.contains(marker.replace(" ", ""))
        }
    }

    /**
     * Fügt Antworten zu Datenblöcken (Hex-Strings) zusammen – einer pro Steuergerät bzw. Nachricht.
     * Unterstützt ISO-TP-Mehrfachrahmen im ELM-Format:
     *   014
     *   0:490201314A34
     *   1:...
     */
    fun payloads(raw: String, command: String? = null): List<String> {
        val ls = lines(raw, command).filter { line -> line.all { it.isHexOrColon() } }
        if (ls.isEmpty()) return emptyList()

        val result = mutableListOf<String>()
        var i = 0
        while (i < ls.size) {
            val line = ls[i]
            val next = ls.getOrNull(i + 1)
            if (!line.contains(':') && line.length == 3 && next != null && next.startsWith("0:")) {
                // Mehrfachrahmen: erste Zeile = Gesamtlänge in Bytes (hex)
                val length = line.toInt(16)
                val sb = StringBuilder()
                i++
                while (i < ls.size && ls[i].length >= 2 && ls[i][1] == ':') {
                    sb.append(ls[i].substring(2))
                    i++
                }
                result += sb.toString().take(length * 2)
            } else {
                result += line.substringAfter(':')
                i++
            }
        }
        return result.filter { it.isNotEmpty() }
    }

    fun hexToBytes(hex: String): IntArray =
        IntArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16) }

    /**
     * Wertet eine Mode-01-Antwort aus, z. B. "410C1AF8" für PID 0C.
     * Gibt null zurück, wenn kein passender Datenblock gefunden wurde.
     */
    fun parsePid(raw: String, pid: Pid): Double? {
        val prefix = "41${pid.code}"
        val block = payloads(raw, pid.command).firstOrNull { it.startsWith(prefix) } ?: return null
        val data = hexToBytes(block.removePrefix(prefix))
        if (data.size < pid.bytes) return null
        return pid.formula(data)
    }

    /**
     * Wertet Mode 03 / 07 / 0A aus. Bei CAN steht nach dem Antwort-Byte die Anzahl der Codes,
     * bei K-Line/J1850 nicht (dort wird mit 0000 aufgefüllt).
     */
    fun parseDtcs(raw: String, mode: String, isCan: Boolean, kind: Dtc.Kind): List<Dtc> {
        val responseByte = (mode.toInt(16) + 0x40).toString(16).uppercase()
        val codes = linkedSetOf<String>()
        for (block in payloads(raw, mode)) {
            if (!block.startsWith(responseByte)) continue
            var data = hexToBytes(block.substring(2))
            if (isCan && data.isNotEmpty()) {
                val count = data[0]
                data = data.copyOfRange(1, minOf(data.size, 1 + count * 2))
            }
            var j = 0
            while (j + 1 < data.size) {
                val a = data[j]
                val b = data[j + 1]
                if (a != 0 || b != 0) codes += Dtc.decode(a, b)
                j += 2
            }
        }
        return codes.map { Dtc(it, kind) }
    }

    /** Fahrgestellnummer aus Mode 09 PID 02. */
    fun parseVin(raw: String): String? {
        // CAN: ein Block "4902 01 <17 ASCII-Zeichen>"; K-Line: mehrere Zeilen "4902 <Nr> <4 Bytes>".
        // Zähler- und Füllbytes sind keine druckbaren Zeichen und fallen beim Filtern weg.
        val blocks = payloads(raw, "0902").filter { it.startsWith("4902") }
        if (blocks.isEmpty()) return null
        val ascii = blocks.flatMap { hexToBytes(it.removePrefix("4902")).toList() }
            .filter { it in 0x30..0x39 || it in 0x41..0x5A }
            .map { it.toChar() }
            .joinToString("")
        return ascii.takeLast(17).takeIf { it.length == 17 }
    }

    /** ATDPN liefert z. B. "A6" oder "6"; 6–9 und A–C sind CAN-Varianten. */
    fun isCanProtocol(dpnResponse: String): Boolean {
        val p = lines(dpnResponse, "ATDPN").firstOrNull()?.removePrefix("A") ?: return false
        return p.firstOrNull() in listOf('6', '7', '8', '9', 'A', 'B', 'C')
    }

    private fun Char.isHexOrColon() = this == ':' || this in '0'..'9' || this in 'A'..'F'
}
