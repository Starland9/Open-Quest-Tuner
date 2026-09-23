package io.github.openquesttuner.ui.components

import android.content.pm.PackageManager
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val ICON_PX = 96

/** Icônes déjà chargées, pour que le défilement de la liste ne relise pas le PackageManager. */
private val iconCache = LruCache<String, ImageBitmap>(200)

/** Icône 48dp du jeu ; initiale du nom tant qu'elle n'est pas chargée, ou si elle est introuvable. */
@Composable
fun GameIcon(packageName: String, label: String, modifier: Modifier = Modifier) {
    val pm = LocalContext.current.packageManager
    val icon by produceState(initialValue = iconCache.get(packageName), packageName) {
        if (value == null) value = withContext(Dispatchers.IO) { loadIcon(pm, packageName) }
    }
    Box(modifier.size(48.dp), contentAlignment = Alignment.Center) {
        val bitmap = icon
        if (bitmap != null) {
            Image(bitmap, contentDescription = null, modifier = Modifier.fillMaxSize())
        } else {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.medium,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(label.take(1).uppercase(), style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
}

private fun loadIcon(pm: PackageManager, packageName: String): ImageBitmap? = try {
    pm.getApplicationIcon(packageName).toBitmap(ICON_PX, ICON_PX).asImageBitmap()
        .also { iconCache.put(packageName, it) }
} catch (e: PackageManager.NameNotFoundException) {
    null
}
