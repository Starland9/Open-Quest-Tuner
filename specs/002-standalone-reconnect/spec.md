# Feature Specification: Reconnexion autonome après un redémarrage (sans PC)

**Feature Branch**: `002-standalone-reconnect` (aucune branche créée : travail sur `main`)

**Created**: 2026-09-24

**Status**: Draft (amendée le 2026-09-24 après la recherche du plan : User Story 4, FR-021 à FR-025, FR-015 assouplie, à la demande de l'utilisateur)

**Input**: User description: "actu j aio deux issues un qui demande si c est possible de faire une connexion standalone sans tcpip a chaque fois et une autre qui se demande si on peux monter a 144hz" — cette spec couvre la première issue (connexion autonome, sans refaire `adb tcpip` à chaque fois). La seconde (144 Hz) fera l'objet d'une demande distincte.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Se reconnecter sans PC après un redémarrage (Priority: P1)

Aujourd'hui, un redémarrage du casque coupe le débogage sans fil. Si le port de débogage réseau
ouvert depuis le PC s'est refermé lui aussi, l'appli ne peut plus se reconnecter : l'utilisateur
doit rebrancher son casque à un PC et relancer la commande à chaque redémarrage. C'est ce que
signale l'issue.

Désormais, une fois connecté (via PC ou sans fil), l'utilisateur active l'option
« Reconnexion autonome » dans l'écran de connexion. L'appli lui explique ce que cela implique, il
confirme. Ensuite, après chaque redémarrage du casque, il ouvre l'appli : elle réactive d'elle-même
le débogage sans fil et se reconnecte, sans PC et sans aucun geste. Le PC ne sert plus qu'une
seule fois, pour la toute première autorisation.

**Why this priority**: c'est la demande de l'issue, et la dernière étape qui empêchait d'utiliser
l'appli sans PC au quotidien (constitution, principe V : après la première configuration, tout
doit être utilisable sans PC).

**Independent Test**: sur un Quest 3 déjà autorisé :
1. activer « Reconnexion autonome » et confirmer ;
2. redémarrer le casque ;
3. ouvrir l'appli : l'état passe à « Connecté (sans fil) » sans aucune action et sans PC.

Sur le Quest 3 de test, le port réseau reste ouvert après un redémarrage : l'essai vérifie donc que
la méthode affichée est bien « sans fil », et non « via PC ».

**Acceptance Scenarios**:

1. **Given** l'appli connectée (via PC ou sans fil) et l'option inactive, **When** l'utilisateur
   active « Reconnexion autonome » puis confirme après l'explication, **Then** l'appli confirme
   que l'option est active.
2. **Given** l'option active, et le casque redémarré puis connecté à un Wi-Fi déjà autorisé,
   **When** l'utilisateur ouvre l'appli, **Then** elle réactive le débogage sans fil et passe à
   « Connecté (sans fil) », sans aucune action ni PC.
3. **Given** l'option active et le débogage sans fil encore actif, **When** l'utilisateur ouvre
   l'appli, **Then** elle se reconnecte comme aujourd'hui, sans rien réactiver.
4. **Given** l'option active et le casque sur un Wi-Fi jamais autorisé, **When** l'appli réactive
   le débogage sans fil, **Then** elle explique qu'il faut accepter la fenêtre d'Horizon OS
   « autoriser sur ce réseau », et se connecte dès que l'utilisateur l'accepte.
5. **Given** l'appli connectée via PC et l'option inactive, **When** « Passer en sans fil »
   réussit, **Then** l'appli propose d'activer la reconnexion autonome, sans l'activer d'office.
6. **Given** l'option active et l'appli déconnectée en cours d'utilisation, **When**
   l'utilisateur relance la connexion depuis l'écran de connexion, **Then** l'appli se comporte
   comme à l'ouverture : elle réactive le débogage sans fil si besoin, puis se connecte.
7. **Given** l'option inactive, **When** le casque redémarre et que l'utilisateur ouvre l'appli,
   **Then** l'appli se comporte comme aujourd'hui : elle ne réactive jamais le débogage sans fil
   d'elle-même.

---

### User Story 2 - Comprendre pourquoi la reconnexion autonome n'a pas abouti (Priority: P2)

La reconnexion autonome peut échouer pour des raisons que l'appli ne peut pas régler seule : pas
de Wi-Fi, réseau refusé dans la fenêtre d'Horizon OS, autorisation retirée par le casque, droit de
réactivation perdu. L'utilisateur voit la cause probable et la seule étape à faire. Il sait en
particulier si le PC est vraiment nécessaire, ou non.

