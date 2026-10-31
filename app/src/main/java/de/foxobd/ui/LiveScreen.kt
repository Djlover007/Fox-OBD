package de.foxobd.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.foxobd.ObdViewModel
import de.foxobd.UiState
import de.foxobd.obd.Pid
import de.foxobd.obd.Pids
import java.util.Locale

@Composable
fun LiveScreen(state: UiState, vm: ObdViewModel) {
    // PIDs, die das Auto nicht kennt (Wert null nach der ersten Abfrage), ausblenden
    val visible = Pids.dashboard.filter { !state.live.containsKey(it.code) || state.live[it.code] != null }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(160.dp),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    InfoRow("Adapter", state.adapterVersion)
                    InfoRow("Protokoll", state.protocol)
                    InfoRow("FIN", state.vin ?: "nicht verfügbar")
                    InfoRow("Batterie", state.batteryVoltage?.let { "%.1f V".format(Locale.GERMANY, it) })
                    Row(
                        Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (state.liveRunning) {
                            OutlinedButton(onClick = vm::stopLive) { Text("Pause") }
                        } else {
                            Button(onClick = vm::startLive) { Text("Live starten") }
                        }
                    }
                }
            }
        }
        items(visible, key = { it.code }) { pid ->
            GaugeCard(pid, state.live[pid.code])
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value ?: "–", fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun GaugeCard(pid: Pid, value: Double?) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    val fraction = value?.let { ((it - pid.min) / (pid.max - pid.min)).coerceIn(0.0, 1.0).toFloat() } ?: 0f

    Card {
        Column(
            Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(pid.name, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            Box(
                Modifier.fillMaxWidth().aspectRatio(1.4f).padding(top = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 10.dp.toPx()
                    val diameter = minOf(size.width, size.height * 1.4f) - stroke
                    val topLeft = Offset((size.width - diameter) / 2, stroke / 2)
                    val arcSize = Size(diameter, diameter)
                    drawArc(track, 150f, 240f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                    if (value != null) {
                        drawArc(accent, 150f, 240f * fraction, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        value?.let { formatValue(it) } ?: "–",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(pid.unit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun formatValue(v: Double): String =
    if (v >= 100 || v == Math.floor(v)) "%.0f".format(Locale.GERMANY, v) else "%.1f".format(Locale.GERMANY, v)
