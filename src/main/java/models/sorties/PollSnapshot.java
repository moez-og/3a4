package models.sorties;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Snapshot (read-model) d'un sondage pour l'affichage UI.
 * Contient les options, les votes et la sélection de l'utilisateur courant.
 */
public class PollSnapshot {

    private int id;
    private int annonceId;
    private String question;
    private int createdBy;
    private String createdByName;

    private boolean open;
    private boolean allowMulti;
    private boolean allowAddOptions;
    private boolean pinned;

    private int totalVoters;

    private final List<PollOptionSnapshot> options = new ArrayList<>();
    private final Set<Integer> myOptionIds = new HashSet<>();

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getAnnonceId() {
        return annonceId;
    }

    public void setAnnonceId(int annonceId) {
        this.annonceId = annonceId;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public int getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(int createdBy) {
        this.createdBy = createdBy;
    }

    public String getCreatedByName() {
        return createdByName;
    }

    public void setCreatedByName(String createdByName) {
        this.createdByName = createdByName;
    }

    public boolean isOpen() {
        return open;
    }

    public void setOpen(boolean open) {
        this.open = open;
    }

    public boolean isAllowMulti() {
        return allowMulti;
    }

    public void setAllowMulti(boolean allowMulti) {
        this.allowMulti = allowMulti;
    }

    public boolean isAllowAddOptions() {
        return allowAddOptions;
    }

    public void setAllowAddOptions(boolean allowAddOptions) {
        this.allowAddOptions = allowAddOptions;
    }

    public boolean isPinned() {
        return pinned;
    }

    public void setPinned(boolean pinned) {
        this.pinned = pinned;
    }

    public int getTotalVoters() {
        return totalVoters;
    }

    public void setTotalVoters(int totalVoters) {
        this.totalVoters = totalVoters;
    }

    public List<PollOptionSnapshot> getOptions() {
        return options;
    }

    public Set<Integer> getMyOptionIds() {
        return myOptionIds;
    }
}