**Why this priority**: sans explication, un échec renvoie l'utilisateur vers le PC alors que,
le plus souvent, il suffit d'activer le Wi-Fi ou d'accepter une fenêtre.

**Independent Test**: option active, provoquer chaque cause (Wi-Fi coupé, fenêtre réseau refusée,
autorisations de débogage révoquées depuis les paramètres du casque, droit retiré depuis un PC),
ouvrir l'appli, puis vérifier que la cause et l'étape affichées correspondent.

**Acceptance Scenarios**:

1. **Given** l'option active et le casque sans Wi-Fi, **When** l'utilisateur ouvre l'appli,
   **Then** l'état est « Déconnecté » avec l'invitation à connecter le casque à un Wi-Fi. Dès que
   le casque rejoint un réseau, pendant que l'appli est ouverte, elle réessaie seule.
2. **Given** l'option active et la fenêtre « autoriser sur ce réseau » refusée, ou ignorée plus de
   60 secondes, **When** la tentative se termine, **Then** l'état est « Déconnecté », avec la
   marche à suivre pour réessayer.
3. **Given** l'option active et une autorisation de débogage que le casque ne reconnaît plus
   (révoquée ou expirée), **When** la connexion est refusée, **Then** l'appli explique qu'il faut
   refaire une fois l'autorisation via PC et affiche les étapes.
4. **Given** l'option active mais un droit de réactivation perdu (retiré depuis un PC, ou par une
   mise à jour d'Horizon OS), **When** l'utilisateur ouvre l'appli, **Then** l'option est
   affichée « à réactiver », l'appli se reconnecte par une autre méthode si l'une est disponible,
   et une fois connectée l'utilisateur la réactive en un geste.
5. **Given** un échec de la reconnexion autonome, quelle qu'en soit la cause, **When**
   l'utilisateur regarde l'écran, **Then** il voit « Déconnecté » accompagné de la cause, sans
   message d'erreur alarmant ni fenêtre bloquante.

---

### User Story 3 - Désactiver la reconnexion autonome (Priority: P3)

L'utilisateur qui ne veut plus que l'appli réactive le débogage sans fil désactive l'option en un
geste. L'appli renonce au droit qu'elle avait obtenu.

**Why this priority**: l'option donne à l'appli un droit système durable. Pouvoir le retirer est
une exigence de sécurité (constitution, principe I), mais c'est un parcours rare.

**Independent Test**: option active et appli connectée, désactiver l'option, redémarrer le casque,
ouvrir l'appli : le débogage sans fil n'a pas été réactivé et l'appli ne détient plus le droit.

**Acceptance Scenarios**:

1. **Given** l'option active et l'appli connectée, **When** l'utilisateur la désactive, **Then**
   l'appli cesse toute réactivation, renonce au droit obtenu et le confirme. La connexion en cours
   n'est pas coupée.
2. **Given** l'option active et l'appli déconnectée, **When** l'utilisateur la désactive,
   **Then** l'appli cesse aussitôt toute réactivation, et indique qu'elle renoncera au droit à la
   prochaine connexion.
3. **Given** l'option désactivée, **When** le casque redémarre et que l'utilisateur ouvre
   l'appli, **Then** le débogage sans fil n'est pas réactivé.

---

### User Story 4 - Garder l'autorisation valide sans jamais repasser par le PC (Priority: P2)

*Ajoutée le 2026-09-24. La recherche du plan (research.md R6) montre qu'une connexion sans fil ne
prolonge pas une autorisation de débogage. Sur un casque où les autorisations expirent (7 jours
par défaut dans Android), il faudrait donc refaire l'étape PC chaque semaine. L'utilisateur a
choisi de proposer d'empêcher cette expiration.*

Quand il active la reconnexion autonome sur un casque où les autorisations de débogage expirent,
l'utilisateur voit au bout de combien de jours il devrait repasser par le PC. Il peut alors
cocher, en plus, « Ne jamais faire expirer les autorisations de débogage ». L'appli retient la
valeur d'origine et la rétablit quand il revient sur ce choix.

**Why this priority**: sans ce choix, la User Story 1 ne tient qu'une semaine sur ces casques.
Mais c'est un compromis de sécurité, et il est sans objet sur les casques dont les autorisations
n'expirent pas, comme le Quest 3 de test.

