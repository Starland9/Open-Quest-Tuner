package io.github.openquesttuner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.github.openquesttuner.ui.OqtApp
import io.github.openquesttuner.ui.theme.OqtTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { OqtTheme { OqtApp() } }
    }
}
