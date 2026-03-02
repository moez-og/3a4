package models.evenements;

import java.time.LocalDateTime;

/**
 * Modèle Paiement — correspond à la table "paiement" en base.
 *
 * Méthodes possibles : CARTE_BANCAIRE, ESPECES, VIREMENT, FLOUCI
 * Statuts possibles  : PAYE, REMBOURSE
 */
public class Paiement {

    private int id;
    private int inscriptionId;
    private double montant;
    private String methode;         // CARTE_BANCAIRE | ESPECES | VIREMENT | FLOUCI
    private String statut;          // PAYE | REMBOURSE
    private String referenceCode;
    private String nomCarte;        // nullable
    private String quatreDerniers;  // nullable — 4 derniers chiffres de la carte
    private LocalDateTime datePaiement;

    public Paiement() {}

    public Paiement(int id, int inscriptionId, double montant, String methode,
                    String statut, String referenceCode, String nomCarte,
                    String quatreDerniers, LocalDateTime datePaiement) {
        this.id = id;
        this.inscriptionId = inscriptionId;
        this.montant = montant;
        this.methode = methode;
        this.statut = statut;
        this.referenceCode = referenceCode;
        this.nomCarte = nomCarte;
        this.quatreDerniers = quatreDerniers;
        this.datePaiement = datePaiement;
    }

    // ── Getters / Setters ──

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getInscriptionId() { return inscriptionId; }
    public void setInscriptionId(int inscriptionId) { this.inscriptionId = inscriptionId; }

    public double getMontant() { return montant; }
    public void setMontant(double montant) { this.montant = montant; }

    public String getMethode() { return methode; }
    public void setMethode(String methode) { this.methode = methode; }

    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }

    public String getReferenceCode() { return referenceCode; }
    public void setReferenceCode(String referenceCode) { this.referenceCode = referenceCode; }

    public String getNomCarte() { return nomCarte; }
    public void setNomCarte(String nomCarte) { this.nomCarte = nomCarte; }

    public String getQuatreDerniers() { return quatreDerniers; }
    public void setQuatreDerniers(String quatreDerniers) { this.quatreDerniers = quatreDerniers; }

    public LocalDateTime getDatePaiement() { return datePaiement; }
    public void setDatePaiement(LocalDateTime datePaiement) { this.datePaiement = datePaiement; }

    @Override
    public String toString() {
        return "Paiement{" +
                "id=" + id +
                ", inscriptionId=" + inscriptionId +
                ", montant=" + montant +
                ", methode='" + methode + '\'' +
                ", statut='" + statut + '\'' +
                ", referenceCode='" + referenceCode + '\'' +
                ", datePaiement=" + datePaiement +
                '}';
    }
}