**Independent Test**: via le PC, fixer un délai d'expiration de 7 jours sur le casque. Activer la
reconnexion autonome en cochant le choix : les autorisations n'expirent plus. Décocher : le
délai redevient 7 jours.

**Acceptance Scenarios**:

1. **Given** un casque dont les autorisations expirent, **When** l'utilisateur active la
   reconnexion autonome, **Then** l'explication indique le délai, et propose un choix distinct,
   non coché, « Ne jamais faire expirer les autorisations de débogage », avec ses conséquences.
2. **Given** ce choix coché et confirmé, l'appli connectée, **When** l'activation se termine,
   **Then** le casque ne fait plus expirer les autorisations, et l'appli le confirme.
3. **Given** l'option active sans ce choix, l'appli connectée, **When** l'utilisateur l'active
   depuis la carte « Reconnexion autonome » et confirme, **Then** le résultat est le même qu'au
   scénario 2.
4. **Given** le choix actif et l'appli connectée, **When** l'utilisateur le désactive, ou
   désactive la reconnexion autonome, **Then** le délai d'origine est rétabli.
5. **Given** le choix actif et l'appli déconnectée, **When** l'utilisateur le désactive, **Then**
   l'appli indique que le délai d'origine sera rétabli à la prochaine connexion, et le fait.
6. **Given** un casque dont les autorisations n'expirent déjà pas, **When** l'utilisateur active
   la reconnexion autonome, **Then** le choix n'est pas proposé, et ce réglage n'est pas modifié.

---

### Edge Cases

- **Appli ouverte juste après le redémarrage, avant que le casque ait rejoint le Wi-Fi** : l'appli
  affiche « Déconnecté », invite à attendre le Wi-Fi, et réessaie seule dès qu'il est là (FR-010).
- **Port réseau PC resté ouvert** (comme sur le Quest 3 de test) : l'ordre de reconnexion actuel
  est conservé (dernière méthode réussie d'abord). Si c'était le sans-fil, l'appli le réactive
  sans attendre l'échec de la découverte.
- **Changement de point d'accès sur un même réseau** : la fenêtre « autoriser sur ce réseau » peut
  réapparaître (constaté après une mise en veille). Tant qu'elle est ouverte, aucune appli ne se
  lance : l'appli explique qu'il faut l'accepter.
- **Débogage sans fil coupé par un autre moyen pendant que l'appli est ouverte** : la connexion
  est perdue. Tant que l'option est active, l'appli le réactive à la prochaine tentative de
  connexion ; pour l'éviter, l'utilisateur désactive l'option.
- **Casque qui refuse d'accorder le droit** (version d'Horizon OS future, par exemple) : l'appli
  indique que l'option n'est pas disponible sur ce casque. Le parcours actuel (« Passer en sans
  fil » après une connexion via PC) reste disponible.
- **Droit accordé, mais le débogage sans fil ne s'active pas** : au bout de 60 secondes, l'appli
  abandonne et affiche la cause « le casque n'a pas activé le débogage sans fil ».
- **Mise à jour de l'appli** : l'option, le droit et l'autorisation de débogage sont conservés.
- **Désinstallation de l'appli** : l'option, le droit et l'autorisation de l'appli sont perdus. À
  la réinstallation, tout recommence par la première autorisation via PC.
- **Mode développeur désactivé** : aucune connexion n'est possible ; l'appli l'indique.
- **Option désactivée hors connexion, puis appli désinstallée avant toute reconnexion** : le droit
  disparaît avec la désinstallation, rien ne subsiste.
- **Appli désinstallée alors que « Ne jamais faire expirer » est actif** : le réglage reste en
  place, puisque l'appli n'est plus là pour le rétablir. L'explication le dit (FR-022), et le
  README donne la commande pour le rétablir depuis un PC.
- **Délai d'expiration modifié par un autre outil** (un PC, par exemple) pendant que le choix est
  actif : au moment de rétablir, l'appli constate que le réglage ne vaut plus « jamais ». Elle n'y
  touche pas et oublie la valeur retenue (FR-023).
- **Autorisation déjà expirée avant l'activation du choix** : il est trop tard pour elle, il faut
  refaire une fois l'étape PC. Le choix ne vaut que pour la suite.

## Requirements *(mandatory)*

### Functional Requirements

**Activation**

