# Feature Specification: Fréquences d'affichage au-delà de 120 Hz

**Feature Branch**: `003-high-refresh-rates` (aucune branche créée : travail sur `main`)

**Created**: 2026-09-24

**Status**: Draft (amendée le 2026-09-24 : plafond à 200 Hz, à la demande de l'utilisateur ; puis
précisions issues de `/speckit-analyze` : FR-008, SC-006, US3 scénario 1)

**Input**: User description: "Fréquences d'affichage élevées (seconde issue : « peut-on monter à 144 Hz ? »). Proposer sur Quest 3 les fréquences au-delà de 120 Hz mesurées le 2026-09-24 (144, 160, 180, 200, 207 Hz, voir docs/compatibility.md), marquées expérimental ; empêcher de choisir certaines résolutions pour certaines fréquences élevées. Demande de l'utilisateur : « avec ce que tu as, crée la spec ; on mettra expérimental sur les valeurs élevées avec impossibilité de choisir certaines résolutions pour certains fps si possible »."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Choisir une fréquence au-delà de 120 Hz (Priority: P1)

Un utilisateur a demandé dans une issue s'il pouvait passer à 144 Hz. Les essais du 2026-09-24
sur Quest 3 (docs/compatibility.md) montrent que l'écran suit toutes les fréquences demandées
jusqu'à 207 Hz, y compris dans un jeu ; l'appli s'arrête à 200 Hz. Le jeu doit toutefois
produire autant d'images par seconde : avec ses réglages par défaut, Beat Saber tient environ
160 images/s, et au-delà il répète des images.

Dans le profil d'un jeu, l'utilisateur voit désormais, après 120 Hz, les fréquences 144, 160,
180 et 200 Hz, toutes marquées « expérimental ». Il en choisit une, lit l'avertissement sur
la fluidité et la chauffe, puis touche « Appliquer et lancer » : le jeu démarre à cette
fréquence.

**Why this priority**: c'est la réponse directe à l'issue, et le seul changement nécessaire pour
en profiter : le réglage de fréquence existe déjà, seule la liste des valeurs s'allonge.

**Independent Test**: sur un Quest 3 connecté, créer un profil à 144 Hz pour Beat Saber, toucher
« Appliquer et lancer », puis constater que l'écran tourne à 144 Hz. Refaire avec 200 Hz.

**Acceptance Scenarios**:

1. **Given** un Quest 3 dont l'écran déclare ces fréquences, **When** l'utilisateur ouvre le
   choix de la fréquence dans un profil, **Then** il voit les fréquences actuelles, puis 144, 160,
   180 et 200 Hz, et aucune fréquence au-delà.
2. **Given** le choix de la fréquence ouvert, **When** l'utilisateur regarde les fréquences
   au-delà de 120 Hz, **Then** chacune porte la mention « expérimental ».
3. **Given** un profil à une fréquence au-delà de 120 Hz, **When** l'utilisateur le regarde,
   **Then** un avertissement explique que le jeu doit suivre cette cadence, faute de quoi des
   images sont répétées et le jeu peut paraître moins fluide qu'à 120 Hz, et que la chauffe
   augmente et l'autonomie baisse.
4. **Given** un profil à 160 Hz, **When** l'utilisateur touche « Appliquer et lancer », **Then**
   le jeu démarre et l'écran tourne à 160 Hz.
5. **Given** un casque dont l'écran ne déclare pas une fréquence élevée, **When** l'utilisateur
   ouvre le choix de la fréquence, **Then** cette fréquence n'est pas proposée.

---

### User Story 2 - Combinaisons fréquence et résolution limitées (Priority: P2)

Plus la fréquence est élevée, moins le jeu a de temps pour calculer chaque image. Une résolution
élevée à 200 Hz garantit des saccades. Pour chaque fréquence au-delà de 120 Hz, l'appli fixe donc
une résolution maximale. Les résolutions plus élevées ne peuvent pas être choisies à cette
fréquence, et l'appli dit pourquoi.

**Why this priority**: c'est ce qui évite qu'une fréquence élevée donne un résultat pire que
120 Hz. Mais la User Story 1 apporte déjà de la valeur sans cette limite.

**Independent Test**: dans un profil à ×1,5, choisir 200 Hz : la résolution passe à ×0,8 avec
un message, et les paliers de ×0,9 à ×1,5 sont indisponibles. Revenir à 120 Hz : tous les paliers
redeviennent disponibles.

