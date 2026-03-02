-- ============================================================
-- MIGRATION SQL — Fonctionnalités avancées Événements
-- Base de données : fintokhrej
-- Date : 2026-02-28
-- ============================================================
-- Ce script corrige et complète la base de données pour
-- supporter toutes les fonctionnalités événement du projet Java.
-- ============================================================

-- ──────────────────────────────────────────────────────────────
-- 1. TABLE inscription : ajouter la colonne nb_tickets
-- ──────────────────────────────────────────────────────────────
-- Le modèle Inscription.java et InscriptionService utilisent
-- nb_tickets (nombre de tickets demandés, défaut = 1).
-- Cette colonne est absente du dump original.

ALTER TABLE `inscription`
  ADD COLUMN `nb_tickets` INT NOT NULL DEFAULT 1
  COMMENT 'Nombre de tickets demandés par l utilisateur';


-- ──────────────────────────────────────────────────────────────
-- 2. TABLE paiement : création complète (MANQUANTE)
-- ──────────────────────────────────────────────────────────────
-- Le modèle Paiement.java et PaiementService.java attendent
-- cette table avec toutes les colonnes ci-dessous.
-- Méthodes : CARTE_BANCAIRE | ESPECES | VIREMENT | FLOUCI
-- Statuts  : PAYE | REMBOURSE

CREATE TABLE IF NOT EXISTS `paiement` (
  `id`               INT AUTO_INCREMENT PRIMARY KEY,
  `inscription_id`   INT          NOT NULL,
  `montant`          DOUBLE       NOT NULL,
  `methode`          VARCHAR(50)  NOT NULL COMMENT 'CARTE_BANCAIRE | ESPECES | VIREMENT | FLOUCI',
  `statut`           VARCHAR(30)  NOT NULL DEFAULT 'PAYE' COMMENT 'PAYE | REMBOURSE',
  `reference_code`   VARCHAR(100) NOT NULL COMMENT 'Code unique PAY-XXXXXXXX',
  `nom_carte`        VARCHAR(100) DEFAULT NULL COMMENT 'Nom sur la carte (nullable)',
  `quatre_derniers`  VARCHAR(4)   DEFAULT NULL COMMENT '4 derniers chiffres de la carte (nullable)',
  `date_paiement`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

  CONSTRAINT `fk_paiement_inscription`
    FOREIGN KEY (`inscription_id`) REFERENCES `inscription` (`id`)
    ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ──────────────────────────────────────────────────────────────
-- 3. TABLE ticket : corriger la clé étrangère (BUG)
-- ──────────────────────────────────────────────────────────────
-- Dans le dump original, la FK fk_ticket_event pointe vers
-- evenement(id) alors que la colonne est inscription_id.
-- Le TicketService.java fait JOIN inscription i ON i.id = t.inscription_id
-- → il faut corriger pour référencer inscription(id).

ALTER TABLE `ticket`
  DROP FOREIGN KEY `fk_ticket_event`;

ALTER TABLE `ticket`
  ADD CONSTRAINT `fk_ticket_inscription`
    FOREIGN KEY (`inscription_id`) REFERENCES `inscription` (`id`)
    ON DELETE CASCADE ON UPDATE CASCADE;


-- ──────────────────────────────────────────────────────────────
-- 4. INDEX supplémentaires pour les performances
-- ──────────────────────────────────────────────────────────────

-- Index sur paiement.inscription_id (recherche fréquente)
CREATE INDEX `idx_paiement_inscription` ON `paiement` (`inscription_id`);

-- Index sur paiement.reference_code (recherche par code)
CREATE INDEX `idx_paiement_reference` ON `paiement` (`reference_code`);

-- Index sur ticket.inscription_id (jointures fréquentes)
CREATE INDEX `idx_ticket_inscription` ON `ticket` (`inscription_id`);

-- Index sur inscription.event_id + statut (requêtes count/filtre)
CREATE INDEX `idx_inscription_event_statut` ON `inscription` (`event_id`, `statut`);


-- ============================================================
-- RÉSUMÉ DES MODIFICATIONS
-- ============================================================
--
-- ┌─────────────────────┬──────────────────────────────────────┐
-- │ Modification        │ Détail                               │
-- ├─────────────────────┼──────────────────────────────────────┤
-- │ inscription         │ + colonne nb_tickets (INT, défaut 1) │
-- │ paiement (NOUVEAU)  │ Table complète pour les paiements    │
-- │                     │   - inscription_id (FK)              │
-- │                     │   - montant, methode, statut         │
-- │                     │   - reference_code (unique PAY-xxx)  │
-- │                     │   - nom_carte, quatre_derniers       │
-- │                     │   - date_paiement                    │
-- │ ticket              │ FK corrigée : inscription(id)        │
-- │                     │   au lieu de evenement(id)           │
-- │ Index               │ 4 index ajoutés pour performances    │
-- └─────────────────────┴──────────────────────────────────────┘
--
-- ============================================================