- **FR-001**: Quand elle est connectée, par n'importe quelle méthode, l'appli DOIT permettre
  d'activer l'option « Reconnexion autonome » depuis l'écran de connexion.
- **FR-002**: Avant d'activer l'option, l'appli DOIT expliquer en langage simple :
  - ce que fait l'option : réactiver seule le débogage sans fil quand elle s'ouvre ;
  - qu'elle obtient pour cela un droit système du casque, qu'elle garde jusqu'à la désactivation
    de l'option ou jusqu'à sa désinstallation ;
  - qu'elle n'utilise ce droit que pour cette seule action ;
  - qu'un réseau Wi-Fi est nécessaire ;
  - si les autorisations de débogage expirent sur ce casque, au bout de combien de jours sans
    connexion via PC (User Story 4).

  L'option n'est activée qu'après une confirmation explicite de l'utilisateur.
- **FR-003**: Après un « Passer en sans fil » réussi, si l'option est inactive, l'appli DOIT
  proposer de l'activer, sans l'activer d'office.
- **FR-004**: Après l'activation, l'appli DOIT vérifier qu'elle détient bien le droit. Sinon, elle
  indique que l'option n'est pas disponible sur ce casque et la laisse inactive.

**Reconnexion**

- **FR-005**: Quand l'option est active et que l'appli n'est pas connectée, au démarrage de
  l'appli comme à chaque connexion demandée par l'utilisateur, l'appli DOIT réactiver elle-même
  le débogage sans fil s'il est coupé, puis s'y connecter avec l'autorisation qu'elle possède
  déjà. Cela se fait sans PC, sans code d'appairage, et sans autre action de l'utilisateur que
  l'éventuelle fenêtre d'Horizon OS.
- **FR-006**: L'ordre de reconnexion actuel DOIT être conservé : la dernière méthode réussie
  d'abord, puis l'autre. La réactivation de FR-005 a lieu quand vient le tour de la méthode sans
  fil, avant de chercher à s'y connecter.
- **FR-007**: Si Horizon OS demande d'autoriser le réseau, l'appli DOIT expliquer qu'il faut
  accepter la fenêtre, et attendre la réponse jusqu'à 60 secondes, comme pour « Passer en sans
  fil ».
- **FR-008**: L'appli NE DOIT réactiver le débogage sans fil qu'à son démarrage, sur une demande
  de connexion de l'utilisateur, ou lors de la nouvelle tentative de FR-010. Jamais au démarrage
  du casque, ni quand l'appli est fermée.
- **FR-009**: Si la reconnexion échoue, l'état DOIT être « Déconnecté », accompagné de la cause
  probable et de l'étape suivante, sans message d'erreur alarmant. L'appli distingue au moins les
  causes suivantes :
  - le casque n'est pas connecté à un Wi-Fi ;
  - le réseau n'a pas été autorisé (fenêtre refusée ou ignorée) ;
  - le casque ne reconnaît plus l'autorisation de l'appli : il faut refaire une fois
    l'autorisation via PC ;
  - le droit de réactivation a été perdu : l'option passe à « à réactiver » ;
  - le casque n'a pas activé le débogage sans fil, ou cause indéterminée : l'appli propose de
    réessayer, ou de passer par le PC.
- **FR-010**: Tant que l'appli est ouverte et déconnectée faute de Wi-Fi, elle DOIT retenter la
  reconnexion d'elle-même dès que le casque rejoint un réseau Wi-Fi.

**État et désactivation**

- **FR-011**: L'écran de connexion DOIT afficher l'état de l'option : « active », « inactive » ou
  « à réactiver » (droit perdu). L'état « à réactiver » est accompagné de la marche à suivre.
- **FR-012**: L'état de l'option DOIT être conservé après la fermeture de l'appli, un redémarrage
  du casque et une mise à jour de l'appli.
- **FR-013**: L'utilisateur DOIT pouvoir désactiver l'option en un geste. L'appli cesse aussitôt
  toute réactivation. Si elle est connectée, elle renonce aussi au droit obtenu ; sinon, elle y
  renonce à la prochaine connexion et l'indique.
- **FR-014**: Désactiver l'option NE DOIT couper ni la connexion en cours, ni le débogage sans fil
  déjà actif.

**Sécurité et vie privée** (constitution, principes I, II et III)

