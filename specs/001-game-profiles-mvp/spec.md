# Feature Specification: Profils par jeu (MVP d'OpenQuestTuner)

**Feature Branch**: `001-game-profiles-mvp` (aucune branche créée : dépôt sans commit)

**Created**: 2026-09-23

**Status**: Draft

**Input**: User description: "Première fonctionnalité d'OpenQuestTuner (MVP) : une application 2D pour casque Meta Quest qui permet à l'utilisateur de (1) connecter l'appli à l'ADB du casque, soit par appairage du débogage sans fil sans PC (code d'appairage + port saisis depuis l'écran Développeur d'Horizon OS, ouvert à côté en multi-fenêtre), soit via `adb tcpip 5555` lancé une fois depuis un PC, avec reconnexion à la dernière méthode utilisée au démarrage de l'appli ; (2) lister les jeux VR installés (hors applis système), avec icône, nom et indication d'un profil existant, et une recherche par nom ; (3) créer, modifier et supprimer un profil par jeu : fréquence d'affichage, résolution de rendu par œil (paliers relatifs à la résolution par défaut du casque), niveaux CPU et GPU, niveau de rendu fovéal fixe et rendu fovéal dynamique — chaque réglage pouvant rester « par défaut du jeu » ; (4) appliquer le profil puis lancer le jeu en un geste (le jeu est redémarré s'il tournait déjà, et les réglages non définis dans le profil sont remis par défaut pour ne pas hériter du jeu précédent) ; (5) réinitialiser tous les réglages gérés en un geste ; (6) afficher les propriétés debug.oculus.* actuellement actives pour le diagnostic. Langues de l'interface : français et anglais. Hors périmètre pour cette fonctionnalité : application automatique quand un jeu est lancé depuis le menu du Quest, reconnexion automatique à ADB après un redémarrage du casque, réglages de capture vidéo, overlay de performances, profils communautaires et import/export."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Connecter l'appli au casque (Priority: P1)

*Amendée le 2026-09-23 après essai sur Quest 3 (vros 207) : l'écran Android d'appairage n'est pas
accessible dans le casque, mais une clé autorisée une fois suffit ensuite pour le sans-fil
(docs/compatibility.md).*

Dans son casque, l'utilisateur ouvre OpenQuestTuner pour la première fois. L'appli lui explique
qu'elle a besoin d'un accès de débogage, qui s'obtient en deux temps.

- **Première autorisation, une seule fois, avec un PC ou un téléphone** : l'utilisateur branche
  son casque, lance une fois la commande qui ouvre le port de débogage réseau, puis touche
  « Se connecter (via PC) » dans l'appli. Il accepte l'invite d'autorisation du casque en cochant
  « Toujours autoriser ».
- **Passage en sans fil** : une fois connecté, l'utilisateur touche « Passer en sans fil ».
  L'appli active elle-même le débogage sans fil ; la première fois sur ce réseau, Horizon OS
  demande de l'autoriser. L'appli se connecte alors sans fil, sans code d'appairage et sans PC.
- **Appairage par code, en secours** : si sa version d'Horizon OS affiche l'écran « Débogage sans
  fil » avec un code, l'utilisateur peut aussi s'appairer sans PC en saisissant ce code et ce port.

Lors des ouvertures suivantes, l'appli se reconnecte seule : d'abord avec la dernière méthode qui
a fonctionné, puis avec l'autre.

**Why this priority**: sans connexion, aucun réglage ne peut être appliqué. C'est le prérequis de
toute la valeur du produit. Grâce au passage en sans fil, le PC ne sert qu'une seule fois.

**Independent Test**: sur un Quest 3 en mode développeur :
1. méthode PC : l'état passe à « Connecté (via PC) » ;
2. « Passer en sans fil » : l'état passe à « Connecté (sans fil) », sans code ;
3. fermer puis rouvrir l'appli : l'état redevient « Connecté » sans aucune action.

