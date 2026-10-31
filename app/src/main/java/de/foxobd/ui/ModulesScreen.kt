package de.foxobd.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.foxobd.ModuleResult
import de.foxobd.ModuleStatus
import de.foxobd.ObdViewModel
import de.foxobd.UiState
import de.foxobd.obd.Ecu
import de.foxobd.obd.UdsDtc

@Composable
fun ModulesScreen(state: UiState, vm: ObdViewModel) {
    var showMissing by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf<Ecu?>(null) }

    val scanned = state.modules
    val shown = if (showMissing) scanned else scanned.filter { it.status != ModuleStatus.NOT_PRESENT }
    val totalFaults = scanned.sumOf { it.dtcs.size }

    Column(Modifier.fillMaxSize()) {
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())

        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = vm::scanModules, enabled = !state.busy) {
                Text(if (scanned.isEmpty()) "Steuergeräte scannen" else "Erneut scannen")
            }
            if (scanned.isNotEmpty()) {
                val found = scanned.count { it.status == ModuleStatus.OK || it.status == ModuleStatus.ERROR }
                Text(
                    "$found Steuergeräte gefunden · $totalFaults Fehler",
                    style = MaterialTheme.typography.titleSmall,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = showMissing, onCheckedChange = { showMissing = it })
                    Spacer(Modifier.width(8.dp))
                    Text("Nicht verbaute anzeigen", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                Text(
                    "Fragt die Steuergeräte des Autos einzeln ab (Motor, Getriebe, ABS, Airbag, Bordnetz …) " +
                        "und liest Teilenummer und Fehlerspeicher. Zündung an, Motor aus.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(shown, key = { it.ecu.address }) { m ->
                ModuleCard(
                    m,
                    expanded = expanded == m.ecu.address,
                    busy = state.busy,
                    didResult = state.didResults[m.ecu.address],
                    onReadDid = { did -> vm.readDid(m.ecu, did) },
                    onToggle = { expanded = if (expanded == m.ecu.address) null else m.ecu.address },
                    onClear = { confirmClear = m.ecu },
                )
            }
        }
    }

    confirmClear?.let { ecu ->
        AlertDialog(
            onDismissRequest = { confirmClear = null },
            title = { Text("Fehler löschen?") },
            text = {
                Text(
                    "Fehlerspeicher von „${ecu.name}“ wird gelöscht. Aktive Fehler kommen sofort wieder – " +
                        "notier dir die Codes vorher.",
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.clearModuleDtcs(ecu); confirmClear = null }) { Text("Löschen") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = null }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun ModuleCard(
    m: ModuleResult,
    expanded: Boolean,
    busy: Boolean,
    didResult: String?,
    onReadDid: (String) -> Unit,
    onToggle: () -> Unit,
    onClear: () -> Unit,
) {
    val (statusText, statusColor) = when (m.status) {
        ModuleStatus.WAITING -> "wartet" to MaterialTheme.colorScheme.onSurfaceVariant
        ModuleStatus.SCANNING -> "wird abgefragt …" to MaterialTheme.colorScheme.primary
        ModuleStatus.NOT_PRESENT -> "nicht verbaut / keine Antwort" to MaterialTheme.colorScheme.onSurfaceVariant
        ModuleStatus.ERROR -> "Fehler beim Lesen" to WarnRed
        ModuleStatus.OK -> if (m.dtcs.isEmpty()) "OK" to OkGreen else "${m.dtcs.size} Fehler" to WarnRed
    }

    Card(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = m.status == ModuleStatus.OK || m.status == ModuleStatus.ERROR, onClick = onToggle),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    m.ecu.address,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(m.ecu.name, style = MaterialTheme.typography.titleSmall)
                    val sub = listOfNotNull(m.partNumber, m.systemName).joinToString(" · ")
                    if (sub.isNotEmpty()) {
                        Text(
                            sub,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (m.status == ModuleStatus.SCANNING) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(statusText, style = MaterialTheme.typography.labelMedium, color = statusColor)
                }
            }

            if (expanded) {
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                m.error?.let { Text(it, color = WarnRed, style = MaterialTheme.typography.bodySmall) }
                if (m.dtcs.isEmpty() && m.error == null) {
                    Text("Keine Fehler gespeichert.", style = MaterialTheme.typography.bodyMedium)
                }
                m.dtcs.forEach { UdsDtcRow(it) }
                if (m.dtcs.isNotEmpty()) {
                    OutlinedButton(
                        onClick = onClear,
                        enabled = !busy,
                        modifier = Modifier.padding(top = 8.dp),
                    ) { Text("Fehler löschen") }
                }
                DidExplorer(busy, didResult, onReadDid)
            }
        }
    }
}

@Composable
private fun UdsDtcRow(dtc: UdsDtc) {
    val color: Color = if (dtc.active) WarnRed else FoxOrange
    Column(Modifier.padding(vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(dtc.display, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = color)
            Text(dtc.statusLabel, style = MaterialTheme.typography.labelMedium, color = color)
        }
        Text(dtc.description, style = MaterialTheme.typography.bodySmall)
    }
}

/** Eingabe für einen Messwert (DID) und Anzeige der Rohantwort. */
@Composable
private fun DidExplorer(busy: Boolean, result: String?, onRead: (String) -> Unit) {
    var did by remember { mutableStateOf("") }
    HorizontalDivider(Modifier.padding(vertical = 12.dp))
    Text("Messwert lesen (DID)", style = MaterialTheme.typography.titleSmall)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        OutlinedTextField(
            value = did,
            onValueChange = { did = it.take(4).uppercase() },
            placeholder = { Text("z. B. F187") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Button(onClick = { onRead(did) }, enabled = !busy && did.length == 4) { Text("Lesen") }
    }
    result?.let {
        Text(
            it,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
