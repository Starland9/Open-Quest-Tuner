package io.github.openquesttuner.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

private val covers = object : LruCache<String, ImageBitmap>(16 * 1024 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
}
private val downloads = Semaphore(3)
private val failedUntil = ConcurrentHashMap<String, Long>()
private const val MAX_BYTES = 2 * 1024 * 1024
private const val DISK_BUDGET = 64L * 1024 * 1024

/** GitHub landscape URLs with cached covers and installed-banner/app-icon fallback. */
@Composable
fun GameCover(packageName: String, label: String, online: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val banner by produceState(covers.get(packageName), packageName, online) {
        value = covers.get(packageName) ?: withContext(Dispatchers.IO) {
            loadCover(context, packageName, online)
        }
    }
    Box(modifier.background(Color(0xFF17263A)), contentAlignment = Alignment.Center) {
        if (banner != null) Image(banner!!, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            GameIcon(packageName, label, size = maxHeight * .8f)
        }
    }
}

private fun decode(file: File): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    val sample = CoverArtworkSource.decodeSampleSize(bounds.outWidth, bounds.outHeight) ?: return null
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeFile(file.path, options)?.asImageBitmap()
}

private suspend fun loadCover(context: Context, packageName: String, online: Boolean): ImageBitmap? {
    val address = CoverArtworkSource.url(packageName) ?: return nativeBanner(context, packageName)
    val directory = File(context.cacheDir, "launcher-covers").apply { mkdirs() }
    val cached = File(directory, "$packageName.jpg")
    if (cached.isFile) {
        decode(cached)?.let { covers.put(packageName, it); return it }
        cached.delete()
    }
    if (!online || (failedUntil[packageName] ?: 0) > android.os.SystemClock.elapsedRealtime()) return nativeBanner(context, packageName)
    val downloaded = downloads.withPermit {
        // Another request may have completed while this one waited.
        if (cached.isFile) decode(cached)?.let { covers.put(packageName, it); return@withPermit it }
        var connection: HttpURLConnection? = null
        var temporary: File? = null
        try {
            connection = URL(address).openConnection() as HttpURLConnection
            connection.connectTimeout = 4500
            connection.readTimeout = 4500
            if (connection.responseCode != 200 || connection.contentLengthLong > MAX_BYTES || connection.contentType?.startsWith("image/") != true) return@withPermit null
            val data = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    if (data.size() + n > MAX_BYTES) return@withPermit null
                    data.write(buffer, 0, n)
                }
            }
            temporary = File.createTempFile("cover-", ".jpg", directory)
            temporary.writeBytes(data.toByteArray())
            val result = decode(temporary) ?: return@withPermit null
            if (temporary.renameTo(cached)) {
                val files = directory.listFiles()?.filter { it.extension == "jpg" && !it.name.startsWith("cover-") }?.sortedBy { it.lastModified() }.orEmpty()
                var total = files.sumOf { it.length() }
                for (file in files) {
                    if (total <= DISK_BUDGET) break
                    if (file != cached) { val length = file.length(); if (file.delete()) total -= length }
                }
            }
            failedUntil.remove(packageName)
            covers.put(packageName, result)
            result
        } catch (_: Exception) {
            null
        } finally {
            temporary?.delete()
            connection?.disconnect()
            // Failed requests are retried after five minutes, rather than on every scroll.
            if (!cached.isFile) failedUntil[packageName] = android.os.SystemClock.elapsedRealtime() + 300_000
        }
    }
    return downloaded ?: nativeBanner(context, packageName)
}

private fun nativeBanner(context: Context, packageName: String): ImageBitmap? = try {
    context.packageManager.getApplicationBanner(packageName)?.toBitmap(512, 288)?.asImageBitmap()
} catch (_: Exception) { null }
