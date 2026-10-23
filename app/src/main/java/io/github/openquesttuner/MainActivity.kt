package io.github.openquesttuner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import io.github.openquesttuner.ui.OqtApp
import io.github.openquesttuner.ui.theme.OqtTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container=(application as OqtApplication).container
        container.immersiveHud.host(this)
        lifecycleScope.launch { container.learningGameLaunched.collect { if(!isFinishing && !isDestroyed) moveTaskToBack(true) } }
        setContent { OqtTheme { OqtApp() } }
    }

    override fun onResume() {
        super.onResume()
        // La permission a pu être retirée depuis un PC pendant que l'appli était en arrière-plan.
        (application as OqtApplication).container.autoReconnect.refresh()
    }
}
