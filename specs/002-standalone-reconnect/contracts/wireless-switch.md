# Contrat : `WirelessDebuggingSwitch`

Interface du cœur (`core/WirelessDebuggingSwitch.kt`), implémentée côté Android par
`adb/AndroidWirelessSwitch.kt` et simulée dans les tests (`FakeWirelessDebuggingSwitch`). Elle
donne à `AutoReconnectPolicy` les lectures dont il a besoin, et **la seule écriture de réglage
système** de toute l'appli.

## Opérations

| Opération | Retour | Implémentation Android | Erreurs |
|---|---|---|---|
| `hasWriteRight()` | `Boolean` | `checkSelfPermission(WRITE_SECURE_SETTINGS) == PERMISSION_GRANTED` | aucune |
| `isDebuggingEnabled()` | `Boolean` | `Settings.Global.ADB_ENABLED` vaut 1 (constante publique) | illisible → `true`, pour ne pas bloquer à tort : la connexion dira ensuite ce qui manque |
| `isOnWifi()` | `Boolean` | réseau actif avec `TRANSPORT_WIFI` (`ConnectivityManager`, permission `ACCESS_NETWORK_STATE` déjà déclarée) | aucune |
| `isWirelessDebuggingEnabled()` | `Boolean` | `Settings.Global` `"adb_wifi_enabled"` vaut 1 (clé `@Readable`, research.md R2) | exception inattendue → `false` |
| `authorizationLifetimeMs()` | `Long?` | `Settings.Global` `"adb_allowed_connection_time"` (clé `@Readable`) ; `0` = jamais | `null` si la clé est absente : délai par défaut du système, 7 jours. Valeur non numérique : `-1`, donc hors plage, et le choix n'est pas proposé (FR-024). Lecture seule par l'API ; seules les commandes shell C11 à C13 le modifient (research.md R6). |
| `enableWirelessDebugging()` | `Unit` | `Settings.Global.putInt(cr, "adb_wifi_enabled", 1)` | `SecurityException` sans la permission ; elle est remontée à la politique, qui renvoie `RIGHT_LOST` |

Tous les appels sont synchrones et rapides : ce sont des lectures locales et une écriture de
réglage. Aucun ne touche au réseau.

## Règle d'écriture unique (FR-015, principe I)

- `enableWirelessDebugging()` est **le seul endroit** du code qui écrit un réglage système. Il
  n'écrit qu'une clé (`adb_wifi_enabled`), et une seule valeur (`1`).
- L'appli n'écrit jamais `0` : elle ne coupe pas le débogage sans fil (hors périmètre, et FR-014).
- Elle n'écrit jamais par l'API `adb_allowed_connection_time` : elle le lit seulement, et ne le
  modifie que par les commandes shell C11 à C13, sur choix explicite (FR-021). Elle n'écrit
  jamais la liste des clés autorisées, ni aucun autre réglage sécurisé.
- **Garde** : un test JVM parcourt `app/src/main/java`. Il échoue si `Settings.Global.put`,
  `Settings.Secure.put` ou `Settings.System.put` apparaît ailleurs que dans
  `AndroidWirelessSwitch.kt`, ou plus d'une fois dans ce fichier.

## Surveillance du Wi-Fi (FR-010)

Elle ne fait pas partie de l'interface, parce que la politique n'en a pas besoin. C'est
`AutoReconnectController` qui s'abonne aux réseaux Wi-Fi (`registerNetworkCallback`, filtre
`TRANSPORT_WIFI`) pendant toute la vie du processus. À l'arrivée d'un réseau, si la dernière
cause est `NO_WIFI` et que l'appli est toujours déconnectée, il relance `reconnectLast()` après
2 s : le réseau annoncé ne devient le réseau actif, celui que lit `isOnWifi()`, qu'un instant plus
tard. Une seule relance peut tourner à la fois.
