package de.foxobd.obd

/**
 * Rohe Verbindung zum Adapter. Schickt einen Befehl und liefert die Antwort
 * bis zum ELM327-Prompt '>' (ohne den Prompt selbst).
 */
interface ObdTransport {
    val name: String
    suspend fun connect()
    suspend fun send(command: String, timeoutMs: Long = 3000): String
    fun close()
}

open class ObdException(message: String, cause: Throwable? = null) : Exception(message, cause)