**Acceptance Scenarios**:

1. **Given** le port de débogage réseau ouvert depuis un PC, **When** l'utilisateur touche
   « Se connecter (via PC) » puis accepte l'invite d'autorisation du casque, **Then** l'état passe
   à « Connecté (via PC) ».
2. **Given** l'appli connectée via PC, **When** l'utilisateur touche « Passer en sans fil » et,
   si Horizon OS le demande, autorise le débogage sans fil sur ce réseau, **Then** l'état passe à
   « Connecté (sans fil) », sans aucun code d'appairage.
3. **Given** l'appli connectée via PC, **When** l'utilisateur touche « Passer en sans fil » mais
   n'autorise pas le réseau dans les 60 secondes, **Then** l'appli reste « Connecté (via PC) » et
   explique comment réessayer.
4. **Given** une version d'Horizon OS qui affiche un code d'appairage, **When** l'utilisateur
   saisit ce code et ce port puis touche « Associer », **Then** l'appli confirme l'appairage et
   passe à « Connecté (sans fil) » sans autre saisie.
5. **Given** un code d'appairage erroné ou expiré, **When** l'utilisateur touche « Associer »,
   **Then** l'appli indique que le code est refusé ou expiré et invite à en demander un nouveau.
6. **Given** aucune méthode disponible (port fermé, débogage sans fil désactivé), **When**
   l'utilisateur tente de se connecter, **Then** l'appli affiche une cause compréhensible et
   l'étape à refaire.
7. **Given** une connexion réussie lors d'une session précédente et la même méthode toujours
   disponible, **When** l'utilisateur rouvre l'appli, **Then** elle se reconnecte seule, sans
   bloquer l'interface.
8. **Given** la dernière méthode n'est plus disponible mais l'autre l'est (par exemple, après un
   redémarrage, le sans-fil est coupé mais le port réseau est resté ouvert), **When**
   l'utilisateur rouvre l'appli, **Then** elle se reconnecte seule avec l'autre méthode.
9. **Given** aucune méthode n'est disponible, **When** l'utilisateur rouvre l'appli, **Then**
   l'état affiche simplement « Déconnecté », sans message d'erreur alarmant.

---

### User Story 2 - Optimiser un jeu et le lancer (Priority: P1)

L'utilisateur parcourt la liste de ses jeux VR, choisit un jeu et règle son profil :
- fréquence d'affichage ;
- résolution de rendu ;
- niveaux CPU et GPU ;
- rendu fovéal fixe et rendu fovéal dynamique.

Il touche « Appliquer et lancer » : le jeu démarre avec ces réglages. Le profil est enregistré et
réutilisé la prochaine fois.

**Why this priority**: c'est la raison d'être de l'appli. Avec les User Stories 1 et 4, elle forme
le MVP.

**Independent Test**: une fois connecté, créer un profil pour un jeu de référence, par exemple
120 Hz et résolution ×1,2, puis toucher « Appliquer et lancer ». Le jeu démarre, l'effet est
visible, et le diagnostic (User Story 5) affiche les valeurs attendues.

**Acceptance Scenarios**:

1. **Given** l'appli connectée et des jeux VR installés, **When** l'utilisateur ouvre la liste,
   **Then** il voit ses jeux VR (hors applis système), avec icône et nom, triés par nom.
2. **Given** un jeu sans profil, **When** l'utilisateur l'ouvre, **Then** tous les réglages sont
   sur « Par défaut du jeu ».
3. **Given** un profil modifié, **When** l'utilisateur touche « Appliquer et lancer », **Then** le
   profil est enregistré, le jeu est arrêté s'il tournait, tous les réglages gérés sont appliqués
   et le jeu démarre.
4. **Given** un profil qui laisse certains réglages sur « Par défaut du jeu », et un jeu
   précédent qui les avait modifiés, **When** l'utilisateur lance ce jeu, **Then** ces réglages
   sont remis par défaut avant le lancement.