**Acceptance Scenarios**:

1. **Given** un profil à 180 Hz, **When** l'utilisateur ouvre le choix de la résolution,
   **Then** les paliers au-dessus de ×0,9 sont indisponibles, et l'appli indique qu'ils ne sont
   pas proposés à 180 Hz.
2. **Given** un profil à ×1,5, **When** l'utilisateur choisit 200 Hz, **Then** la résolution
   descend à ×0,8, et un message dit que la résolution a été abaissée pour cette fréquence.
3. **Given** un profil à ×0,8, **When** l'utilisateur choisit 200 Hz, **Then** la résolution
   n'est pas modifiée.
4. **Given** un profil à 200 Hz et ×0,8, **When** l'utilisateur repasse à 120 Hz, **Then** tous
   les paliers de résolution redeviennent disponibles, et la résolution reste à ×0,8.
5. **Given** un profil à une fréquence élevée et une résolution laissée sur « Par défaut du
   jeu », **When** l'utilisateur le regarde, **Then** ce choix reste possible, et
   l'avertissement de la User Story 1 rappelle que la résolution du jeu peut être trop élevée
   pour cette fréquence.
6. **Given** un profil enregistré avec une combinaison hors limites (modifié hors de l'appli, par
   exemple), **When** l'utilisateur touche « Appliquer et lancer », **Then** le jeu n'est pas
   lancé et l'appli indique quel réglage corriger.

---

### User Story 3 - Voir la fréquence réelle de l'écran (Priority: P3)

Le diagnostic affiche déjà la fréquence *demandée*. Pour vérifier qu'une fréquence expérimentale
a bien pris, l'utilisateur voit aussi la fréquence à laquelle l'écran tourne *réellement*, même
sans connexion.

**Why this priority**: cela permet à chacun de vérifier une valeur expérimentale sur son casque
et de le signaler, sans PC ni outil de mesure (constitution, principe III). Mais ce n'est pas
indispensable pour utiliser les fréquences élevées.

**Independent Test**: appliquer un profil à 180 Hz, ouvrir le diagnostic : la fréquence réelle
affichée est 180 Hz. Toucher « Tout réinitialiser » : elle revient à la fréquence normale du
casque.

**Acceptance Scenarios**:

1. **Given** un profil à 180 Hz appliqué, **When** l'utilisateur ouvre le diagnostic, **Then**
   il voit « Fréquence de l'écran : 180 Hz », distincte de la valeur demandée, qui reste dans
   la liste des réglages actifs quand l'appli est connectée.
2. **Given** l'appli déconnectée, **When** l'utilisateur ouvre le diagnostic, **Then** la
   fréquence réelle de l'écran reste affichée.
3. **Given** le diagnostic ouvert, **When** la fréquence de l'écran change, **Then** la valeur
   affichée se met à jour sans action de l'utilisateur.

---

### Edge Cases

- **Jeu qui ne suit pas la cadence** : l'écran tourne à la fréquence demandée mais répète des
  images. Mesuré sur Beat Saber, avec les réglages du jeu : une image sur cinq répétée à 180 Hz,
  près d'une sur trois à 200 Hz. L'appli ne peut pas mesurer la cadence du jeu ; elle prévient
  (FR-004).
- **Jeu qui impose sa propre fréquence** : l'effet n'est pas garanti. Le diagnostic (User
  Story 3) montre la fréquence réelle.
- **Fréquence élevée active après la fermeture du jeu** : tout le casque, menu compris, reste à
  cette fréquence jusqu'à « Tout réinitialiser » ou un redémarrage. Cela consomme plus de
  batterie ; l'appli le rappelle (FR-009).
- **Profil créé sur un casque, puis utilisé sur un autre** dont l'écran ne déclare pas cette
  fréquence : la fréquence est conservée dans le profil, mais signalée comme non disponible sur
  ce casque. « Appliquer et lancer » est refusé tant qu'elle n'est pas changée.
- **Casque non reconnu** : l'appli ne propose une fréquence élevée que si l'écran la déclare, et
  la marque « expérimental » comme les autres.
- **Mise à jour d'Horizon OS qui retire des fréquences** : même comportement que le cas
  précédent, sans action particulière de l'utilisateur.
