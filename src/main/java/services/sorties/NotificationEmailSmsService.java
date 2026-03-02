package services.sorties;

import models.sorties.AnnonceSortie;
import models.sorties.ParticipationSortie;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

/**
 * Service d'envoi de notifications EMAIL (Mailtrap) / SMS (Twilio)
 * lors de l'acceptation ou du refus d'une participation.
 */
public class NotificationEmailSmsService {

    // ═══════════════════════════════════════════════════════════════════
    //  🔑  CONFIGURATION EMAIL — Mailtrap SMTP
    // ═══════════════════════════════════════════════════════════════════
    private static final String SMTP_HOST = getenvOrDefault("APP_SMTP_HOST", "smtp.gmail.com");
    private static final int SMTP_PORT = parseIntOrDefault(getenvOrDefault("APP_SMTP_PORT", "587"), 587);
    private static final String SMTP_USER = getenvOrDefault("APP_SMTP_USER", "");
    private static final String SMTP_PASS = getenvOrDefault("APP_SMTP_PASS", "");
    private static final String EMAIL_FROM = getenvOrDefault("APP_EMAIL_FROM", SMTP_USER);

    // ═══════════════════════════════════════════════════════════════════
    //  🔑  CONFIGURATION SMS — Twilio
    // ═══════════════════════════════════════════════════════════════════
    private static final String TWILIO_ACCOUNT_SID = getenvOrDefault("TWILIO_ACCOUNT_SID", "");
    private static final String TWILIO_AUTH_TOKEN = getenvOrDefault("TWILIO_AUTH_TOKEN", "");
    private static final String TWILIO_FROM_NUMBER = getenvOrDefault("TWILIO_FROM_NUMBER", "");

    private static final DateTimeFormatter DT_FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'a' HH:mm");

    // ═══════════════════════════════════════════════════════════════════
    //  Methode principale — acceptation OU refus
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Envoie une notification au participant selon son contact prefere.
     *
     * @param participation  la demande de participation
     * @param annonce        l'annonce de sortie correspondante
     * @param accepte        true = acceptee, false = refusee
     */
    public void envoyerNotification(ParticipationSortie participation, AnnonceSortie annonce, boolean accepte) {
        System.out.println("[NotifService] envoyerNotification appele — accepte=" + accepte);

        if (participation == null || annonce == null) {
            System.err.println("[NotifService] participation ou annonce null");
            return;
        }

        String contactPrefer = safe(participation.getContactPrefer()).trim().toUpperCase();
        String contactValue  = safe(participation.getContactValue()).trim();

        System.out.println("[NotifService] contactPrefer=" + contactPrefer + " | contactValue=" + contactValue);

        if (contactValue.isEmpty()) {
            System.err.println("[NotifService] contact_value vide — aucun envoi.");
            return;
        }

        if ("EMAIL".equals(contactPrefer)) {
            envoyerEmail(contactValue, buildSujet(annonce, accepte), buildCorps(participation, annonce, accepte));
        } else if ("TELEPHONE".equals(contactPrefer)) {
            envoyerSms(contactValue, buildSms(participation, annonce, accepte));
        } else {
            System.err.println("[NotifService] contact_prefer inconnu : '" + contactPrefer + "'");
        }
    }

