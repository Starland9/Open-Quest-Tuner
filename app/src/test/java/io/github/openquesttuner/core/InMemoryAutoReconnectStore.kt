package io.github.openquesttuner.core

/** Préférences en mémoire, aux mêmes valeurs par défaut que `ConnectionPrefs` (data-model.md). */
class InMemoryAutoReconnectStore(
    override var autoReconnect: Boolean = false,
    override var rightGrantedByApp: Boolean = false,
    override var revokePending: Boolean = false,
    override var expiryOriginal: String? = null,
    override var expiryRestorePending: Boolean = false,
) : AutoReconnectStore
