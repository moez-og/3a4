package models.evenements;

import java.time.LocalDate;

public class Ticket {

    private int id;
    private int inscriptionId;
    private LocalDate date; // colonne "date" dans ta DB

    public Ticket() {}

    public Ticket(int id, int inscriptionId, LocalDate date) {
        this.id = id;
        this.inscriptionId = inscriptionId;
        this.date = date;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getInscriptionId() { return inscriptionId; }
    public void setInscriptionId(int inscriptionId) { this.inscriptionId = inscriptionId; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    @Override
    public String toString() {
        return "Ticket{" +
                "id=" + id +
                ", inscriptionId=" + inscriptionId +
                ", date=" + date +
                '}';
    }
}