5. **Given** l'appli déconnectée, **When** l'utilisateur ouvre un profil, **Then** il peut le
   modifier et l'enregistrer, mais « Appliquer et lancer » est désactivé avec une explication et
   un accès à l'écran de connexion.
6. **Given** un réglage que le casque refuse d'appliquer, **When** l'utilisateur touche
   « Appliquer et lancer », **Then** le jeu n'est pas lancé et l'appli indique quel réglage a
   échoué.
7. **Given** un profil avec des niveaux CPU/GPU élevés ou une résolution supérieure à ×1,0,
   **When** l'utilisateur le regarde, **Then** un avertissement signale la chauffe, l'autonomie
   réduite et le risque de perte de fluidité.
8. **Given** un réglage ou une valeur non vérifié pour le casque détecté, ou un casque non
   reconnu, **When** l'utilisateur ouvre un profil, **Then** ces réglages sont marqués
   « expérimental ».
9. **Given** un jeu lancé via « Appliquer et lancer », **When** l'appli confirme le lancement,
   **Then** elle rappelle que les réglages resteront actifs pour les jeux suivants jusqu'à
   « Tout réinitialiser » ou un redémarrage du casque.

---

### User Story 3 - Retrouver et gérer ses profils (Priority: P2)

L'utilisateur qui possède beaucoup de jeux retrouve rapidement un jeu grâce à la recherche. Il
repère d'un coup d'œil les jeux qui ont déjà un profil. Il peut modifier ou supprimer un profil
existant, et lancer un jeu directement depuis la liste avec son profil.

**Why this priority**: indispensable dès que l'utilisateur a plus d'une poignée de jeux, mais le
produit reste utilisable sans cette story.

**Independent Test**: avec au moins 10 jeux installés et 2 profils, retrouver un jeu en tapant
une partie de son nom, vérifier l'indicateur de profil, supprimer un profil, puis constater que
l'indicateur disparaît.

**Acceptance Scenarios**:

1. **Given** la liste des jeux, **When** l'utilisateur tape une partie d'un nom, **Then** la liste
   se filtre au fil de la saisie, sans tenir compte des majuscules ni des accents.
2. **Given** un jeu avec profil, **When** l'utilisateur regarde la liste, **Then** ce jeu porte un
   indicateur « Profil ».
3. **Given** un profil existant, **When** l'utilisateur le supprime et confirme, **Then** le jeu
   n'a plus de profil et ses réglages repassent tous sur « Par défaut du jeu ».
4. **Given** un jeu installé ou désinstallé pendant que l'appli est ouverte, **When** l'utilisateur
   rafraîchit la liste, **Then** la liste reflète le changement.
5. **Given** la liste des jeux et l'appli connectée, **When** l'utilisateur touche « Lancer » sur
   un jeu, **Then** le jeu démarre avec son profil, ou avec tous les réglages par défaut s'il n'en
   a pas.

---

### User Story 4 - Tout réinitialiser (Priority: P1)

Après une session, l'utilisateur veut revenir au comportement normal du casque pour tous ses
jeux. Il touche « Tout réinitialiser » : tous les réglages gérés par l'appli reviennent à leur
valeur par défaut.

**Why this priority**: les réglages restent actifs après la fermeture d'un jeu, et touchent
aussi les jeux lancés depuis le menu du Quest. L'utilisateur doit pouvoir en sortir en un geste,
sans redémarrer le casque. La constitution l'exige (principe I) : cette story fait donc partie du
MVP, au même titre que les User Stories 1 et 2.

**Independent Test**: appliquer un profil, toucher « Tout réinitialiser », puis constater dans le
diagnostic qu'aucun réglage géré n'est plus actif.

**Acceptance Scenarios**:

1. **Given** des réglages actifs, **When** l'utilisateur touche « Tout réinitialiser », **Then**
   tous les réglages gérés reviennent par défaut et l'appli le confirme.
