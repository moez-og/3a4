package models.lieux;

/**
 * Représente les horaires d'ouverture d'un lieu pour un jour donné.
 * Un lieu peut avoir jusqu'à 2 plages horaires par jour (ex: matin + après-midi).
 */
public class LieuHoraire {

    private int id;
    private int lieuId;
    private String jour; // LUNDI, MARDI, MERCREDI, JEUDI, VENDREDI, SAMEDI, DIMANCHE
    private boolean ouvert;

    private String heureOuverture1; // format HH:mm  ex: "09:00"
    private String heureFermeture1; // format HH:mm  ex: "13:00"
    private String heureOuverture2; // optional  ex: "15:00"
    private String heureFermeture2; // optional  ex: "22:00"

    public LieuHoraire() {}

    public LieuHoraire(String jour, boolean ouvert,
                       String heureOuverture1, String heureFermeture1,
                       String heureOuverture2, String heureFermeture2) {
        this.jour = jour;
        this.ouvert = ouvert;
        this.heureOuverture1 = heureOuverture1;
        this.heureFermeture1 = heureFermeture1;
        this.heureOuverture2 = heureOuverture2;
        this.heureFermeture2 = heureFermeture2;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getLieuId() { return lieuId; }
    public void setLieuId(int lieuId) { this.lieuId = lieuId; }

    public String getJour() { return jour; }
    public void setJour(String jour) { this.jour = jour; }

    public boolean isOuvert() { return ouvert; }
    public void setOuvert(boolean ouvert) { this.ouvert = ouvert; }

    public String getHeureOuverture1() { return heureOuverture1; }
    public void setHeureOuverture1(String heureOuverture1) { this.heureOuverture1 = heureOuverture1; }

    public String getHeureFermeture1() { return heureFermeture1; }
    public void setHeureFermeture1(String heureFermeture1) { this.heureFermeture1 = heureFermeture1; }

    public String getHeureOuverture2() { return heureOuverture2; }
    public void setHeureOuverture2(String heureOuverture2) { this.heureOuverture2 = heureOuverture2; }

    public String getHeureFermeture2() { return heureFermeture2; }
    public void setHeureFermeture2(String heureFermeture2) { this.heureFermeture2 = heureFermeture2; }

    @Override
    public String toString() {
        if (!ouvert) return jour + ": Fermé";
        StringBuilder sb = new StringBuilder(jour + ": " + heureOuverture1 + " - " + heureFermeture1);
        if (heureOuverture2 != null && !heureOuverture2.isBlank()) {
            sb.append(" / ").append(heureOuverture2).append(" - ").append(heureFermeture2);
        }
        return sb.toString();
    }
}
