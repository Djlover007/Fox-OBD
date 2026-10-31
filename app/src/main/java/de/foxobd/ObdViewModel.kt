package de.foxobd

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.foxobd.obd.BluetoothTransport
import de.foxobd.obd.DemoTransport
import de.foxobd.obd.Dtc
import de.foxobd.obd.ElmClient
import de.foxobd.obd.ObdTransport
import de.foxobd.obd.Pids
import de.foxobd.obd.Ecu
import de.foxobd.obd.UdsClient
import de.foxobd.obd.UdsDtc
import de.foxobd.obd.VwEcus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PairedDevice(val name: String, val address: String)

enum class ModuleStatus { WAITING, SCANNING, OK, NOT_PRESENT, ERROR }

data class ModuleResult(
    val ecu: Ecu,
    val status: ModuleStatus = ModuleStatus.WAITING,
    val partNumber: String? = null,
    val systemName: String? = null,
    val dtcs: List<UdsDtc> = emptyList(),
    val error: String? = null,
)

enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED }

data class UiState(
    val connection: ConnectionState = ConnectionState.DISCONNECTED,
    val deviceName: String? = null,
    val adapterVersion: String? = null,
    val protocol: String? = null,
    val vin: String? = null,
    val batteryVoltage: Double? = null,
    val pairedDevices: List<PairedDevice> = emptyList(),
    val live: Map<String, Double?> = emptyMap(),   // PID-Code → Wert
    val liveRunning: Boolean = false,
    val dtcs: List<Dtc>? = null,                   // null = noch nicht gelesen
    val modules: List<ModuleResult> = emptyList(),  // leer = noch nicht gescannt
    val didResults: Map<String, String> = emptyMap(), // Steuergeräte-Adresse → letzte DID-Antwort
    val busy: Boolean = false,
    val message: String? = null,
    val log: List<String> = emptyList(),
)

class ObdViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var client: ElmClient? = null
    private var liveJob: Job? = null
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.GERMANY)

    private val bluetoothAdapter =
        app.getSystemService(BluetoothManager::class.java)?.adapter

    private fun log(line: String) {
        val entry = "${timeFormat.format(Date())}  $line"
        _state.update { it.copy(log = (it.log + entry).takeLast(500)) }
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    @SuppressLint("MissingPermission")
    fun loadPairedDevices() {
        val adapter = bluetoothAdapter
        if (adapter == null) {
            _state.update { it.copy(message = "Dieses Gerät hat kein Bluetooth.") }
            return
        }
        if (!adapter.isEnabled) {
            _state.update { it.copy(message = "Bitte Bluetooth einschalten.") }
        }
        val devices = try {
            adapter.bondedDevices.orEmpty()
                .map { PairedDevice(it.name ?: "Unbekannt", it.address) }
                .sortedByDescending { d -> OBD_HINTS.any { d.name.contains(it, ignoreCase = true) } }
        } catch (e: SecurityException) {
            _state.update { it.copy(message = "Bluetooth-Berechtigung fehlt.") }
            emptyList()
        }
        _state.update { it.copy(pairedDevices = devices) }
    }

    fun connect(device: PairedDevice) {
        val btDevice: BluetoothDevice = bluetoothAdapter?.getRemoteDevice(device.address) ?: return
        connectWith(BluetoothTransport(btDevice))
    }

    fun connectDemo() = connectWith(DemoTransport())

    private fun connectWith(transport: ObdTransport) {
        disconnect()
        _state.update {
            it.copy(connection = ConnectionState.CONNECTING, deviceName = transport.name, message = null)
        }
        viewModelScope.launch {
            val c = ElmClient(transport, ::log)
            try {
                c.connectAndInit()
                client = c
                _state.update {
                    it.copy(
                        connection = ConnectionState.CONNECTED,
                        adapterVersion = c.adapterVersion,
                        protocol = c.protocol,
                    )
                }
                val vin = runCatching { c.readVin() }.getOrNull()
                val volt = runCatching { c.batteryVoltage() }.getOrNull()
                _state.update { it.copy(vin = vin, batteryVoltage = volt) }
                startLive()
            } catch (e: CancellationException) {
                c.close()
                throw e
            } catch (e: Exception) {
                c.close()
                log("Fehler: ${e.message}")
                _state.update {
                    it.copy(connection = ConnectionState.DISCONNECTED, message = e.message ?: "Verbindung fehlgeschlagen")
                }
            }
        }
    }

    fun disconnect() {
        liveJob?.cancel()
        liveJob = null
        client?.close()
        client = null
        _state.update {
            UiState(pairedDevices = it.pairedDevices, log = it.log)
        }
    }

    fun startLive() {
        val c = client ?: return
        if (liveJob?.isActive == true) return
        _state.update { it.copy(liveRunning = true) }
        liveJob = viewModelScope.launch {
            // PIDs, die einmal "NO DATA" liefern, werden nicht mehr abgefragt
            val unsupported = mutableSetOf<String>()
            var round = 0
            try {
                while (isActive) {
                    for (pid in Pids.dashboard) {
                        if (pid.code in unsupported) continue
                        val value = c.read(pid)
                        if (value == null) unsupported += pid.code
                        _state.update { it.copy(live = it.live + (pid.code to value)) }
                    }
                    if (round++ % 10 == 0) {
                        val v = c.batteryVoltage()
                        _state.update { it.copy(batteryVoltage = v) }
                    }
                    delay(100)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("Live-Daten gestoppt: ${e.message}")
                _state.update { it.copy(message = e.message, liveRunning = false) }
            }
        }
        liveJob?.invokeOnCompletion { _state.update { it.copy(liveRunning = false) } }
    }

    fun stopLive() {
        liveJob?.cancel()
        liveJob = null
    }

    fun readDtcs() = withPausedLive {
        val dtcs = it.readDtcs()
        _state.update { s -> s.copy(dtcs = dtcs) }
    }

    fun clearDtcs() = withPausedLive {
        val ok = it.clearDtcs()
        _state.update { s ->
            s.copy(message = if (ok) "Fehlerspeicher gelöscht." else "Löschen wurde nicht bestätigt.")
        }
        val dtcs = it.readDtcs()
        _state.update { s -> s.copy(dtcs = dtcs) }
    }

    /** Fragt alle bekannten VW-Steuergeräte nacheinander ab. */
    fun scanModules() = withPausedLive { elm ->
        if (!elm.isCan) {
            _state.update { it.copy(message = "Steuergeräte-Scan braucht CAN – dieses Auto nutzt ${elm.protocol}.") }
            return@withPausedLive
        }
        val uds = UdsClient(elm)
        _state.update { s -> s.copy(modules = VwEcus.all.map { ModuleResult(it) }) }
        try {
            for (ecu in VwEcus.all) {
                updateModule(ecu) { it.copy(status = ModuleStatus.SCANNING) }
                val result = scanOne(uds, ecu)
                updateModule(ecu) { result }
            }
        } finally {
            uds.reset()
        }
    }

    fun clearModuleDtcs(ecu: Ecu) = withPausedLive { elm ->
        val uds = UdsClient(elm)
        try {
            uds.select(ecu)
            val ok = uds.clearDtcs()
            _state.update { it.copy(message = if (ok) "${ecu.name}: Fehlerspeicher gelöscht." else "${ecu.name}: Löschen nicht bestätigt.") }
            val dtcs = uds.readDtcs()
            updateModule(ecu) { it.copy(dtcs = dtcs) }
        } finally {
            uds.reset()
        }
    }

    /**
     * Liest einen beliebigen Messwert (UDS 0x22) – zum Erforschen, welche Werte das Auto liefert.
     * Ergebnis: Rohdaten in Hex, Dezimalwerte der Bytes und ggf. Text.
     */
    fun readDid(ecu: Ecu, did: String) {
        val clean = did.uppercase().filter { it in '0'..'9' || it in 'A'..'F' }
        if (clean.length != 4) {
            _state.update { it.copy(message = "DID bitte als 4 Hex-Zeichen, z. B. F187") }
            return
        }
        withPausedLive { elm ->
            val uds = UdsClient(elm)
            val text = try {
                uds.select(ecu)
                val r = uds.request("22$clean")
                if (r == null) {
                    "$clean: keine Antwort"
                } else {
                    val bytes = de.foxobd.obd.ElmParser.hexToBytes(r.substring(6))
                    val hex = bytes.joinToString(" ") { "%02X".format(it) }
                    val dec = bytes.joinToString(" ")
                    val ascii = bytes.filter { it in 0x20..0x7E }.map { it.toChar() }.joinToString("")
                    buildString {
                        append("$clean = $hex\n")
                        append("dezimal: $dec")
                        if (ascii.length >= 3) append("\ntext: $ascii")
                    }
                }
            } catch (e: de.foxobd.obd.ObdException) {
                "$clean: ${e.message}"
            } finally {
                uds.reset()
            }
            _state.update { it.copy(didResults = it.didResults + (ecu.address to text)) }
        }
    }

    private suspend fun scanOne(uds: UdsClient, ecu: Ecu): ModuleResult = try {
        uds.select(ecu)
        val part = uds.readAscii(UdsClient.DID_PART_NUMBER)
        if (part == null) {
            ModuleResult(ecu, ModuleStatus.NOT_PRESENT)
        } else {
            val system = uds.readAscii(UdsClient.DID_SYSTEM_NAME)
            val dtcs = runCatching { uds.readDtcs() }
            ModuleResult(
                ecu,
                if (dtcs.isSuccess) ModuleStatus.OK else ModuleStatus.ERROR,
                partNumber = part,
                systemName = system,
                dtcs = dtcs.getOrDefault(emptyList()),
                error = dtcs.exceptionOrNull()?.message,
            )
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log("${ecu.address} ${ecu.name}: ${e.message}")
        ModuleResult(ecu, ModuleStatus.ERROR, error = e.message)
    }

    private fun updateModule(ecu: Ecu, change: (ModuleResult) -> ModuleResult) =
        _state.update { s -> s.copy(modules = s.modules.map { if (it.ecu == ecu) change(it) else it }) }

    /** Hält die Live-Abfrage an, damit Fehlercode-Befehle nicht dazwischenfunken. */
    private fun withPausedLive(block: suspend (ElmClient) -> Unit) {
        val c = client ?: return
        val wasRunning = liveJob?.isActive == true
        viewModelScope.launch {
            liveJob?.let { it.cancel(); it.join() }
            _state.update { it.copy(busy = true) }
            try {
                block(c)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("Fehler: ${e.message}")
                _state.update { it.copy(message = e.message) }
            } finally {
                _state.update { it.copy(busy = false) }
                if (wasRunning && client === c) startLive()
            }
        }
    }

    override fun onCleared() {
        disconnect()
    }

    companion object {
        private val OBD_HINTS = listOf("OBD", "ELM", "V-LINK", "VLINK", "VGATE", "ICAR", "KONNWEI", "VEEPEAK")
    }
}
