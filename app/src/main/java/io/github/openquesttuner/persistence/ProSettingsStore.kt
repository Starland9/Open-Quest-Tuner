package io.github.openquesttuner.persistence

import io.github.openquesttuner.core.*
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class ProSettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("pro_settings_v3", Context.MODE_PRIVATE)
    private val _autoApply = MutableStateFlow(prefs.getBoolean("auto_apply", true))
    val autoApply = _autoApply.asStateFlow()
    private val _filter = MutableStateFlow(runCatching { GameFilter.valueOf(prefs.getString("filter", "ALL")!!) }.getOrDefault(GameFilter.ALL))
    val filter = _filter.asStateFlow()
    private val _sort = MutableStateFlow(runCatching { GameSort.valueOf(prefs.getString("sort", "NAME")!!) }.getOrDefault(GameSort.NAME))
    val sort = _sort.asStateFlow()
    fun setAutoApply(value: Boolean) { prefs.edit().putBoolean("auto_apply", value).apply(); _autoApply.value = value }
    fun setFilter(value: GameFilter) { prefs.edit().putString("filter", value.name).apply(); _filter.value = value }
    fun setSort(value: GameSort) { prefs.edit().putString("sort", value.name).apply(); _sort.value = value }
}
