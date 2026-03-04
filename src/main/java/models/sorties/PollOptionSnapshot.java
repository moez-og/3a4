package models.sorties;

/**
 * Snapshot (read-model) d'une option de sondage.
 * Utilisé côté UI pour afficher le texte, le nombre de votes et le pourcentage.
 */
public class PollOptionSnapshot {

    private int id;
    private String text;
    private int votes;
    /**
     * Fraction entre 0 et 1 (ex: 0.42 = 42%).
     */
    private double percent;

    public PollOptionSnapshot() {
    }

    public PollOptionSnapshot(int id, String text, int votes, double percent) {
        this.id = id;
        this.text = text;
        this.votes = votes;
        this.percent = percent;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public int getVotes() {
        return votes;
    }

    public void setVotes(int votes) {
        this.votes = votes;
    }

    public double getPercent() {
        return percent;
    }

    public void setPercent(double percent) {
        this.percent = percent;
    }
}
