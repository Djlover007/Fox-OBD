package de.foxobd.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import de.foxobd.ConnectionState
import de.foxobd.ObdViewModel
import de.foxobd.UiState

@Composable
fun ConnectScreen(state: UiState, vm: ObdViewModel) {
    val context = LocalContext.current
    val needsRuntimePermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    fun hasPermission() = !needsRuntimePermission ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
        PackageManager.PERMISSION_GRANTED

    var granted by remember { mutableStateOf(hasPermission()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        if (it) vm.loadPairedDevices()
    }

    LaunchedEffect(granted) {
        if (granted) vm.loadPairedDevices()
    }

    val connecting = state.connection == ConnectionState.CONNECTING

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "OBD-Adapter verbinden",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            "Steck den ELM327-Adapter in die OBD-Buchse, mach die Zündung an und kopple ihn " +
                "einmal in den Bluetooth-Einstellungen (PIN meist 1234 oder 0000).",
            style = MaterialTheme.typography.bodyMedium,
        )

        if (connecting) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Text("Verbinde mit ${state.deviceName} … (Protokollsuche kann ~10 s dauern)")
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
                enabled = !connecting,
            ) { Text("Bluetooth-Einstellungen") }
            Button(onClick = vm::connectDemo, enabled = !connecting) { Text("Demo-Modus") }
        }

        if (!granted) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Für die Verbindung braucht die App die Berechtigung „Geräte in der Nähe“.")
                    Button(onClick = { launcher.launch(Manifest.permission.BLUETOOTH_CONNECT) }) {
                        Text("Berechtigung erteilen")
                    }
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Gekoppelte Geräte", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = vm::loadPairedDevices, enabled = !connecting) { Text("Aktualisieren") }
            }
            if (state.pairedDevices.isEmpty()) {
                Text(
                    "Keine gekoppelten Geräte gefunden.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.pairedDevices, key = { it.address }) { d ->
                    Card(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !connecting) { vm.connect(d) },
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(d.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                d.address,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