- **FR-015**: Le droit obtenu NE DOIT servir qu'à activer le débogage sans fil. L'appli NE DOIT
  modifier aucun autre paramètre système, à une seule exception : la durée de validité des
  autorisations de débogage, sur le choix explicite de FR-021, et seulement vers « jamais » ou
  vers sa valeur d'origine. Elle ne touche jamais à la liste des appareils autorisés.
- **FR-016**: L'appli NE DOIT obtenir ce droit qu'après la confirmation de FR-002 : jamais en
  silence, ni au détour d'une autre action.
- **FR-017**: La reconnexion autonome NE DOIT ajouter aucune communication réseau : elle reste une
  connexion locale de l'appli au casque lui-même.
- **FR-018**: L'option DOIT être marquée « expérimental » sur les modèles de casque où elle n'a
  pas été vérifiée. Chaque vérification est consignée dans le tableau de compatibilité.

**Interface**

- **FR-019**: Les instructions de connexion affichées dans l'appli DOIVENT présenter le parcours
  complet : une autorisation via PC, une seule fois, puis la reconnexion autonome.
- **FR-020**: Tous les nouveaux textes DOIVENT exister en français et en anglais, et toutes les
  nouvelles cibles d'interaction DOIVENT mesurer au moins 48 dp.

**Expiration des autorisations** (User Story 4, ajoutée le 2026-09-24)

- **FR-021**: Quand les autorisations de débogage du casque expirent (délai fixé, ou délai par
  défaut du système), l'appli DOIT proposer, en plus de la reconnexion autonome et par un choix
  distinct, de ne jamais les faire expirer. Ce choix n'est jamais coché d'office.
- **FR-022**: L'explication de ce choix DOIT dire, en langage simple :
  - que sans lui, il faudra refaire l'étape PC au bout de N jours sans connexion via PC ;
  - qu'il vaut pour toutes les autorisations de débogage du casque, celle du PC comprise ;
  - qu'il reste en place après un redémarrage ;
  - qu'il reste aussi en place si l'appli est désinstallée sans qu'il ait été désactivé.
- **FR-023**: L'appli DOIT retenir la valeur d'origine du délai. Elle DOIT la rétablir quand
  l'utilisateur désactive ce choix ou la reconnexion autonome ; si elle n'est pas connectée, elle
  le fait à la prochaine connexion et l'indique. Elle ne rétablit la valeur que si le réglage vaut
  encore « jamais » ; sinon, elle n'y touche pas et oublie la valeur retenue.
- **FR-024**: Si les autorisations n'expirent déjà pas sur le casque, l'appli NE DOIT ni proposer
  ce choix, ni modifier ce réglage.
- **FR-025**: La carte « Reconnexion autonome » DOIT afficher l'état de ce choix (actif, inactif,
  rétablissement en attente) et permettre de le changer en un geste, avec confirmation, quand
  l'appli est connectée.

### Key Entities *(include if feature involves data)*

- **Option « Reconnexion autonome »** : le choix de l'utilisateur, conservé localement. Attributs :
  active ou inactive ; renoncement au droit en attente (option désactivée hors connexion).
- **Droit de réactivation** : l'autorisation système que le casque accorde à l'appli pour
  activer le débogage sans fil. Elle survit aux redémarrages et aux mises à jour de l'appli, et
  disparaît à la désinstallation. Attribut : détenu ou non, relu par l'appli à chaque démarrage.
- **Connexion** (entité du MVP, étendue) : ajoute la cause du dernier échec de reconnexion
  (FR-009).
- **Choix « autorisations sans expiration »** : attaché à l'option. Attributs : état (sans objet,
  inactif, actif, rétablissement en attente) et valeur d'origine du délai, retenue tant que le
  choix est actif ou que le rétablissement est en attente.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Option active et casque sur un Wi-Fi déjà autorisé : sur 5 redémarrages
  consécutifs, l'appli affiche « Connecté (sans fil) » moins de 15 secondes après son ouverture,
  sans aucune action ni PC, dans 100 % des cas.
- **SC-002**: Sur une semaine d'usage normal comprenant au moins 3 redémarrages du casque,
  l'utilisateur n'a jamais besoin du PC.
- **SC-003**: Depuis l'écran de connexion, activer l'option demande au plus 2 gestes (activer,
  puis confirmer).
- **SC-004**: Dans chaque situation d'échec testée (pas de Wi-Fi, réseau refusé, autorisation
  révoquée, droit retiré), la cause affichée correspond à la situation réelle dans 100 % des
  essais.
