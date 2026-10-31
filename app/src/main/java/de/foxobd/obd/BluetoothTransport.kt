package de.foxobd.obd

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

/**
 * ELM327 über Bluetooth Classic (SPP / RFCOMM).
 * Der Adapter muss vorher in den Android-Einstellungen gekoppelt sein (PIN meist 1234 oder 0000).
 */
@SuppressLint("MissingPermission") // Berechtigung wird in der UI vorher geprüft
class BluetoothTransport(private val device: BluetoothDevice) : ObdTransport {

    override val name: String = device.name ?: device.address

    private var socket: BluetoothSocket? = null

    override suspend fun connect() = withContext(Dispatchers.IO) {
        close()
        val s = try {
            device.createRfcommSocketToServiceRecord(SPP_UUID).also { it.connect() }
        } catch (first: IOException) {
            // Manche China-Adapter wollen die "insecure"-Variante
            try {
                device.createInsecureRfcommSocketToServiceRecord(SPP_UUID).also { it.connect() }
            } catch (second: IOException) {
                throw ObdException("Verbindung zu $name fehlgeschlagen: ${second.message}", second)
            }
        }
        socket = s
    }

    override suspend fun send(command: String, timeoutMs: Long): String = withContext(Dispatchers.IO) {
        val s = socket ?: throw ObdException("Nicht verbunden")
        val input = s.inputStream
        val output = s.outputStream
        try {
            // Reste aus vorherigen Antworten verwerfen
            while (input.available() > 0) input.read()

            output.write((command + "\r").toByteArray(Charsets.US_ASCII))
            output.flush()

            val sb = StringBuilder()
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                if (input.available() > 0) {
                    val c = input.read()
                    if (c < 0) break
                    if (c.toChar() == '>') return@withContext sb.toString()
                    sb.append(c.toChar())
                } else {
                    delay(5)
                }
            }
            throw ObdException("Zeitüberschreitung bei '$command' (bisher: '${sb.toString().trim()}')")
        } catch (e: IOException) {
            throw ObdException("Verbindung verloren: ${e.message}", e)
        }
    }

    override fun close() {
        try { socket?.close() } catch (_: IOException) { }
        socket = null
    }

    companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}
