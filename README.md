# Fox OBD

Eigene Android-Diagnose-App für Standard-OBD-II über einen ELM327-Bluetooth-Adapter.

## Was sie kann (v0.3)

- Gekoppelte Bluetooth-Adapter anzeigen und verbinden (ELM327, vLinker, Vgate, OBDLink …)
- Automatische Protokollerkennung (CAN, K-Line, J1850)
- **Live-Daten** als Rundinstrumente: Drehzahl, Geschwindigkeit, Kühlmittel, Last, Drosselklappe,
  Ansaugluft, Saugrohrdruck, Luftmasse, Tank, Steuergerätespannung, Öltemperatur;
  Werte, die das Auto nicht liefert, werden ausgeblendet
- **Diesel-Werte** (Standard-OBD): Raildruck, Verbrauch, DPF-Differenzdruck, DPF-Temperatur,
  Luftdruck, Strecke seit Fehler löschen
- Batteriespannung (`ATRV`) und Fahrgestellnummer (Mode 09)
- **Fehlercodes** lesen (gespeichert, sporadisch, permanent) und löschen (mit Sicherheitsabfrage)
- **Steuergeräte-Scan (VW, UDS über CAN):** fragt Motor, Getriebe, ABS, Klima, Bordnetz, Airbag,
  Lenksäule, Kombi, Gateway, Lenkhilfe und Infotainment einzeln ab – mit Teilenummer, Systemname
  und Fehlerspeicher (aktiv / gespeichert / sporadisch); Fehler pro Steuergerät löschbar.
  Ausgelegt für VW-Modelle ab ca. 2012, z. B. Caddy 4 (2015–2020). Codieren ist bewusst nicht enthalten.
- **Messwert-Explorer:** pro Steuergerät beliebige UDS-Messwerte (DID, Service 0x22) lesen und
  Rohdaten anzeigen – um herauszufinden, wo dein Auto z. B. die Rußbeladung ablegt
- **Log** der kompletten Kommunikation mit dem Adapter
- **Demo-Modus** – simuliertes Auto, damit du die App ohne Adapter testen kannst

## Loslegen

1. Ordner in **Android Studio** öffnen (Ladybug oder neuer). Gradle synchronisiert und lädt alles selbst.
2. Handy per USB anschließen → ▶ Run.
3. Erst mal **Demo-Modus** probieren.
4. Fürs Auto: Adapter einstecken, Zündung an, Adapter in den Android-Bluetooth-Einstellungen
   koppeln, in der App antippen.

Unit-Tests für den Parser: `./gradlew test`

## Aufbau

```
app/src/main/java/de/foxobd/
├── obd/                    ← reiner OBD-Kern, ohne UI
│   ├── ObdTransport.kt     Schnittstelle: Befehl rein, Antwort raus
│   ├── BluetoothTransport  ELM327 über Bluetooth Classic (SPP)
│   ├── DemoTransport       Simuliertes Auto
│   ├── ElmClient           Init-Sequenz, PIDs, Fehlercodes, FIN
│   ├── ElmParser           Antworten auswerten (auch ISO-TP-Mehrfachrahmen)
│   ├── Uds                 VW-Steuergeräte, UDS-Dienste 0x22 / 0x19 / 0x14
│   ├── Pids                Messwerte + Umrechnungsformeln
│   └── Dtc                 Fehlercode-Dekodierung + Beschreibungen
├── ObdViewModel.kt         Zustand, Live-Abfrage, Verbindung
└── ui/                     Compose-Oberfläche (Verbinden, Live, Fehler, Log)
```

## Ideen für die nächsten Schritte

- **BLE-Adapter** (z. B. vLinker FS/MC+ BLE, Veepeak BLE): zweiten Transport mit `BluetoothGatt` ergänzen
- **Unterstützte PIDs abfragen** (`0100`, `0120`, `0140` …) statt alles durchzuprobieren
- **Fehlercode-Datenbank** als JSON in `assets/` statt der kleinen Liste in `Dtc.kt`
- **Freeze Frame** (Mode 02) und **Readiness-Monitore** (PID 01 01)
- **Aufzeichnung** von Fahrten als CSV + Diagramme
- **VW-Messwerte** per Service 0x22 (z. B. DPF-Rußbeladung, AdBlue-Füllstand, Öltemperatur) –
  die DIDs hängen vom Motor ab
- **Fehler-Umgebungsdaten** (Kilometerstand, Datum beim Auftreten) per Service 0x19 04/06
- Codieren (Service 0x2E) erst ganz am Ende und nur mit Backup – falsche Werte können Steuergeräte lahmlegen.
