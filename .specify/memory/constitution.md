# Constitution d'OpenQuestTuner

OpenQuestTuner est une application Android 2D open source pour casques Meta Quest, alternative
libre à Quest Games Optimizer. Elle applique des réglages par jeu (résolution de rendu, fréquence
d'affichage, niveaux CPU/GPU, rendu fovéal…) via les propriétés système `debug.oculus.*`, grâce à
un client ADB embarqué dans l'appli.

## Principes fondamentaux

### I. Sécurité du casque d'abord (NON NÉGOCIABLE)

- L'appli NE DOIT exécuter que des commandes shell qu'elle construit elle-même à partir de valeurs
  validées : liste blanche de propriétés, entiers bornés par des plages connues, noms de paquets et
  d'activités vérifiés puis échappés.
- Aucun texte libre saisi par l'utilisateur et aucune donnée importée (fichier, profil partagé) NE
  DOIT atteindre le shell sans passer par cette validation. En conséquence, l'appli n'offre pas de
  console shell libre.
- La clé privée ADB DOIT rester dans le stockage privé de l'appli : elle n'est jamais exportée,
  journalisée ni transmise.
- Tout réglage de performance (propriétés `debug.oculus.*`) DOIT être réversible. Une action en un
  geste remet toutes les propriétés gérées à leur valeur par défaut, et un redémarrage du casque
  les efface toutes. L'appli n'écrit jamais de propriété persistante (`persist.*`).
- Un changement durable de l'accès au débogage, qui survit au redémarrage du casque, NE DOIT être
  fait qu'à trois conditions. Exemples : une permission que l'appli s'accorde à elle-même, ou le
  délai d'expiration des autorisations de débogage.
  1. L'utilisateur l'a choisi explicitement, après une explication de ses conséquences. Rien
     n'est coché ni activé d'office. L'explication dit aussi ce qui resterait en place si l'appli
     était désinstallée.
  2. Un seul geste dans l'appli le défait.
  3. L'appli retient la valeur d'origine et la rétablit à ce geste. Elle ne rétablit que ce
     qu'elle a elle-même changé.

  Ces changements forment une liste fermée, fixée par le contrat des commandes de la
  fonctionnalité qui les introduit, avec des cibles et des valeurs constantes ou validées.

Justification : un accès shell ADB permet de dégrader ou de bloquer un casque. La confiance des
utilisateurs repose sur la garantie qu'aucune entrée ne peut détourner ce shell et qu'aucun
réglage n'est irréversible. Certains usages exigent pourtant qu'un accès survive au redémarrage :
se reconnecter sans PC, par exemple. Il reste alors sous le contrôle de l'utilisateur, qui
choisit, voit ce qui change et peut revenir en arrière.

### II. Libre, clean-room et respectueux de la vie privée

- Le projet est distribué sous licence GPL-3.0. Toute dépendance DOIT avoir une licence compatible.
- Aucun code, ressource, binaire, texte ni élément de marque de Quest Games Optimizer NE DOIT être
  copié ou décompilé. L'implémentation s'appuie uniquement sur de la documentation publique et sur
  nos propres essais.
- L'appli NE DOIT contenir ni télémétrie, ni compte, ni publicité, ni traqueur.
- L'appli DOIT fonctionner entièrement hors ligne. Sa seule communication réseau est la connexion
  ADB vers le casque lui-même (localhost et découverte mDNS du service ADB local). Toute
  fonctionnalité réseau future, comme des profils communautaires, DOIT être désactivée par défaut
  et activée explicitement par l'utilisateur.

Justification : le projet existe pour offrir une alternative libre et digne de confiance. Une
reprise de QGO exposerait le projet juridiquement ; une collecte de données trahirait sa raison
d'être.

### III. Vérifié sur casque réel

- Chaque propriété `debug.oculus.*` et chaque plage de valeurs exposée DOIT être vérifiée sur un
  casque réel (Quest 3 en priorité) avant d'être présentée comme stable.
- Chaque vérification DOIT être consignée dans le tableau de compatibilité : casque, version
  d'Horizon OS, valeur testée, effet observé.
- Tout réglage non vérifié pour le casque détecté DOIT être marqué « expérimental » dans
  l'interface.

Justification : ces propriétés ne sont pas documentées par Meta et leur comportement change selon
les casques et les versions d'Horizon OS. Seul un test réel dit si un réglage fonctionne.

### IV. Cœur testable sans casque

- Le code DOIT être séparé en deux couches : un cœur en Kotlin pur, sans dépendance Android
  (modèles de profils, validation, génération des commandes, parsing des sorties shell), et une
  couche Android/ADB mince.
- Tout accès shell DOIT passer par l'interface `ShellBackend`. Le cœur ignore l'implémentation :
  ADB embarqué aujourd'hui, Shizuku possible demain.
- Chaque règle métier du cœur DOIT être couverte par des tests unitaires JVM, exécutables sans
  casque ni émulateur.

Justification : le casque n'est pas toujours disponible pour tester. Un cœur pur et testé permet de
garantir le principe I (commandes sûres) à chaque modification.

### V. Simplicité et UX pensée pour la VR

- Le projet reste un seul module Gradle `app` tant qu'aucun besoin concret n'impose un découpage.
- Chaque nouvelle dépendance DOIT être justifiée dans le plan de la fonctionnalité. Pas
  d'abstraction spéculative ; seule l'interface `ShellBackend` est imposée (principe IV).
- L'interface DOIT être utilisable dans un panneau 2D du casque, aux contrôleurs comme aux mains :
  cibles d'interaction d'au moins 48 dp, texte lisible, aucun besoin de clavier physique.
- Après la première configuration ADB, toutes les fonctionnalités DOIVENT être utilisables sans PC.

Justification : l'utilisateur est dans son casque quand il utilise l'appli. Chaque couche ou
dépendance superflue ralentit un projet maintenu par peu de personnes.

## Contraintes techniques

- Langage et interface : Kotlin et Jetpack Compose, minSdk 29.
- ADB : libadb-android, pour l'ADB TCP classique (port 5555), l'appairage du débogage sans fil
  (TLS) et la découverte mDNS.
- Persistance : fichiers JSON locaux dans le stockage privé de l'appli.
- Casques cibles : Quest 3 (prioritaire), Quest 3S, Quest 2, Quest Pro.
- Changer l'un de ces choix DOIT être justifié dans le plan concerné et reporté ici par un
  amendement MINOR.

## Workflow de développement

- Chaque fonctionnalité suit le cycle Spec Kit : specify → clarify (si besoin) → plan → tasks →
  implement.
- Chaque plan DOIT remplir la section « Constitution Check ». Toute dérogation DOIT être justifiée
  dans « Complexity Tracking ».
- Avant toute intégration dans la branche principale, les tests unitaires (`./gradlew test`) DOIVENT
  passer et le build `./gradlew assembleDebug` DOIT réussir.
- Toute nouvelle propriété `debug.oculus.*` DOIT être ajoutée au tableau de compatibilité avant
  d'être exposée dans l'interface.

## Gouvernance

- Cette constitution prime sur toute autre pratique du projet. En cas de conflit, elle l'emporte,
  ou bien elle est amendée.
- Un amendement est proposé par pull request. Il contient sa justification, son impact sur les
  specs et plans existants, et un rapport d'impact mis à jour.
- Versionnement semver : MAJOR pour la suppression ou la redéfinition incompatible d'un principe ;
  MINOR pour l'ajout ou l'élargissement d'un principe ou d'une section ; PATCH pour une
  clarification sans changement de sens.
- Conformité : chaque plan (Constitution Check) et chaque revue de pull request vérifient le
  respect des principes.

**Version** : 1.1.0 | **Ratifiée** : 2026-09-23 | **Dernier amendement** : 2026-09-24
