package io.github.openquesttuner.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class GameFilter { ALL, FAVOURITES, OPTIMISED, CUSTOM, NO_PROFILE }
enum class GameSort { NAME, PROFILE }

