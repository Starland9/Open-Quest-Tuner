# Quickstart : valider le MVP sur un casque réel

Ce guide prouve, de bout en bout, que la fonctionnalité répond à la [spec](spec.md). Il sert aussi
à remplir le tableau de compatibilité `docs/compatibility.md` (constitution, principe III).

## Prérequis

- Un Meta Quest 3, en mode développeur (compte développeur Meta), connecté au Wi-Fi.
- Un PC Linux avec le SDK Android (`~/Android/Sdk`, `adb`) et le JDK 21. Le PC ne sert qu'à
  installer l'appli, et pour la méthode « via PC ».
- Au moins deux jeux VR installés, dont un jeu de référence léger et stable, par exemple une démo
  gratuite du store.

## 1. Build et tests (sans casque)

```bash
./gradlew test            # Tests unitaires JVM du cœur : tous verts
./gradlew assembleDebug   # APK : app/build/outputs/apk/debug/app-debug.apk
```

Attendu : les deux commandes réussissent. C'est la condition d'intégration fixée par la
constitution.

## 2. Installation

```bash
adb devices                                   # le casque apparaît en "device"
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Dans le casque : Bibliothèque → Sources inconnues → OpenQuestTuner.

## 3. Scénarios de validation

Pour chaque scénario, noter ✅ ou ❌ et la version d'Horizon OS (Paramètres → Système → À propos).

### US1 : connexion

| # | Étapes | Résultat attendu | Réf. |
|---|---|---|---|
| 1.1 | Débrancher le câble. Dans OpenQuestTuner, toucher « Ouvrir les options développeur ». Si rien ne s'ouvre, passer par les paramètres du casque, dont l'emplacement varie selon la version (research.md R4). Activer « Débogage sans fil », accepter « Toujours autoriser sur ce réseau », puis « Associer avec un code ». Placer ce panneau à côté d'OpenQuestTuner, saisir port et code, toucher « Associer ». Noter dans le journal de `docs/compatibility.md` si le bouton a fonctionné. | Le panneau des options s'ouvre à côté de l'appli ; « Connecté (sans fil) » en moins de 15 s, sans autre saisie | FR-001, FR-002, FR-007, SC-001 |
| 1.2 | Recommencer avec un code faux. | Message « code refusé ou expiré » | US1-2 |
| 1.3 | Fermer complètement l'appli, puis la rouvrir. | Reconnexion seule en moins de 5 s | FR-005, SC-007 |
| 1.4 | Désactiver le débogage sans fil, rouvrir l'appli. | « Déconnecté », sans message d'erreur | US1-6 |
| 1.5 | Méthode PC : `adb tcpip 5555` depuis le PC, débrancher, puis toucher « Se connecter (via PC) » et accepter l'invite dans le casque. | « Connecté (via PC) » | FR-003 |
| 1.6 | Chronométrer 1.1 avec une personne qui découvre l'appli, sans aide. | Moins de 5 min | SC-001 |

### US2 : profil puis « Appliquer et lancer »

| # | Étapes | Résultat attendu | Réf. |
|---|---|---|---|
| 2.1 | Ouvrir la liste des jeux. | Jeux VR uniquement, avec icône et nom, triés, sans applis système ni OpenQuestTuner | FR-008 |
| 2.2 | Ouvrir le jeu de référence. | Tous les réglages sur « Par défaut du jeu » | FR-012 |
| 2.3 | Choisir 120 Hz, résolution ×1,2, CPU 4, GPU 4, fovéal moyen, fovéal dynamique désactivé, puis « Appliquer et lancer ». | Le jeu démarre en moins de 5 s (hors chargement), avec le rappel « réglages actifs » | FR-019, FR-022, SC-003 |
| 2.4 | Pendant que le jeu tourne, depuis le PC : `adb shell getprop \| grep debug.oculus`. | Les 7 valeurs correspondent au profil | SC-004 |
| 2.5 | Vérifier l'effet réel (voir section 4). | Fréquence et résolution effectivement modifiées | SC-008 |
| 2.6 | Ouvrir un 2ᵉ jeu sans profil, puis « Lancer ». | `getprop` ne montre plus aucune propriété gérée | US2-4 |
| 2.7 | Désactiver le débogage sans fil, ouvrir un profil. | Modification et enregistrement possibles, « Appliquer et lancer » désactivé et expliqué | FR-021 |
| 2.8 | Régler CPU au maximum et résolution ×1,5. | Avertissement chauffe/autonomie visible | FR-018 |
| 2.9 | Partir de l'appli fermée, avec la connexion déjà configurée et un jeu sans profil. Chronométrer depuis l'ouverture de l'appli : trouver le jeu, régler 3 valeurs, toucher « Appliquer et lancer », jusqu'au démarrage du jeu. | Moins d'une minute | SC-002 |

### US3 à US5

| # | Étapes | Résultat attendu | Réf. |
|---|---|---|---|
| 3.1 | Rechercher une partie d'un nom, en minuscules et sans accent. | Filtrage immédiat | FR-009 |
| 3.2 | Supprimer un profil et confirmer. | Indicateur retiré, réglages par défaut | FR-014 |
| 3.3 | Fermer l'appli, redémarrer le casque, rouvrir. | Profils identiques | FR-013, SC-006 |
| 4.1 | Après 2.3, « Tout réinitialiser ». | `getprop` ne montre plus de valeur non vide pour les 7 clés | FR-023, SC-005 |
| 5.1 | Ouvrir le diagnostic après 2.3, puis après 4.1. | Il affiche les valeurs du profil, puis « aucun réglage actif » | FR-024 |
| 5.2 | Passer le système en français, puis en anglais. | Tous les textes traduits | FR-029, SC-010 |

## 4. Vérifier l'effet réel d'une propriété (tableau de compatibilité)

Pour chaque propriété, lancer le jeu de référence avec la valeur à tester, puis observer :

- **Outil de référence** : OVR Metrics Tool, l'appli officielle de Meta, en overlay. Elle
  affiche la fréquence d'affichage, les FPS et les niveaux CPU/GPU.
- **Journal** : `adb logcat -s VrApi`. La ligne périodique de statistiques indique notamment les
  FPS, les niveaux CPU/GPU et le niveau fovéal. Le format exact est à confirmer lors du premier
  essai.
- **Résolution** : comparaison visuelle, en regardant la netteté d'un texte lointain, et
  capture d'écran avant/après.

Consigner chaque essai dans `docs/compatibility.md` (casque, version d'Horizon OS, jeu, valeur,
effet observé). Une propriété ne passe de « expérimental » à « vérifié » dans le catalogue
`QuestModel` qu'après un essai concluant (SC-008).

## 5. Contrôle réseau (SC-009)

Pendant une session complète (connexion, profil, lancement, réinitialisation), depuis le PC :

```bash
APP_UID=$(adb shell dumpsys package io.github.openquesttuner | grep -m1 -o 'userId=[0-9]*' | cut -d= -f2)
# Colonne 8 = uid, colonne 3 = adresse distante (hexadécimal, petit-boutiste)
adb shell "cat /proc/net/tcp /proc/net/tcp6 /proc/net/udp /proc/net/udp6" | awk -v u="$APP_UID" '$8 == u { print $3 }' | sort -u
```

Attendu : seulement des adresses locales. En TCP, `0100007F:xxxx` (127.0.0.1) ou l'équivalent
IPv6. En UDP, éventuellement le multicast mDNS `FB0000E0:14E9` (224.0.0.251:5353) pendant la
découverte du port sans fil.