- **Casque qui chauffe à fréquence élevée** : l'avertissement thermique existant s'applique.
  L'essai de 1 minute du 2026-09-24 est passé de 43 à 45 °C, avec un état thermique normal.

## Requirements *(mandatory)*

### Functional Requirements

**Fréquences proposées**

- **FR-001**: En plus des fréquences actuelles, l'appli DOIT proposer 144, 160, 180 et
  200 Hz, chacune seulement si l'écran du casque détecté la déclare. Aucune fréquence au-delà
  de 200 Hz NE DOIT être proposée, même si l'écran la déclare.
- **FR-002**: Les fréquences proposées DOIVENT rester une liste fermée, choisie dans un menu ;
  aucune fréquence ne peut être saisie librement (constitution, principe I).
- **FR-003**: Toute fréquence au-delà de 120 Hz DOIT être marquée « expérimental », sur tous les
  modèles de casque, Quest 3 compris, jusqu'à ce qu'une décision contraire soit consignée dans le
  tableau de compatibilité.
- **FR-004**: Quand un profil utilise une fréquence au-delà de 120 Hz, l'appli DOIT afficher un
  avertissement qui explique, en langage simple :
  - que le jeu doit produire autant d'images par seconde, sinon des images sont répétées et le
    jeu peut paraître moins fluide qu'à 120 Hz ;
  - que baisser la résolution ou monter le niveau GPU aide le jeu à suivre ;
  - que la chauffe augmente et que l'autonomie baisse.

**Limites de résolution**

- **FR-005**: Pour chaque fréquence au-delà de 120 Hz, l'appli DOIT limiter la résolution de
  rendu à un palier maximal :

  | Fréquence | Résolution maximale |
  |---|---|
  | 144 Hz | ×1,0 |
  | 160 Hz | ×1,0 |
  | 180 Hz | ×0,9 |
  | 200 Hz | ×0,8 |

  Les paliers au-dessus du maximum sont visibles mais indisponibles, avec la raison affichée.
- **FR-006**: Si l'utilisateur choisit une fréquence élevée alors que la résolution du profil
  dépasse le maximum de cette fréquence, l'appli DOIT abaisser la résolution à ce maximum et le
  dire clairement. Une résolution laissée sur « Par défaut du jeu » n'est pas modifiée.
- **FR-007**: Aux fréquences de 120 Hz et moins, tous les paliers de résolution DOIVENT rester
  disponibles, comme aujourd'hui. Repasser à l'une de ces fréquences NE DOIT PAS remonter la
  résolution d'office.
- **FR-008**: « Appliquer et lancer » et « Lancer » DOIVENT refuser un profil dont la fréquence,
  au-delà de 120 Hz, n'est pas déclarée par l'écran du casque, ou dont la résolution dépasse le
  maximum de sa fréquence. Le jeu n'est pas lancé, et l'appli indique quel réglage corriger. Les
  fréquences de 120 Hz et moins gardent la règle actuelle (celles du modèle de casque), sans
  dépendre de l'écran : un écran illisible ne bloque donc aucun profil existant (SC-005).

**Information et diagnostic**

- **FR-009**: Pour une fréquence au-delà de 120 Hz, le rappel « les réglages restent actifs » DOIT
  préciser que tout le casque, menu compris, reste à cette fréquence jusqu'à « Tout
  réinitialiser » ou un redémarrage, ce qui consomme davantage de batterie.
- **FR-010**: Le diagnostic DOIT afficher la fréquence à laquelle l'écran tourne réellement,
  distincte de la valeur demandée, lisible sans connexion et mise à jour en direct.

**Interface**

- **FR-011**: Tous les nouveaux textes DOIVENT exister en français et en anglais, et toutes les
  nouvelles cibles d'interaction DOIVENT mesurer au moins 48 dp.

### Key Entities *(include if feature involves data)*

- **Fréquence d'affichage** (réglage existant, étendu) : la liste proposée devient la liste du
  modèle de casque, complétée par les fréquences élevées que l'écran déclare. Attributs : valeur
  en Hz, « élevée » (au-delà de 120 Hz) ou non, statut « expérimental ».
- **Limite de résolution** : pour chaque fréquence élevée, le palier de résolution maximal
  autorisé (FR-005).
