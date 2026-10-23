package io.github.openquesttuner.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.HttpURLConnection
import java.net.URL

sealed interface CatalogUpdateState {
    data object Idle : CatalogUpdateState
    data object Checking : CatalogUpdateState
    data class Available(val remoteRevision: Int, val installedRevision: Int) : CatalogUpdateState
    data class Current(val revision: Int) : CatalogUpdateState
    data class Updated(val revision: Int) : CatalogUpdateState
    data class Error(val message: String) : CatalogUpdateState
}

