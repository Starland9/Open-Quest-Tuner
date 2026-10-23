package io.github.openquesttuner.persistence

import io.github.openquesttuner.core.*
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.HttpURLConnection
import java.net.URL

class CatalogUpdater(context: Context, private val store: CommunityProfileCatalogStore) {
    private val prefs = context.getSharedPreferences("catalog_update_v1", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow<CatalogUpdateState>(CatalogUpdateState.Idle)
    val state: StateFlow<CatalogUpdateState> = _state.asStateFlow()
    var sourceUrl: String
        get() = prefs.getString("source_url", "").orEmpty()
        set(value) { prefs.edit().putString("source_url", value.trim()).apply() }
    val lastCheckedMs: Long get() = prefs.getLong("last_checked", 0L)

    suspend fun checkAndUpdate(url: String = sourceUrl, install: Boolean): Result<CommunityCatalog> = withContext(Dispatchers.IO) {
        runCatching {
            require(url.startsWith("https://")) { "Catalogue source must use HTTPS" }
            _state.value = CatalogUpdateState.Checking
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000; readTimeout = 10_000; requestMethod = "GET"
                prefs.getString("etag", null)?.let { setRequestProperty("If-None-Match", it) }
            }
            try {
                val code = connection.responseCode
                prefs.edit().putLong("last_checked", System.currentTimeMillis()).apply()
                if (code == HttpURLConnection.HTTP_NOT_MODIFIED) {
                    val current = store.catalog.value; _state.value = CatalogUpdateState.Current(current.catalogRevision); return@runCatching current
                }
                require(code in 200..299) { "Catalogue server returned HTTP $code" }
                val text = connection.inputStream.bufferedReader().use { it.readText() }
                val remote = store.decodeAndValidate(text)
                val current = store.catalog.value
                if (remote.catalogRevision <= current.catalogRevision) {
                    _state.value = CatalogUpdateState.Current(current.catalogRevision); current
                } else if (!install) {
                    _state.value = CatalogUpdateState.Available(remote.catalogRevision, current.catalogRevision); remote
                } else {
                    val installed = store.import(text).getOrThrow()
                    connection.getHeaderField("ETag")?.let { prefs.edit().putString("etag", it).apply() }
                    _state.value = CatalogUpdateState.Updated(installed.catalogRevision); installed
                }
            } finally { connection.disconnect() }
        }.onFailure { _state.value = CatalogUpdateState.Error(it.message ?: "Catalogue update failed") }
    }
}
