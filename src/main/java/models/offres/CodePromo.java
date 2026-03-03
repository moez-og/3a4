package models.offres;

import java.sql.Date;

public class CodePromo {
    private int id;
    private int offre_id;
    private int user_id;
    private String qr_image_url;
    private Date date_generation;
    private Date date_expiration;
    private String statut;

    public CodePromo() {
    }

    public CodePromo(int id, int offre_id, String qr_image_url, Date date_generation, Date date_expiration, String statut) {
        this.id = id;
        this.offre_id = offre_id;
        this.qr_image_url = qr_image_url;
        this.date_generation = date_generation;
        this.date_expiration = date_expiration;
        this.statut = statut;
    }

    public CodePromo(int id, int offre_id, int user_id, String qr_image_url, Date date_generation, Date date_expiration, String statut) {
        this.id = id;
        this.offre_id = offre_id;
        this.user_id = user_id;
        this.qr_image_url = qr_image_url;
        this.date_generation = date_generation;
        this.date_expiration = date_expiration;
        this.statut = statut;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getOffre_id() {
        return offre_id;
    }

    public void setOffre_id(int offre_id) {
        this.offre_id = offre_id;
    }

    public int getUser_id() {
        return user_id;
    }

    public void setUser_id(int user_id) {
        this.user_id = user_id;
    }

    public String getQr_image_url() {
        return qr_image_url;
    }

    public void setQr_image_url(String qr_image_url) {
        this.qr_image_url = qr_image_url;
    }

    public Date getDate_generation() {
        return date_generation;
    }

    public void setDate_generation(Date date_generation) {
        this.date_generation = date_generation;
    }

    public Date getDate_expiration() {
        return date_expiration;
    }

    public void setDate_expiration(Date date_expiration) {
        this.date_expiration = date_expiration;
    }

    public String getStatut() {
        return statut;
    }

    public void setStatut(String statut) {
        this.statut = statut;
    }
}