2. **Given** l'appli déconnectée, **When** l'utilisateur regarde « Tout réinitialiser », **Then**
   l'action est désactivée, avec le rappel qu'un redémarrage du casque efface aussi tous les
   réglages.

---

### User Story 5 - Diagnostiquer les réglages actifs et l'état thermique (Priority: P3)

*Amendée le 2026-09-23 après les essais sur Quest 3 (vros 207) : ajout de l'état thermique du
casque, lisible sans connexion, et d'un indicateur « actif sur le casque » dans l'écran profil.
Après « Tout réinitialiser », l'écran profil gardait ses choix, ce qui laissait croire que les
réglages étaient encore actifs. Et pendant les essais, le casque a chauffé sans que l'appli le
signale.*

L'utilisateur (ou un contributeur qui teste un nouveau casque) veut savoir quels réglages sont
réellement actifs. L'appli affiche la liste des propriétés de réglage actuellement définies sur
le casque, avec leurs valeurs. Dans l'écran profil d'un jeu, elle indique si les réglages affichés
sont actifs sur le casque ou pas encore appliqués.

L'utilisateur veut aussi savoir si son casque chauffe, surtout avec des niveaux CPU/GPU élevés.
L'appli affiche l'état thermique donné par le système, même sans connexion, et prévient quand le
casque devient chaud.

**Why this priority**: utile pour vérifier un profil, et nécessaire pour remplir le tableau de
compatibilité (constitution, principe III). Ce n'est pas un parcours quotidien.

**Independent Test**: une fois connecté, ouvrir le diagnostic avant et après « Appliquer et
lancer », et comparer les valeurs affichées avec le profil.

**Acceptance Scenarios**:

1. **Given** l'appli connectée et un profil appliqué, **When** l'utilisateur ouvre le diagnostic,
   **Then** il voit chaque propriété active et sa valeur, en lecture seule.
2. **Given** aucun réglage actif, **When** l'utilisateur ouvre le diagnostic, **Then** l'appli
   indique qu'aucun réglage n'est actif.
3. **Given** le diagnostic affiché, **When** l'utilisateur touche « Actualiser », **Then** les
   valeurs sont relues sur le casque.
4. **Given** l'appli ouverte, connectée ou non, **When** l'utilisateur ouvre le diagnostic,
   **Then** il voit l'état thermique du casque, mis à jour en direct.
5. **Given** un casque dont l'état thermique atteint « modéré » ou plus, **When** l'utilisateur
   est sur la liste des jeux ou sur un profil, **Then** un avertissement l'invite à baisser les
   niveaux CPU/GPU ou la résolution, ou à faire une pause.
6. **Given** l'appli connectée, **When** l'utilisateur ouvre le profil d'un jeu, **Then** l'appli
   indique si les réglages affichés sont actifs sur le casque ou non appliqués. Après « Tout
   réinitialiser », un profil non vide est indiqué « non appliqué ».

---

### Edge Cases

- **Connexion perdue en cours d'utilisation** (Wi-Fi coupé, débogage désactivé, casque en
  veille) : l'état passe à « Déconnecté » dès la prochaine action qui échoue. Les actions qui
  demandent une connexion sont désactivées avec une explication. L'édition des profils reste
  possible.
- **Invite d'autorisation refusée ou ignorée** dans le casque : la tentative échoue au bout d'un
  délai raisonnable, avec un message qui explique comment réessayer.
- **Débogage sans fil coupé**, par exemple après un redémarrage du casque : l'appli se reconnecte
  par le port réseau s'il est resté ouvert, sinon elle affiche « Déconnecté ». Une fois
  reconnectée, « Passer en sans fil » réactive le sans-fil ; il n'y a jamais besoin de réappairer.
- **Réseau refusé** dans la fenêtre d'Horizon OS « autoriser le débogage sans fil » : l'appli
  reste connectée via PC et explique comment réessayer.