- **Profil de jeu** (entité existante) : sa structure ne change pas. Il gagne une règle de
  validité : une fréquence au-delà de 120 Hz doit être déclarée par l'écran, et la résolution ne
  doit pas dépasser le maximum de sa fréquence.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Sur Quest 3, pour chacune des 4 fréquences élevées, l'écran tourne à la fréquence du
  profil moins de 5 secondes après « Appliquer et lancer », dans 100 % des essais.
- **SC-002**: 100 % des fréquences au-delà de 120 Hz portent la mention « expérimental ».
- **SC-003**: Aucune combinaison fréquence et résolution hors limites ne peut être appliquée,
  ni depuis l'interface, ni depuis un profil modifié hors de l'appli (100 % des essais).
- **SC-004**: Chaque fois que l'appli abaisse la résolution (FR-006), l'utilisateur voit un
  message qui le dit, dans 100 % des cas.
- **SC-005**: Les profils existants à 120 Hz ou moins sont appliqués exactement comme avant :
  aucune valeur modifiée, aucune option retirée.
- **SC-006**: La fréquence réelle affichée par le diagnostic correspond à celle mesurée sur le
  casque dans 100 % des essais, et se met à jour en moins de 5 secondes après un changement de la
  fréquence de l'écran. Ce délai court à partir du changement réel de l'écran, qui peut lui-même
  prendre plus de temps : après « Tout réinitialiser », le retour à la fréquence du menu a pris
  environ 12 s sur Quest 3 (research.md R2).
- **SC-007**: 100 % des nouveaux textes de l'interface sont disponibles en français et en
  anglais.

## Assumptions

- **Mesures de départ** (docs/compatibility.md, 2026-09-24, Quest 3, vros 207) :
  - l'écran déclare chaque fréquence entière de 72 à 207 Hz à sa résolution native, et jusqu'à
    240 Hz dans un mode à résolution réduite ;
  - le réglage de fréquence que l'appli gère déjà suffit pour atteindre ces fréquences, sans
    nouveau réglage système ; l'écran change de fréquence en 3 secondes au plus ;
  - dans Beat Saber, avec les réglages du jeu (résolution ×1,0, charge GPU de 93 à 98 %), le jeu
    suit jusqu'à environ 160 images/s.
- **Fréquences retenues** : 144, 160, 180 et 200 Hz, parmi les valeurs mesurées ce jour-là.
  144 Hz répond à l'issue. 207 Hz, maximum de l'écran à sa résolution native, a aussi été
  mesuré, mais l'utilisateur a choisi de plafonner l'appli à 200 Hz.
- **Limites de résolution** : la table de FR-005 garde la même quantité de pixels par seconde
  que ×1,0 à 160 Hz, la meilleure combinaison mesurée. Résolution maximale = √(160 ÷ fréquence),
  arrondie au palier inférieur. Elle n'a été calibrée que sur un jeu, avec les niveaux CPU/GPU du
  jeu. Elle sera revue après des essais avec une résolution plus basse et un niveau GPU plus
  élevé.
- La limite porte sur le rendu du jeu. Elle ne protège pas le casque : une combinaison hors
  limites donne des saccades, pas de dégât. C'est un garde-fou de confort, demandé par
  l'utilisateur.
- La même table s'applique à tous les modèles, car les paliers de résolution sont relatifs à la
  résolution par défaut de chaque casque. Les modèles moins puissants que le Quest 3 (Quest 2,
  Quest Pro) risquent de saccader plus tôt : les valeurs y sont déjà « expérimental ».
- « Par défaut du jeu » reste permis à fréquence élevée, car l'appli ne connaît pas la résolution
  choisie par le jeu. L'avertissement (FR-004) couvre ce cas.
- Les fréquences au-delà de 120 Hz sont toutes « expérimental » par choix de l'utilisateur,
  même là où l'écran a été vérifié : ce qui reste incertain, c'est la tenue des jeux, qui varie
  de l'un à l'autre.
- Hors périmètre de cette fonctionnalité :
  - fréquences au-delà de 200 Hz : 201 à 207 Hz à la résolution native de l'écran, et jusqu'à
    240 Hz dans son mode à résolution réduite ;
  - fréquences intermédiaires (toutes les autres valeurs de 121 à 199 Hz) ;
  - mesure de la cadence réelle du jeu (images produites par seconde) dans l'appli ;
  - limites de résolution différentes selon le modèle de casque ou selon le jeu ;
  - ajustement automatique du niveau GPU quand la fréquence monte.
