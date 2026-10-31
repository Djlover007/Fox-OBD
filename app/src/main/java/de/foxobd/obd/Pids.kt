package de.foxobd.obd

/** Ein Standard-OBD-II-Messwert aus Mode 01 (SAE J1979). */
data class Pid(
    val code: String,          // z. B. "0C"
    val name: String,
    val unit: String,
    val bytes: Int,            // erwartete Datenbytes
    val min: Double,
    val max: Double,
    val formula: (IntArray) -> Double,
) {
    val command: String get() = "01$code"
}

object Pids {
    val RPM = Pid("0C", "Drehzahl", "1/min", 2, 0.0, 8000.0) { (it[0] * 256 + it[1]) / 4.0 }
    val SPEED = Pid("0D", "Geschwindigkeit", "km/h", 1, 0.0, 260.0) { it[0].toDouble() }
    val COOLANT = Pid("05", "Kühlmittel", "°C", 1, -40.0, 130.0) { it[0] - 40.0 }
    val INTAKE_TEMP = Pid("0F", "Ansauglufttemp.", "°C", 1, -40.0, 80.0) { it[0] - 40.0 }
    val LOAD = Pid("04", "Motorlast", "%", 1, 0.0, 100.0) { it[0] * 100.0 / 255.0 }
    val THROTTLE = Pid("11", "Drosselklappe", "%", 1, 0.0, 100.0) { it[0] * 100.0 / 255.0 }
    val MAP = Pid("0B", "Saugrohrdruck", "kPa", 1, 0.0, 255.0) { it[0].toDouble() }
    val MAF = Pid("10", "Luftmasse", "g/s", 2, 0.0, 300.0) { (it[0] * 256 + it[1]) / 100.0 }
    val FUEL = Pid("2F", "Tankfüllung", "%", 1, 0.0, 100.0) { it[0] * 100.0 / 255.0 }
    val ECU_VOLTAGE = Pid("42", "Spannung (Steuergerät)", "V", 2, 0.0, 16.0) { (it[0] * 256 + it[1]) / 1000.0 }
    val OIL_TEMP = Pid("5C", "Öltemperatur", "°C", 1, -40.0, 150.0) { it[0] - 40.0 }


    // --- Diesel (SAE J1979). Nicht jedes Steuergerät unterstützt alle; nicht unterstützte werden ausgeblendet.
    val RAIL_PRESSURE = Pid("23", "Raildruck", "bar", 2, 0.0, 2200.0) { (it[0] * 256 + it[1]) / 10.0 }
    val FUEL_RATE = Pid("5E", "Verbrauch", "l/h", 2, 0.0, 30.0) { (it[0] * 256 + it[1]) / 20.0 }
    val BARO = Pid("33", "Luftdruck", "kPa", 1, 80.0, 110.0) { it[0].toDouble() }

    /** Byte A = Unterstützungs-Bits, B-C = Differenzdruck (vorzeichenbehaftet, 1/100 kPa) → mbar. */
    val DPF_DELTA = Pid("7A", "DPF-Differenzdruck", "mbar", 3, 0.0, 300.0) {
        ((it[1] shl 8) or it[2]).toShort() / 100.0 * 10.0
    }

    /** Byte A = Unterstützungs-Bits, B-C = Eingangstemperatur Bank 1. Bytebelegung am Auto per Log prüfen. */
    val DPF_TEMP = Pid("7C", "DPF-Temperatur", "°C", 3, 0.0, 800.0) { (it[1] * 256 + it[2]) / 10.0 - 40.0 }

    val DISTANCE_SINCE_CLEAR = Pid("31", "Strecke seit Fehler löschen", "km", 2, 0.0, 65535.0) {
        (it[0] * 256 + it[1]).toDouble()
    }

    val dashboard = listOf(
        RPM, SPEED, COOLANT, OIL_TEMP, LOAD, THROTTLE, INTAKE_TEMP, MAP, MAF, FUEL, ECU_VOLTAGE,
        RAIL_PRESSURE, FUEL_RATE, DPF_DELTA, DPF_TEMP, BARO, DISTANCE_SINCE_CLEAR,
    )
}
