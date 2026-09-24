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

    override fun onResume() {
        super.onResume()
        // La permission a pu être retirée depuis un PC pendant que l'appli était en arrière-plan.
        (application as OqtApplication).container.autoReconnect.refresh()
    }
}
