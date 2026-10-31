package de.foxobd.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.foxobd.ObdViewModel
import de.foxobd.UiState
import de.foxobd.obd.Dtc

@Composable
fun DtcScreen(state: UiState, vm: ObdViewModel) {
    var confirmClear by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())

        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = vm::readDtcs, enabled = !state.busy) { Text("Fehler auslesen") }
            OutlinedButton(
                onClick = { confirmClear = true },
                enabled = !state.busy && !state.dtcs.isNullOrEmpty(),
            ) { Text("Löschen") }
        }

        when {
            state.dtcs == null -> Hint("Noch nicht ausgelesen. Tipp auf „Fehler auslesen“.")
            state.dtcs.isEmpty() -> Hint("Keine Fehler im Motorsteuergerät gespeichert. 👍")
            else -> LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.dtcs, key = { it.code + it.kind }) { DtcCard(it) }
                item {
                    Text(
                        "Hinweis: Standard-OBD liest nur abgasrelevante Steuergeräte (meist Motor/Getriebe). " +
                            "Airbag, ABS, Komfort usw. brauchen herstellerspezifische Diagnose.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Fehlerspeicher löschen?") },
            text = {
                Text(
                    "Nur bei Zündung an und Motor aus. Dabei werden auch die Readiness-Werte " +
                        "zurückgesetzt – das Auto braucht danach einige Fahrten, bis z. B. die AU wieder möglich ist.",
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; vm.clearDtcs() }) { Text("Löschen") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Abbrechen") }
            },
        )
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        modifier = Modifier.padding(16.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun DtcCard(dtc: Dtc) {
    val color = when (dtc.kind) {
        Dtc.Kind.STORED, Dtc.Kind.PERMANENT -> WarnRed
        Dtc.Kind.PENDING -> FoxOrange
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    dtc.code,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge,
                    color = color,
                )
                Text(dtc.kind.label, style = MaterialTheme.typography.labelMedium, color = color)
            }
            Text(dtc.description, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
