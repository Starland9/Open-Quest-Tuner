package io.github.openquesttuner.core

/**
 * Choix persistés de la reconnexion autonome (data-model.md, « Option persistée »). Ils survivent
 * au redémarrage et aux mises à jour, et disparaissent à la désinstallation.
 */
interface AutoReconnectStore {

    /** Choix de l'utilisateur : option active ou non (FR-012). Défaut `false`. */
    var autoReconnect: Boolean

    /**
     * C9 l'a fait passer d'absente à détenue : seule situation où l'appli a le droit de retirer
     * la permission (constitution, principe I, condition 3 ; FR-013). Défaut `false`.
     */
    var rightGrantedByApp: Boolean

    /** Option désactivée hors connexion : retirer la permission à la prochaine connexion. Défaut `false`. */
    var revokePending: Boolean

    /**
     * Délai d'expiration d'origine, retenu par le choix « sans expiration » (FR-023) : `"default"`
     * (clé absente sur le casque) ou un nombre de millisecondes. `null` : le choix n'a rien changé.
     */
    var expiryOriginal: String?

    /** Choix désactivé hors connexion : rétablir le délai à la prochaine connexion. Défaut `false`. */
    var expiryRestorePending: Boolean
}
