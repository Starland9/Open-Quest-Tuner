package io.github.openquesttuner.core

/** Délai d'expiration par défaut du système quand la clé est absente : 7 jours. */
const val DEFAULT_KEY_LIFETIME_MS = 604_800_000L

private const val DAY_MS = 86_400_000L

/** Délai d'expiration des autorisations de débogage, tel qu'expliqué à l'utilisateur (FR-002). */
sealed interface AuthorizationLifetime {
    /** Le casque n'expire pas les autorisations. */
    data object Never : AuthorizationLifetime

    /** Valeur négative, illisible ou au-delà de 3 650 jours : l'appli n'y touche pas (FR-024). */
    data object OutOfRange : AuthorizationLifetime

    /** Nombre de jours entiers ; `0` : moins d'un jour. */
    data class Days(val days: Long) : AuthorizationLifetime
}

/** Choix « autorisations sans expiration », dérivé et jamais stocké (data-model.md). */
enum class ExpiryChoiceStatus { NOT_APPLICABLE, OFF, ON, RESTORE_PENDING }

enum class ExpiryChangeResult { APPLIED, RESTORE_PENDING, NOT_APPLICABLE, FAILED, NOT_CONNECTED }

/** Règles du choix « autorisations sans expiration » (spec 002, US4, FR-021 à FR-025). */
object ExpiryChoicePolicy {

    private const val DEFAULT = "default"

    /** data-model.md, « Durée de vie de l'autorisation ». */
    fun lifetimeOf(ms: Long?): AuthorizationLifetime = when {
        ms == null -> AuthorizationLifetime.Days(DEFAULT_KEY_LIFETIME_MS / DAY_MS)
        ms == 0L -> AuthorizationLifetime.Never
        ms in 1..ShellCommand.MAX_KEY_LIFETIME_MS -> AuthorizationLifetime.Days(ms / DAY_MS)
        else -> AuthorizationLifetime.OutOfRange
    }

    /** data-model.md, « `ExpiryChoiceStatus` ». */
    fun status(original: String?, restorePending: Boolean, currentMs: Long?): ExpiryChoiceStatus = when {
        original != null && restorePending -> ExpiryChoiceStatus.RESTORE_PENDING
        original != null -> ExpiryChoiceStatus.ON
        lifetimeOf(currentMs) is AuthorizationLifetime.Days -> ExpiryChoiceStatus.OFF
        else -> ExpiryChoiceStatus.NOT_APPLICABLE
    }

    /** Valeur à retenir avant C11 : `"default"` si la clé est absente. */
    fun encodeOriginal(currentMs: Long?): String = currentMs?.toString() ?: DEFAULT

    /**
     * C13 pour `"default"`, sinon C12 avec la valeur retenue.
     * @throws IllegalArgumentException si la valeur retenue n'est pas un délai de la plage de C12.
     */
    fun restoreCommand(original: String): ShellCommand {
        if (original == DEFAULT) return ShellCommand.resetAuthorizationExpiry()
        val ms = requireNotNull(original.toLongOrNull()) { "Valeur retenue invalide : $original" }
        return ShellCommand.restoreAuthorizationExpiry(ms)
    }
}
