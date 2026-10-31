package de.foxobd.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.foxobd.ConnectionState
import de.foxobd.ObdViewModel

private enum class Tab(val label: String, val icon: ImageVector) {
    LIVE("Live", Icons.Filled.Home),
    DTC("OBD-Fehler", Icons.Filled.Warning),
    MODULES("Steuergeräte", Icons.Filled.Build),
    LOG("Log", Icons.AutoMirrored.Filled.List),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoxApp(vm: ObdViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.dismissMessage()
        }
    }

    val connected = state.connection == ConnectionState.CONNECTED

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Fox OBD")
                        if (connected) {
                            Text(
                                listOfNotNull(state.deviceName, state.protocol).joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                actions = {
                    if (connected) {
                        IconButton(onClick = vm::disconnect) {
                            Icon(Icons.Filled.Close, contentDescription = "Trennen")
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (connected) {
                NavigationBar {
                    Tab.entries.forEachIndexed { i, t ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = { Icon(t.icon, contentDescription = null) },
                            label = { Text(t.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            if (!connected) {
                ConnectScreen(state, vm)
            } else when (Tab.entries[tab]) {
                Tab.LIVE -> LiveScreen(state, vm)
                Tab.DTC -> DtcScreen(state, vm)
                Tab.MODULES -> ModulesScreen(state, vm)
                Tab.LOG -> LogScreen(state.log)
            }
        }
    }
}
