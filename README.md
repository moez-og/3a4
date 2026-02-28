# Gestion de sortie — Notifications (participations)

Ce projet est une application **JavaFX + JDBC (MySQL)**.

## Fonctionnalités livrées

### Événements couverts
- `PARTICIPATION_REQUESTED` : quand un participant envoie une demande (statut `EN_ATTENTE`) → notification au **créateur** de la sortie.
- `PARTICIPATION_ACCEPTED` : quand la demande passe à `CONFIRMEE` / `ACCEPTEE` → notification au **participant**.
- `PARTICIPATION_REFUSED` : quand la demande passe à `REFUSEE` → notification au **participant**.

### Centre de notifications
- Icône **cloche** + **badge** (nombre non-lus).
- Centre (historique) : liste des notifications, avec statut lu/non-lu.
- Actions : **marquer comme lu** (par notification) + **tout marquer comme lu**.

### Anti-doublon & droits
- Anti-doublon garanti par une contrainte unique DB : une notification unique par `(receiver_id, type, entity_type, entity_id)`.
- Accès strict : les requêtes de lecture/mise à jour filtrent toujours sur `receiver_id`.

## Base de données

### Table `notifications`
Elle est **créée automatiquement** au premier accès DB (via `utils.Mydb` → `utils.db.DbSchema`).

Colonnes principales :
- `id` (BIGINT, PK)
- `receiver_id` (INT)
- `sender_id` (INT, nullable)
- `type` (VARCHAR)
- `title` (VARCHAR)
- `body` (TEXT)
- `entity_type` (VARCHAR)
- `entity_id` (INT)
- `created_at` (TIMESTAMP)
- `read_at` (TIMESTAMP, nullable)
- `metadata_json` (TEXT, nullable)

Indexes : `receiver_id + created_at`, `receiver_id + read_at`, `receiver_id + type`.

## Où cliquer (UI)

### Front Office
- Dans la barre du haut : bouton 🔔 (cloche) → ouvre le centre de notifications.

### Back Office
- Dans le header (à droite) : bouton 🔔 (cloche) → ouvre le centre de notifications.

## “Temps réel” (desktop)

L’app n’utilise pas WebSocket (pas de serveur HTTP ici). À la place :
- le badge non-lus est **rafraîchi par polling** toutes les **5 secondes** (Front + Back).
- l’historique est en DB, donc visible à la reconnexion.

## Test rapide (manuel)

1. Créer 2 comptes `user` dans MySQL (un créateur, un participant) et se connecter avec l’un puis l’autre.
2. Avec le **créateur**, créer une sortie `annonce_sortie`.
3. Avec le **participant**, ouvrir la sortie et cliquer **Participer** → envoyer la demande.
4. Revenir sur le **créateur** : badge 🔔 augmente, et notification visible.
5. Depuis le créateur (ou l’admin) : accepter/refuser la demande → le participant reçoit la notification correspondante.

## Build / tests

Ce repo utilise Maven, mais si `mvn` n’est pas disponible sur ta machine, installe Maven ou utilise le Maven intégré de ton IDE.
