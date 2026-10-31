package de.foxobd.obd

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Höhere Ebene über dem Transport: Initialisierung, Messwerte, Fehlercodes.
 * Alle Befehle laufen über eine Mutex, weil der ELM327 nur einen Befehl gleichzeitig verarbeitet.
 */
class ElmClient(
    private val transport: ObdTransport,
    private val log: (String) -> Unit = {},
) {
    private val mutex = Mutex()

    var adapterVersion: String = "?"
        private set
    var protocol: String = "?"
        private set
    var isCan: Boolean = false
        private set

    suspend fun raw(command: String, timeoutMs: Long = 3000): String = mutex.withLock {
        log("→ $command")
        val response = transport.send(command, timeoutMs)
        log("← ${response.trim().replace("\r", " | ")}")
        response
    }

    suspend fun connectAndInit() {
        log("Verbinde mit ${transport.name} …")
        transport.connect()
        raw("ATZ", 5000)                       // Reset
        adapterVersion = ElmParser.textLines(raw("ATI")).lastOrNull() ?: "?"
        raw("ATE0")                            // Echo aus
        raw("ATL0")                            // keine Zeilenvorschübe
        raw("ATS0")                            // keine Leerzeichen
        raw("ATH0")                            // keine Header
        raw("ATAT1")                           // adaptives Timing
        raw("ATSP0")                           // Protokoll automatisch suchen

        // Erste Anfrage löst die Protokollsuche aus – kann einige Sekunden dauern
        val probe = raw("0100", 10000)
        ElmParser.error(probe)?.let {
            throw ObdException("Auto antwortet nicht ($it). Zündung an?")
        }
        protocol = ElmParser.textLines(raw("ATDP")).lastOrNull()?.removePrefix("AUTO,")?.trim() ?: "?"
        isCan = ElmParser.isCanProtocol(raw("ATDPN"))
        log("Protokoll: $protocol (CAN: $isCan)")
    }

    /** Batteriespannung direkt am OBD-Stecker, gemessen vom Adapter. */
    suspend fun batteryVoltage(): Double? =
        ElmParser.lines(raw("ATRV")).firstOrNull()?.removeSuffix("V")?.toDoubleOrNull()

    suspend fun read(pid: Pid): Double? = ElmParser.parsePid(raw(pid.command), pid)

    suspend fun readVin(): String? = ElmParser.parseVin(raw("0902", 5000))

    suspend fun readDtcs(): List<Dtc> {
        val stored = ElmParser.parseDtcs(raw("03", 5000), "03", isCan, Dtc.Kind.STORED)
        val pending = ElmParser.parseDtcs(raw("07", 5000), "07", isCan, Dtc.Kind.PENDING)
        val permanent = if (isCan) {
            ElmParser.parseDtcs(raw("0A", 5000), "0A", isCan, Dtc.Kind.PERMANENT)
        } else emptyList()
        return stored + pending.filter { p -> stored.none { it.code == p.code } } + permanent
    }

    /** Mode 04: löscht Fehlerspeicher und Readiness-Status. Nur bei Zündung an / Motor aus! */
    suspend fun clearDtcs(): Boolean {
        val r = raw("04", 5000)
        return ElmParser.payloads(r, "04").any { it.startsWith("44") }
    }

    fun close() = transport.close()
}
