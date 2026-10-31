package de.foxobd

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import de.foxobd.ui.FoxApp
import de.foxobd.ui.FoxTheme

class MainActivity : ComponentActivity() {
    private val viewModel: ObdViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FoxTheme {
                FoxApp(viewModel)
            }
        }
    }
}
