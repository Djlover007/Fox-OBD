package de.foxobd.obd

/** Fehlercode, z. B. P0301. */
data class Dtc(val code: String, val kind: Kind) {
    enum class Kind(val label: String) {
        STORED("gespeichert"),
        PENDING("sporadisch / ausstehend"),
        PERMANENT("permanent"),
    }

    val description: String
        get() = DtcDescriptions.lookup(code)

    companion object {
        /** Wandelt zwei Rohbytes in einen Code wie "P0133" um. */
        fun decode(a: Int, b: Int): String {
            val letter = "PCBU"[(a shr 6) and 0x03]
            val d1 = (a shr 4) and 0x03
            val d2 = a and 0x0F
            return "%c%d%X%02X".format(letter, d1, d2, b)
        }
    }
}

/**
 * Kleine Auswahl generischer Codes. Herstellerspezifische Codes (P1xxx, U1xxx …)
 * sind hier nicht enthalten. Später gern durch eine CSV/JSON-Datei in assets ersetzen.
 */
object DtcDescriptions {
    private val known = mapOf(
        "P0100" to "Luftmassenmesser – Fehlfunktion",
        "P0101" to "Luftmassenmesser – Bereich/Leistung",
        "P0102" to "Luftmassenmesser – Signal zu niedrig",
        "P0103" to "Luftmassenmesser – Signal zu hoch",
        "P0110" to "Ansauglufttemperaturfühler – Fehlfunktion",
        "P0115" to "Kühlmitteltemperaturfühler – Fehlfunktion",
        "P0117" to "Kühlmitteltemperaturfühler – Signal zu niedrig",
        "P0118" to "Kühlmitteltemperaturfühler – Signal zu hoch",
        "P0120" to "Drosselklappenpotentiometer – Fehlfunktion",
        "P0128" to "Kühlmitteltemperatur unter Thermostat-Solltemperatur",
        "P0130" to "Lambdasonde (Bank 1, Sonde 1) – Fehlfunktion",
        "P0133" to "Lambdasonde (Bank 1, Sonde 1) – langsame Reaktion",
        "P0135" to "Lambdasonden-Heizung (Bank 1, Sonde 1) – Fehlfunktion",
        "P0141" to "Lambdasonden-Heizung (Bank 1, Sonde 2) – Fehlfunktion",
        "P0171" to "Gemisch zu mager (Bank 1)",
        "P0172" to "Gemisch zu fett (Bank 1)",
        "P0174" to "Gemisch zu mager (Bank 2)",
        "P0175" to "Gemisch zu fett (Bank 2)",
        "P0200" to "Einspritzventil – Stromkreis Fehlfunktion",
        "P0234" to "Ladedruck zu hoch (Overboost)",
        "P0299" to "Ladedruck zu niedrig (Underboost)",
        "P0300" to "Zufällige/mehrfache Zündaussetzer erkannt",
        "P0301" to "Zündaussetzer Zylinder 1",
        "P0302" to "Zündaussetzer Zylinder 2",
        "P0303" to "Zündaussetzer Zylinder 3",
        "P0304" to "Zündaussetzer Zylinder 4",
        "P0305" to "Zündaussetzer Zylinder 5",
        "P0306" to "Zündaussetzer Zylinder 6",
        "P0325" to "Klopfsensor 1 – Fehlfunktion",
        "P0335" to "Kurbelwellensensor – Fehlfunktion",
        "P0340" to "Nockenwellensensor – Fehlfunktion",
        "P0380" to "Glühkerzen – Stromkreis Fehlfunktion",
        "P0400" to "Abgasrückführung – Durchfluss Fehlfunktion",
        "P0401" to "Abgasrückführung – Durchfluss zu gering",
        "P0420" to "Katalysator (Bank 1) – Wirkungsgrad unter Schwellwert",
        "P0430" to "Katalysator (Bank 2) – Wirkungsgrad unter Schwellwert",
        "P0440" to "Tankentlüftung – Fehlfunktion",
        "P0442" to "Tankentlüftung – kleines Leck erkannt",
        "P0455" to "Tankentlüftung – großes Leck erkannt",
        "P0500" to "Geschwindigkeitssensor – Fehlfunktion",
        "P0505" to "Leerlaufregelung – Fehlfunktion",
        "P0562" to "Bordspannung zu niedrig",
        "P0563" to "Bordspannung zu hoch",
        "P0571" to "Bremslichtschalter – Fehlfunktion",
        "P0600" to "Serielle Kommunikation – Fehlfunktion",
        "P0700" to "Getriebesteuerung – Fehler gespeichert",
        "P2002" to "Partikelfilter (Bank 1) – Wirkungsgrad unter Schwellwert",
        "P2015" to "Saugrohrklappen-Positionssensor – Bereich/Leistung",
        "P242F" to "Partikelfilter – Aschebeladung zu hoch",
        "U0100" to "Keine Kommunikation mit Motorsteuergerät",
        "U0101" to "Keine Kommunikation mit Getriebesteuergerät",
        "U0121" to "Keine Kommunikation mit ABS-Steuergerät",
        "U0155" to "Keine Kommunikation mit Kombiinstrument",
    )

    fun lookup(code: String): String = known[code] ?: when (code.getOrNull(1)) {
        '1', '3' -> "Herstellerspezifischer Code"
        else -> "Keine Beschreibung hinterlegt"
    }
}