- **Découverte automatique du port de connexion impossible** : l'utilisateur peut saisir le port
  affiché par l'écran « Débogage sans fil ».
- **Saisie invalide** (code qui ne fait pas 6 chiffres, port hors de 1 à 65535) : le bouton reste
  désactivé et le champ signale l'erreur.
- **Aucun jeu VR installé** : la liste affiche un état vide explicatif.
- **Jeu désinstallé** après la création de son profil : le jeu disparaît de la liste, mais son
  profil est conservé et réapparaît si le jeu est réinstallé.
- **Jeu désinstallé** entre l'affichage de la liste et le lancement : l'appli signale que le jeu
  est introuvable et rafraîchit la liste.
- **Jeu qui ignore un réglage** (certains jeux imposent leur propre résolution ou fréquence) :
  l'appli ne peut pas garantir l'effet. Le diagnostic permet de vérifier que le réglage est bien
  actif côté casque.
- **Casque non reconnu** : l'appli utilise les valeurs du Quest 3 et marque tous les réglages
  comme expérimentaux.
- **Redémarrage du casque** : tous les réglages actifs sont effacés (voulu) et l'appli sera
  déconnectée à la prochaine ouverture.
- **Réglages actifs après la fermeture du jeu** : ils s'appliquent aussi aux jeux lancés ensuite
  depuis le menu du Quest, jusqu'à une réinitialisation ou un redémarrage. L'utilisateur en est
  informé (FR-022).
