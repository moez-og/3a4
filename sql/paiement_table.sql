-- ============================================================
-- MODULE PAIEMENT — Migration SQL pour la base fintokhrej
-- ============================================================

-- 1. Ajouter la colonne nb_tickets à la table inscription
ALTER TABLE inscription ADD COLUMN nb_tickets INT NOT NULL DEFAULT 1;

-- 2. Créer la table paiement
CREATE TABLE IF NOT EXISTS paiement (
    id               INT AUTO_INCREMENT PRIMARY KEY,
    inscription_id   INT NOT NULL,
    montant          DOUBLE NOT NULL,
    methode          VARCHAR(50) NOT NULL COMMENT 'CARTE_BANCAIRE | ESPECES | VIREMENT | FLOUCI',
    statut           VARCHAR(30) NOT NULL DEFAULT 'PAYE' COMMENT 'PAYE | REMBOURSE',
    reference_code   VARCHAR(100) NOT NULL,
    nom_carte        VARCHAR(100) DEFAULT NULL,
    quatre_derniers  VARCHAR(4)   DEFAULT NULL,
    date_paiement    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_paiement_inscription
        FOREIGN KEY (inscription_id) REFERENCES inscription(id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