- **SC-005**: Quand l'appli s'ouvre avant que le casque ait rejoint le Wi-Fi, elle est connectée
  moins de 15 secondes après que le casque a rejoint le réseau, sans action.
- **SC-006**: Après la désactivation de l'option puis un redémarrage, l'appli ne réactive jamais
  le débogage sans fil (100 % des essais). Si elle était connectée au moment de la désactivation,
  elle ne détient plus le droit.
- **SC-007**: Une comparaison des paramètres système du casque avant et après une reconnexion
  autonome ne montre aucune modification autre que l'activation du débogage sans fil et, si
  l'utilisateur l'a choisi, la durée de validité des autorisations.
- **SC-008**: 100 % des nouveaux textes de l'interface sont disponibles en français et en
  anglais.
- **SC-009**: Après l'activation du choix de la User Story 4, le casque ne fait plus expirer les
  autorisations. Après sa désactivation, le délai retrouve exactement sa valeur d'origine, dans
  100 % des essais.
- **SC-010**: Sur un casque réglé avec un délai d'expiration court (1 heure, pour l'essai) et le
  choix actif, l'appli se reconnecte encore sans PC après l'échéance. Sans le choix, elle affiche
  la cause « autorisation plus reconnue ».

## Assumptions

- Les prérequis du MVP restent valables : casque en mode développeur, connecté à un Wi-Fi. La
  toute première autorisation se fait toujours via PC, une seule fois : l'appairage sans PC est
  inaccessible dans le casque (constaté sur Quest 3, vros 207).
- Horizon OS coupe le débogage sans fil à chaque redémarrage (constaté). Sur la plupart des
  casques, le port réseau ouvert depuis le PC se referme aussi : c'est ce qui oblige aujourd'hui à
  repasser par le PC. Sur le Quest 3 de test, ce port est resté ouvert après les redémarrages
  (docs/compatibility.md) ; les essais vérifient donc la méthode affichée.
- Le casque permet à une appli, à laquelle sa propre connexion de débogage a accordé le droit
  système adéquat, d'activer elle-même le débogage sans fil. D'autres outils (QGO, TheDroidGeek,
  shizuku4quest) utilisent cette méthode, décrite dans
  [specs/001-game-profiles-mvp/research.md](../001-game-profiles-mvp/research.md) (R9). Elle
  reste à vérifier sur casque avant d'être présentée comme stable (FR-018).
- Ce droit survit aux redémarrages et aux mises à jour de l'appli, et disparaît à sa
  désinstallation. La désinstallation efface aussi la clé de l'appli : tout recommence alors par
  le PC.
- Android retire une autorisation de débogage restée sans connexion via PC pendant un certain
  temps : 7 jours par défaut. Une connexion sans fil ne remet pas ce compteur à zéro (research.md
  R6). Sur le Quest 3 testé, les autorisations n'expirent pas ; on ignore si c'est le réglage
  d'usine des Quest. L'utilisateur a décidé le 2026-09-24 de proposer d'empêcher cette expiration
  par un choix explicite (User Story 4). Si l'autorisation expire malgré tout, il faut refaire une
  fois l'étape PC, et l'appli le dit.
- Horizon OS demande d'autoriser chaque nouveau réseau Wi-Fi, et parfois à nouveau quand le point
  d'accès change (constaté). Tant que cette fenêtre est ouverte, aucune appli ne se lance.
- Réactiver le débogage sans fil à l'ouverture de l'appli suffit : l'utilisateur ouvre l'appli
  pour appliquer un profil. Le faire au démarrage du casque ferait apparaître la fenêtre réseau
  d'Horizon OS, qui bloque le casque, sans que l'utilisateur sache d'où elle vient.
- L'option est désactivée par défaut, car elle donne à l'appli un droit système durable
  (constitution, principe I). L'appli la propose au bon moment (FR-003).
- Comme aujourd'hui, le débogage sans fil reste actif après la fermeture de l'appli. Toute
  connexion exige de toute façon une clé autorisée par le casque.
- Hors périmètre de cette fonctionnalité :
  - réactivation au démarrage du casque, sans ouvrir l'appli ;
  - application automatique d'un profil quand un jeu est lancé depuis le menu du Quest ;
  - première autorisation sans PC ;
  - coupure du débogage sans fil à la fermeture de l'appli ;
  - fréquences d'affichage au-delà de 120 Hz (seconde issue, traitée à part).