- **Casque qui chauffe** (session longue, niveaux élevés, charge pendant l'utilisation) : le
  système réduit lui-même les performances, puis coupe l'appli en cours au-delà d'un seuil.
  L'appli affiche l'état thermique et prévient dès « modéré » (FR-031, FR-032). Elle ne baisse
  jamais les réglages d'elle-même.
- **État thermique indisponible** (API absente ou en erreur) : l'appli affiche « inconnu » et
  n'avertit pas.

## Requirements *(mandatory)*

### Functional Requirements

**Connexion**

- **FR-001**: Une fois connectée par la méthode PC, l'appli DOIT permettre de passer en sans fil
  en un geste (« Passer en sans fil »). Elle active elle-même le débogage sans fil du casque, puis
  s'y connecte avec la même autorisation, sans code d'appairage. Si Horizon OS demande d'autoriser
  le réseau, l'appli attend la réponse de l'utilisateur jusqu'à 60 secondes ; sans réponse, elle
  reste connectée via PC.
- **FR-002**: L'appli DOIT aussi permettre l'appairage par code (6 chiffres et port), pour les
  versions d'Horizon OS qui affichent cet écran. Après un appairage réussi, elle se connecte seule
  en trouvant le port de connexion ; si elle n'y parvient pas, l'utilisateur DOIT pouvoir saisir
  ce port manuellement.
- **FR-003**: L'appli DOIT permettre de se connecter au port de débogage réseau 5555 ouvert depuis
  un PC ou un téléphone, et guider l'utilisateur pour accepter l'invite d'autorisation du casque
  en cochant « Toujours autoriser ». C'est la méthode de première connexion.
- **FR-004**: L'appli DOIT afficher en permanence l'état de connexion :
  - « Déconnecté » ;
  - « Connexion en cours » ;
  - « Connecté », avec la méthode utilisée ;
  - « Échec », avec une cause compréhensible et l'action suggérée.
- **FR-005**: Au démarrage, l'appli DOIT tenter de se reconnecter en arrière-plan, d'abord avec la
  dernière méthode réussie, puis avec l'autre. Si toutes les tentatives échouent, l'état DOIT être
  « Déconnecté », sans message d'erreur.
- **FR-006**: L'utilisateur DOIT pouvoir se déconnecter.
- **FR-007**: Chaque méthode de connexion DOIT être accompagnée d'instructions pas à pas, en
  langage simple, affichées dans l'appli. La méthode PC est présentée comme la première étape,
  l'appairage par code comme un secours.

**Liste des jeux**

- **FR-008**: L'appli DOIT lister les applications VR installées par l'utilisateur, sans les
  applications système ni les applications 2D. Chaque jeu est affiché avec son icône, son nom et
  un indicateur de profil existant, et la liste est triée par nom.
- **FR-009**: L'appli DOIT proposer une recherche par nom qui filtre la liste au fil de la saisie,
  sans tenir compte des majuscules ni des accents.
- **FR-010**: L'utilisateur DOIT pouvoir rafraîchir la liste pour prendre en compte les jeux
  installés ou désinstallés.

**Profils**

- **FR-011**: Pour chaque jeu, l'utilisateur DOIT pouvoir définir un profil composé des réglages
  suivants :
  - fréquence d'affichage, parmi les valeurs supportées par le casque détecté ;
  - résolution de rendu par œil, en paliers de ×0,7 à ×1,5 de la résolution par défaut du casque
    détecté, par pas de 0,1, avec les dimensions obtenues affichées pour chaque palier ;
  - niveau CPU et niveau GPU, dans les plages du casque détecté ;
  - rendu fovéal fixe : désactivé, faible, moyen, élevé ou très élevé ;
  - rendu fovéal dynamique : activé ou désactivé.
- **FR-012**: Chaque réglage DOIT pouvoir rester sur « Par défaut du jeu ». C'est sa valeur
  initiale.
- **FR-013**: Les profils DOIVENT être conservés localement, et survivre à la fermeture de l'appli
  comme au redémarrage du casque.
- **FR-014**: L'utilisateur DOIT pouvoir modifier un profil, et le supprimer après confirmation.
- **FR-015**: Un profil dont tous les réglages sont « Par défaut du jeu » DOIT être traité comme
  une absence de profil (pas d'indicateur dans la liste).
- **FR-016**: L'appli DOIT détecter le modèle de casque (Quest 3, Quest 3S, Quest 2, Quest Pro)
  et adapter les valeurs proposées. Pour un casque non reconnu, elle utilise les valeurs du
  Quest 3 et marque tous les réglages comme expérimentaux.
- **FR-017**: Tout réglage ou toute valeur qui n'est pas vérifié pour le casque détecté DOIT être
  marqué « expérimental » dans l'interface.
- **FR-018**: L'appli DOIT prévenir l'utilisateur que des niveaux CPU/GPU élevés et une
  résolution supérieure à ×1,0 augmentent la chauffe et réduisent l'autonomie, et qu'une
  résolution trop élevée peut réduire la fluidité.

**Appliquer et lancer**

- **FR-019**: L'action « Appliquer et lancer » DOIT, dans cet ordre :
  1. enregistrer le profil ;
  2. arrêter le jeu s'il est en cours d'exécution ;
  3. appliquer chaque réglage géré : la valeur du profil, ou la valeur par défaut pour ceux
     laissés sur « Par défaut du jeu » ;
  4. lancer le jeu.
- **FR-020**: Si l'application d'un réglage échoue, l'appli NE DOIT PAS lancer le jeu. Elle DOIT
  indiquer quel réglage a échoué.
- **FR-021**: « Appliquer et lancer » et « Lancer » DOIVENT être désactivés quand l'appli n'est
  pas connectée, avec une explication et un accès direct à l'écran de connexion.
  L'enregistrement d'un profil reste possible.
- **FR-022**: L'appli DOIT informer l'utilisateur que les réglages restent actifs après la
  fermeture du jeu, y compris pour les jeux lancés depuis le menu du Quest, jusqu'à une
  réinitialisation ou un redémarrage du casque.

**Réinitialisation et diagnostic**

- **FR-023**: L'action « Tout réinitialiser » DOIT remettre tous les réglages gérés à leur valeur
  par défaut, puis confirmer le résultat à l'utilisateur.
- **FR-024**: L'appli DOIT afficher, en lecture seule, les propriétés de réglage actuellement
  définies sur le casque et leurs valeurs, avec une action pour les relire.

**Sécurité et vie privée** (constitution, principes I et II)

- **FR-025**: L'appli NE DOIT agir que sur la liste fermée des réglages gérés décrits en FR-011,
  avec des valeurs issues exclusivement des choix proposés. Les seules saisies libres (code
  d'appairage, ports) DOIVENT être validées comme numériques et ne servent qu'à établir la
  connexion.
- **FR-026**: L'appli NE DOIT écrire aucun réglage qui survive à un redémarrage du casque.
  *Exception ajoutée le 2026-09-24 par la fonctionnalité 002 (FR-021 à FR-023) : sur choix explicite
  et réversible de l'utilisateur, la durée de validité des autorisations de débogage.*
- **FR-027**: L'identité de connexion de l'appli auprès du casque DOIT rester dans son stockage
  privé. Elle n'est jamais affichée, exportée ni transmise.
- **FR-028**: L'appli NE DOIT établir aucune communication réseau autre que la connexion locale
  au casque lui-même, et NE DOIT collecter aucune donnée d'usage.

**Interface**

- **FR-029**: L'interface DOIT être disponible en français et en anglais. Elle suit la langue du
  système et utilise l'anglais pour toute autre langue.
- **FR-030**: L'interface DOIT être utilisable dans un panneau du casque aux contrôleurs comme aux
  mains : cibles d'interaction d'au moins 48 dp, et aucun besoin de clavier physique.

**Diagnostic complémentaire** (amendement de l'US5 du 2026-09-23)

- **FR-031**: L'appli DOIT afficher l'état thermique du casque tel que le système le rapporte
  (normal, léger, modéré, sévère, critique, urgence, arrêt imminent), sans exiger de connexion,
  et le mettre à jour en direct.
- **FR-032**: À partir de l'état « modéré », l'appli DOIT afficher un avertissement sur la liste
  des jeux et sur les profils, qui conseille de baisser les niveaux CPU/GPU ou la résolution, ou
  de faire une pause.
- **FR-033**: Quand l'appli est connectée, l'écran profil DOIT indiquer si les réglages affichés
  correspondent aux valeurs actives sur le casque (« actif sur le casque ») ou non (« non
  appliqué »). La comparaison porte sur les 7 propriétés gérées : un réglage « Par défaut du jeu »
  correspond à une propriété vide.

### Key Entities *(include if feature involves data)*

- **Jeu installé** : une application VR installée par l'utilisateur. Attributs : identifiant
  unique, nom affiché, icône, point d'entrée VR utilisé pour le lancement.
- **Profil de jeu** : les réglages voulus pour un jeu, rattachés à son identifiant unique. Il
  contient six réglages optionnels (FR-011) ; un réglage absent signifie « Par défaut du jeu ».
  Il est conservé même si le jeu est désinstallé.
- **Réglage géré** : un paramètre du casque que l'appli sait modifier. Attributs : nom affiché,
  propriété système associée, valeurs admises selon le modèle de casque, statut « vérifié » ou
  « expérimental » par modèle.
- **Modèle de casque** : Quest 3, Quest 3S, Quest 2, Quest Pro ou non reconnu. Attributs :
  résolution de rendu par défaut par œil, fréquences d'affichage supportées, plages de niveaux
  CPU et GPU.
- **Connexion** : le lien entre l'appli et le débogage du casque. Attributs : méthode (sans fil
  ou via PC), état courant, dernière méthode réussie (conservée pour la reconnexion).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Un utilisateur qui découvre l'appli, avec un PC à disposition, réussit la première
  configuration complète (autorisation via PC, puis passage en sans fil) en moins de 5 minutes, en
  suivant uniquement les instructions de l'appli.
- **SC-002**: Une fois connecté, créer un profil pour un jeu et le lancer prend moins d'une
  minute depuis l'ouverture de l'appli.
- **SC-003**: Le lancement du jeu commence moins de 5 secondes après « Appliquer et lancer », hors
  temps de chargement propre au jeu.
- **SC-004**: Dans 100 % des essais, les valeurs affichées par le diagnostic après « Appliquer et
  lancer » correspondent exactement au profil appliqué.
- **SC-005**: Après « Tout réinitialiser », le diagnostic ne montre plus aucun réglage géré actif.
- **SC-006**: 100 % des profils sont retrouvés à l'identique après une fermeture de l'appli et un
  redémarrage du casque.
- **SC-007**: Quand la méthode précédente est toujours disponible, la reconnexion automatique à
  l'ouverture de l'appli aboutit en moins de 5 secondes.
- **SC-008**: Sur Quest 3, chaque réglage présenté comme « vérifié » a un effet constaté sur au
  moins un jeu de référence, et cet effet est consigné dans le tableau de compatibilité.
- **SC-009**: Une inspection du trafic réseau pendant une session complète ne montre aucune
  communication hors de la connexion locale au casque.
- **SC-010**: 100 % des textes de l'interface sont disponibles en français et en anglais.
- **SC-011**: L'état thermique affiché correspond à celui du système (`dumpsys thermalservice`).
  Quand il atteint « modéré », l'avertissement apparaît en moins de 5 secondes.
- **SC-012**: Dans 100 % des essais, l'indicateur de l'écran profil passe à « actif sur le
  casque » après « Appliquer et lancer », puis à « non appliqué » après « Tout réinitialiser ».

## Assumptions

- Le casque est en mode développeur (compte développeur Meta), ce qui est aussi nécessaire pour
  installer l'appli, distribuée hors store pour cette version.
- Le casque est connecté à un réseau Wi-Fi : le débogage sans fil l'exige.
- Horizon OS ne donne pas accès, dans le casque, à l'écran Android « Débogage sans fil » :
  l'option est absente des Paramètres Quest et l'écran développeur d'Android est désactivé
  (constaté sur Quest 3, vros 207). La toute première autorisation se fait donc avec un PC ou un
  téléphone équipé d'ADB. L'appairage par code reste proposé pour les versions qui affichent cet
  écran (signalé à partir de la v83, dans l'ancienne numérotation).
- Une clé autorisée une fois (« Toujours autoriser ») est ensuite acceptée en sans fil (TLS) sans
  appairage. Activer le débogage sans fil déclenche une fenêtre Horizon OS « autoriser sur ce
  réseau » la première fois (constaté sur Quest 3, vros 207).
- Le débogage sans fil est coupé à chaque redémarrage du casque. Le port réseau ouvert depuis un PC
  a survécu au redémarrage lors d'un essai, ce qui reste à reconfirmer. La réactivation
  automatique du sans-fil après un redémarrage est hors périmètre de cette fonctionnalité.
- Les jeux ne lisent ces réglages qu'à leur démarrage. C'est pourquoi l'appli arrête un jeu en
  cours avant de le relancer.
- Les réglages sont des paramètres de débogage non documentés par Meta : leur effet peut varier
  selon le jeu, le casque et la version d'Horizon OS (d'où le statut « expérimental »).
- Paliers de résolution par défaut : ×0,7 à ×1,5 par pas de 0,1. Des valeurs plus larges pourront
  être ajoutées après vérification sur casque.
- En cas d'échec d'un réglage, ne pas lancer le jeu est le comportement le plus sûr ;
  l'utilisateur peut corriger le profil puis réessayer.
- Seules les applications déclarées comme VR sont listées. Les applications 2D, dont
  OpenQuestTuner lui-même, sont exclues.
- Hors périmètre de cette fonctionnalité :
  - application automatique d'un profil quand un jeu est lancé depuis le menu du Quest ;
  - réactivation automatique du débogage sans fil après un redémarrage du casque ;
  - réglages de capture vidéo ;
  - overlay de performances ;
  - profils communautaires et import/export.