    /** Compatibilite avec l'ancien nom */
    public void envoyerConfirmation(ParticipationSortie participation, AnnonceSortie annonce) {
        envoyerNotification(participation, annonce, true);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Construction des messages
    // ═══════════════════════════════════════════════════════════════════

    private String buildSujet(AnnonceSortie annonce, boolean accepte) {
        return (accepte ? "[ACCEPTEE]" : "[REFUSEE]") + " Participation — " + safe(annonce.getTitre());
    }

    private String buildCorps(ParticipationSortie p, AnnonceSortie a, boolean accepte) {
        String date   = a.getDateSortie() != null ? DT_FMT.format(a.getDateSortie()) : "-";
        String budget = a.getBudgetMax() <= 0 ? "Aucun budget defini" : a.getBudgetMax() + " TND max";

        String intro  = accepte
                ? "Bonne nouvelle ! Votre demande de participation a ete ACCEPTEE."
                : "Nous sommes desoles. Votre demande de participation a ete REFUSEE.";
        String footer = accepte
                ? "Merci de vous presenter a l'heure au point de rendez-vous.\n\nBonne sortie !"
                : "Vous pouvez consulter d'autres annonces sur la plateforme.";

        return "Bonjour,\n\n"
                + intro + "\n\n"
                + "==========================================\n"
                + "  DETAILS DE LA SORTIE\n"
                + "==========================================\n"
                + "  Titre        : " + safe(a.getTitre())          + "\n"
                + "  Ville        : " + safe(a.getVille())          + "\n"
                + "  Lieu         : " + safe(a.getLieuTexte())      + "\n"
                + "  Rendez-vous  : " + safe(a.getPointRencontre()) + "\n"
                + "  Activite     : " + safe(a.getTypeActivite())   + "\n"
                + "  Date & heure : " + date                        + "\n"
                + "  Budget       : " + budget                      + "\n"
                + "  Places       : " + Math.max(1, p.getNbPlaces()) + " place(s)\n"
                + "==========================================\n\n"
                + footer + "\n\n"
                + "-- L'equipe de la plateforme\n";
    }

    private String buildSms(ParticipationSortie p, AnnonceSortie a, boolean accepte) {
        String date   = a.getDateSortie() != null ? DT_FMT.format(a.getDateSortie()) : "-";
        String statut = accepte ? "ACCEPTEE" : "REFUSEE";
        return "Participation " + statut + " !\n"
                + "Sortie: "  + safe(a.getTitre())          + "\n"
                + "Date: "    + date                         + "\n"
                + "Lieu: "    + safe(a.getVille())           + " - " + safe(a.getPointRencontre()) + "\n"
                + "Places: "  + Math.max(1, p.getNbPlaces()) + "\n"
                + "-- Sorties App";
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Envoi EMAIL via Mailtrap SMTP
    // ═══════════════════════════════════════════════════════════════════

    private void envoyerEmail(String destinataire, String sujet, String corps) {
        System.out.println("[NotifService] Tentative envoi email vers : " + destinataire);

        if (SMTP_USER.isBlank() || SMTP_PASS.isBlank() || EMAIL_FROM.isBlank()) {
            System.err.println("[NotifService] SMTP non configure (APP_SMTP_USER/APP_SMTP_PASS/APP_EMAIL_FROM). Email non envoye.");
            return;
        }

        Properties props = new Properties();
        props.put("mail.smtp.auth",            "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host",            SMTP_HOST);
        props.put("mail.smtp.port",            String.valueOf(SMTP_PORT));

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(SMTP_USER, SMTP_PASS);
            }
        });

        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(EMAIL_FROM));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(destinataire));
            message.setSubject(sujet);
            message.setText(corps);
            Transport.send(message);
            System.out.println("[NotifService] Email envoye a : " + destinataire);
        } catch (MessagingException e) {
            System.err.println("[NotifService] Echec envoi email : " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Envoi SMS via Twilio
    // ═══════════════════════════════════════════════════════════════════

    private void envoyerSms(String numeroDestinataire, String corps) {
        System.out.println("[NotifService] Tentative envoi SMS vers : " + numeroDestinataire);

        if (TWILIO_ACCOUNT_SID.isBlank() || TWILIO_AUTH_TOKEN.isBlank() || TWILIO_FROM_NUMBER.isBlank()) {
            System.err.println("[NotifService] Twilio non configure (TWILIO_ACCOUNT_SID/TWILIO_AUTH_TOKEN/TWILIO_FROM_NUMBER). SMS non envoye.");
            return;
        }

        String numero = numeroDestinataire.trim();
        if (!numero.startsWith("+")) {
            // Ajoute indicatif Tunisie si absent
            numero = "+216" + numero.replaceAll("[^0-9]", "");
        }

        try {
            com.twilio.Twilio.init(TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN);
            com.twilio.rest.api.v2010.account.Message msg =
                    com.twilio.rest.api.v2010.account.Message.creator(
                            new com.twilio.type.PhoneNumber(numero),
                            new com.twilio.type.PhoneNumber(TWILIO_FROM_NUMBER),
                            corps
                    ).create();
            System.out.println("[NotifService] SMS envoye a : " + numero + " | SID: " + msg.getSid());
        } catch (Exception e) {
            System.err.println("[NotifService] Echec envoi SMS : " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Utilitaires
    // ═══════════════════════════════════════════════════════════════════

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static String getenvOrDefault(String key, String def) {
        try {
            String v = System.getenv(key);
            return v == null ? def : v;
        } catch (Exception e) {
            return def;
        }
    }

    private static int parseIntOrDefault(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return def;
        }
    }
}