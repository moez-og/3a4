package services.sorties;

import models.sorties.AnnonceSortie;
import models.sorties.ParticipationSortie;

import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * Service d'envoi de notifications EMAIL (Mailtrap) / SMS (Twilio)
 * lors de l'acceptation ou du refus d'une participation.
 */
public class NotificationEmailSmsService {

    // Note: on lit d'abord les System Properties (-DKEY=...), puis les variables d'environnement,
    // puis un fichier local (app-secrets.properties ou ~/.app-secrets.properties).
    // IMPORTANT: la config est relue au moment de l'envoi (moins fragile que des static final).
    private static volatile boolean LOGGED_NO_SECRETS = false;

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
     * @return true si l'envoi a reussi, false sinon
     */
    public boolean envoyerNotification(ParticipationSortie participation, AnnonceSortie annonce, boolean accepte) {
        System.out.println("[NotifService] envoyerNotification appele — accepte=" + accepte);

        if (participation == null || annonce == null) {
            System.err.println("[NotifService] participation ou annonce null");
            return false;
        }

        String contactPrefer = safe(participation.getContactPrefer()).trim().toUpperCase();
        String contactValue  = safe(participation.getContactValue()).trim();

        System.out.println("[NotifService] contactPrefer=" + contactPrefer + " | contactValue=" + contactValue);

        if (contactValue.isEmpty()) {
            System.err.println("[NotifService] contact_value vide — aucun envoi.");
            return false;
        }

        if ("EMAIL".equals(contactPrefer)) {
            String sujet = buildSujet(annonce, accepte);
            String corpsText = buildCorps(participation, annonce, accepte);
            String corpsHtml = buildCorpsHtml(participation, annonce, accepte);
            return envoyerEmail(contactValue, sujet, corpsText, corpsHtml);
        } else if ("TELEPHONE".equals(contactPrefer)) {
            return envoyerSms(contactValue, buildSms(participation, annonce, accepte));
        } else {
            System.err.println("[NotifService] contact_prefer inconnu : '" + contactPrefer + "'");
            return false;
        }
    }

    /** Compatibilite avec l'ancien nom */
    public boolean envoyerConfirmation(ParticipationSortie participation, AnnonceSortie annonce) {
        return envoyerNotification(participation, annonce, true);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Construction des messages
    // ═══════════════════════════════════════════════════════════════════

    private String buildSujet(AnnonceSortie annonce, boolean accepte) {
        return (accepte ? "[ACCEPTÉE]" : "[REFUSÉE]") + " Participation — " + safe(annonce.getTitre());
    }

    private String buildCorps(ParticipationSortie p, AnnonceSortie a, boolean accepte) {
        String date   = a.getDateSortie() != null ? DT_FMT.format(a.getDateSortie()) : "-";
        String budget = a.getBudgetMax() <= 0 ? "Aucun budget défini" : a.getBudgetMax() + " TND max";

        String intro  = accepte
                ? "Bonne nouvelle ! Votre demande de participation a été ACCEPTÉE."
                : "Nous sommes désolés. Votre demande de participation a été REFUSÉE.";
        String footer = accepte
                ? "Merci de vous présenter à l'heure au point de rendez-vous.\n\nBonne sortie !"
                : "Vous pouvez consulter d'autres annonces sur la plateforme.";

        return "Bonjour,\n\n"
                + intro + "\n\n"
                + "==========================================\n"
                + "  DÉTAILS DE LA SORTIE\n"
                + "==========================================\n"
                + "  Titre        : " + safe(a.getTitre())          + "\n"
                + "  Ville        : " + safe(a.getVille())          + "\n"
                + "  Lieu         : " + safe(a.getLieuTexte())      + "\n"
                + "  Rendez-vous  : " + safe(a.getPointRencontre()) + "\n"
                + "  Activité     : " + safe(a.getTypeActivite())   + "\n"
                + "  Date & heure : " + date                        + "\n"
                + "  Budget       : " + budget                      + "\n"
                + "  Places       : " + Math.max(1, p.getNbPlaces()) + " place(s)\n"
                + "==========================================\n\n"
                + footer + "\n\n"
                + "-- L'équipe de la plateforme\n";
    }

    private String buildCorpsHtml(ParticipationSortie p, AnnonceSortie a, boolean accepte) {
        String date = a.getDateSortie() != null ? DT_FMT.format(a.getDateSortie()) : "—";
        String budget = a.getBudgetMax() <= 0 ? "Aucun budget défini" : (a.getBudgetMax() + " TND max");

        String statusLabel = accepte ? "ACCEPTÉE" : "REFUSÉE";
        // Palette de l'application: bleu admin (#1a3a5c) pour accepté, rouge pour refusé.
        String accent = accepte ? "#1a3a5c" : "#dc2626";
        String intro = accepte
                ? "Bonne nouvelle ! Votre demande de participation a été <b>ACCEPTÉE</b>."
                : "Nous sommes désolés. Votre demande de participation a été <b>REFUSÉE</b>.";
        String footer = accepte
                ? "Merci de vous présenter à l'heure au point de rendez-vous.<br/><br/><b>Bonne sortie !</b>"
                : "Vous pouvez consulter d'autres annonces sur la plateforme.";

        String titre = escapeHtml(safe(a.getTitre()));
        String ville = escapeHtml(safe(a.getVille()));
        String lieu = escapeHtml(safe(a.getLieuTexte()));
        String rdv = escapeHtml(safe(a.getPointRencontre()));
        String activite = escapeHtml(safe(a.getTypeActivite()));
        String places = String.valueOf(Math.max(1, p.getNbPlaces()));

        // Simple, readable HTML with inline CSS (best compatibility).
        return "<!doctype html>"
                + "<html><head><meta charset='utf-8'></head>"
                + "<body style='margin:0;padding:0;background:#f4f6fb;font-family:Arial,Helvetica,sans-serif;color:#111827;'>"
                + "<div style='max-width:680px;margin:0 auto;padding:24px 12px;'>"
                + "  <div style='background:#ffffff;border:1px solid #e5e7eb;border-radius:14px;overflow:hidden;'>"
                + "    <div style='padding:18px 20px;background:" + accent + ";color:#fff;'>"
                + "      <div style='font-size:14px;opacity:0.95;letter-spacing:0.3px;'>Notification participation</div>"
                + "      <div style='font-size:20px;font-weight:800;margin-top:4px;'>" + titre + "</div>"
                + "      <div style='margin-top:10px;display:inline-block;background:rgba(255,255,255,0.18);padding:6px 10px;border-radius:999px;font-weight:700;font-size:12px;'>"
                + "        " + statusLabel + ""
                + "      </div>"
                + "    </div>"
                + "    <div style='padding:18px 20px;'>"
                + "      <div style='font-size:14px;line-height:1.55;color:#111827;'>Bonjour,<br/><br/>" + intro + "</div>"
                + "      <div style='height:14px;'></div>"
                + "      <div style='font-size:13px;font-weight:800;color:#0f2a44;margin-bottom:10px;'>DÉTAILS DE LA SORTIE</div>"
                + "      <table cellpadding='0' cellspacing='0' style='width:100%;border-collapse:collapse;font-size:13px;'>"
                + rowHtml("Ville", ville)
                + rowHtml("Lieu", lieu)
                + rowHtml("Rendez-vous", rdv)
                + rowHtml("Activité", activite)
                + rowHtml("Date & heure", escapeHtml(date))
                + rowHtml("Budget", escapeHtml(budget))
                + rowHtml("Places", escapeHtml(places) + " place(s)")
                + "      </table>"
                + "      <div style='height:14px;'></div>"
                + "      <div style='font-size:13px;line-height:1.55;color:#111827;'>" + footer + "</div>"
                + "      <div style='height:18px;'></div>"
                + "      <div style='font-size:12px;color:#6b7280;border-top:1px solid #eef2f7;padding-top:12px;'>"
                + "        — L'équipe de la plateforme"
                + "      </div>"
                + "    </div>"
                + "  </div>"
                + "</div>"
                + "</body></html>";
    }

    private static String rowHtml(String label, String value) {
        String v = (value == null || value.isBlank()) ? "—" : value;
        return "<tr>"
                + "<td style='padding:10px 10px;border-top:1px solid #eef2f7;color:#6b7280;width:150px;vertical-align:top;font-weight:700;'>" + escapeHtml(label) + "</td>"
                + "<td style='padding:10px 10px;border-top:1px solid #eef2f7;color:#111827;vertical-align:top;word-break:break-word;'>" + v + "</td>"
                + "</tr>";
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String buildSms(ParticipationSortie p, AnnonceSortie a, boolean accepte) {
        String date   = a.getDateSortie() != null ? DT_FMT.format(a.getDateSortie()) : "-";
        String statut = accepte ? "ACCEPTÉE" : "REFUSÉE";
        return "=== fin tokhrej ===\n"
                + "Participation " + statut + " !\n"
                + "Sortie: "  + safe(a.getTitre())          + "\n"
                + "Date: "    + date                         + "\n"
                + "Lieu: "    + safe(a.getVille())           + " - " + safe(a.getPointRencontre()) + "\n"
                + "Places: "  + Math.max(1, p.getNbPlaces()) + "\n"
                + "-- fin tokhrej";
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Envoi EMAIL via Mailtrap SMTP
    // ═══════════════════════════════════════════════════════════════════

    private boolean envoyerEmail(String destinataire, String sujet, String corpsText, String corpsHtml) {
        System.out.println("[NotifService] Tentative envoi email vers : " + destinataire);

        LoadedProps loaded = loadFileProps();
        Properties fileProps = loaded.props;

        String smtpHost = getConfig("APP_SMTP_HOST", "sandbox.smtp.mailtrap.io", fileProps);
        int smtpPort = parseIntOrDefault(getConfig("APP_SMTP_PORT", "587", fileProps), 587);
        String smtpUser = getConfig("APP_SMTP_USER", "", fileProps);
        String smtpPass = getConfig("APP_SMTP_PASS", "", fileProps);
        String emailFrom = getConfig("APP_EMAIL_FROM", smtpUser, fileProps);
        boolean smtpDebug = parseBool(getConfig("APP_SMTP_DEBUG", "false", fileProps));

        if (smtpUser.isBlank() || smtpPass.isBlank()) {
            System.err.println("[NotifService] SMTP non configure. Email non envoye.");
            System.err.println("[NotifService] Astuce: copie app-secrets.properties.example -> app-secrets.properties (racine du projet) puis remplis APP_SMTP_USER/PASS.");
            System.err.println("[NotifService] Fichiers secrets cherches: " + loaded.searchedSummary);
            if (loaded.loadedFrom != null) {
                System.err.println("[NotifService] Fichier secrets charge: " + loaded.loadedFrom.toAbsolutePath());
            }
            System.err.println("[NotifService] Ou lance avec :");
            System.err.println("[NotifService]   -DAPP_SMTP_HOST=... -DAPP_SMTP_PORT=587 -DAPP_SMTP_USER=... -DAPP_SMTP_PASS=... -DAPP_EMAIL_FROM=...");
            return false;
        }

        String from = emailFrom.isBlank() ? smtpUser : emailFrom;

        System.out.println("[NotifService] SMTP host=" + smtpHost + " port=" + smtpPort + " user=" + smtpUser + " from=" + from);

        Properties props = new Properties();
        props.put("mail.smtp.auth",            "true");
        boolean useSsl = smtpPort == 465;
        props.put("mail.smtp.ssl.enable",      String.valueOf(useSsl));
        props.put("mail.smtp.starttls.enable", String.valueOf(!useSsl));
        // Many SMTP providers (e.g. Gmail on 587) require STARTTLS, not just "enable".
        if (!useSsl) {
            props.put("mail.smtp.starttls.required", "true");
        }
        props.put("mail.smtp.host",            smtpHost);
        props.put("mail.smtp.port",            String.valueOf(smtpPort));

        // Helps with some providers/Java installations (TLS handshake / cert trust).
        props.put("mail.smtp.ssl.trust", smtpHost);
        props.put("mail.smtp.ssl.protocols", "TLSv1.2");

        // Timeouts pour éviter que l'UI "bloque" si le SMTP ne répond pas.
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout",           "10000");
        props.put("mail.smtp.writetimeout",      "10000");

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(smtpUser, smtpPass);
            }
        });

        if (smtpDebug) {
            session.setDebug(true);
            System.out.println("[NotifService] SMTP debug actif (APP_SMTP_DEBUG=true)");
        }

        try {
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(from));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(destinataire));
            message.setSubject(sujet, "UTF-8");

            // Send multipart/alternative: plain text + HTML (better deliverability & compatibility)
            MimeBodyPart textPart = new MimeBodyPart();
            textPart.setText(safe(corpsText), "UTF-8");

            MimeBodyPart htmlPart = new MimeBodyPart();
            String html = safe(corpsHtml);
            if (html.isBlank()) {
                html = "<pre style='font-family:Arial,Helvetica,sans-serif;white-space:pre-wrap'>" + escapeHtml(corpsText) + "</pre>";
            }
            htmlPart.setContent(html, "text/html; charset=UTF-8");

            MimeMultipart mp = new MimeMultipart("alternative");
            mp.addBodyPart(textPart);
            mp.addBodyPart(htmlPart);
            message.setContent(mp);

            Transport.send(message);
            System.out.println("[NotifService] Email envoye a : " + destinataire);
            return true;
        } catch (MessagingException e) {
            System.err.println("[NotifService] Echec envoi email : " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Envoi SMS (désactivé si aucun provider n'est intégré)
    // ═══════════════════════════════════════════════════════════════════

    private boolean envoyerSms(String numeroDestinataire, String corps) {
        // Le projet n'embarque pas de SDK SMS (Twilio a été retiré des dépendances).
        // On garde la méthode pour compatibilité et on ne bloque pas le flux métier.

        String numero = safe(numeroDestinataire).trim();
        if (!numero.isEmpty() && !numero.startsWith("+")) {
            // Ajoute indicatif Tunisie si absent
            numero = "+216" + numero.replaceAll("[^0-9]", "");
        }

        System.out.println("[NotifService] SMS non envoye (provider SMS non configure). Dest=" + numero + " | Msg=" + safe(corps));
        return true;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Utilitaires
    // ═══════════════════════════════════════════════════════════════════

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static String getConfig(String key, String def, Properties fileProps) {
        String direct = getNonBlankConfigValue(key);
        if (direct != null) return direct;

        if (fileProps != null) {
            try {
                String fileVal = fileProps.getProperty(key);
                if (fileVal != null) {
                    String trimmed = fileVal.trim();
                    if (!trimmed.isBlank()) return trimmed;
                }
            } catch (Exception ignored) {
                // ignore
            }
        }

        return def;
    }

    private static String getNonBlankConfigValue(String key) {
        try {
            String sys = System.getProperty(key);
            if (sys != null) {
                String t = sys.trim();
                if (!t.isBlank()) return t;
            }
        } catch (Exception ignored) {
            // ignore
        }
        try {
            String env = System.getenv(key);
            if (env != null) {
                String t = env.trim();
                if (!t.isBlank()) return t;
            }
        } catch (Exception ignored) {
            // ignore
        }

        return null;
    }

    private static final class LoadedProps {
        final Properties props;
        final Path loadedFrom;
        final String searchedSummary;

        private LoadedProps(Properties props, Path loadedFrom, String searchedSummary) {
            this.props = props;
            this.loadedFrom = loadedFrom;
            this.searchedSummary = searchedSummary;
        }
    }

    private static LoadedProps loadFileProps() {
        Properties p = new Properties();

        // Priority:
        //  1) -DAPP_SECRETS_FILE=/path/to/file.properties (or env var)
        //  2) ./app-secrets.properties (working directory)
        //  3) ~/.app-secrets.properties (user home)
        String explicit = getNonBlankConfigValue("APP_SECRETS_FILE");

        Path[] candidates;
        if (explicit != null) {
            candidates = new Path[] { Paths.get(explicit) };
        } else {
            candidates = buildDefaultSecretCandidates();
        }

        StringBuilder searched = new StringBuilder();
        for (int i = 0; i < candidates.length; i++) {
            if (i > 0) searched.append(" | ");
            searched.append(candidates[i] == null ? "<null>" : candidates[i].toAbsolutePath());
        }

        Path loadedFrom = null;
        for (Path path : candidates) {
            try {
                if (path == null) continue;
                if (!Files.exists(path)) continue;
                if (!Files.isRegularFile(path)) continue;

                // IMPORTANT: preserve FIRST occurrence of keys to avoid confusion when the file
                // accidentally contains duplicate lines (common when users append configs).
                Properties loadedProps = loadPropertiesPreserveFirst(path);
                p.putAll(loadedProps);
                loadedFrom = path;
                System.out.println("[NotifService] Secrets charges depuis: " + path.toAbsolutePath());
                break; // first existing file wins
            } catch (Exception e) {
                System.err.println("[NotifService] Impossible de lire le fichier secrets: " + path + " | " + e.getMessage());
            }
        }

        // If explicit file was provided but not found, always say so (helps a lot on Windows).
        if (explicit != null && loadedFrom == null) {
            System.err.println("[NotifService] APP_SECRETS_FILE pointe vers un fichier introuvable: " + Paths.get(explicit).toAbsolutePath());
        }

        // If no file found at all, warn once (to avoid spam).
        if (explicit == null && loadedFrom == null && !LOGGED_NO_SECRETS) {
            LOGGED_NO_SECRETS = true;
            System.err.println("[NotifService] Aucun fichier secrets trouve. Cherche: " + searched);
            System.err.println("[NotifService] Conseil: cree app-secrets.properties a la racine (copie depuis app-secrets.properties.example)");
        }

        return new LoadedProps(p, loadedFrom, searched.toString());
    }

    /**
     * Load .properties while preserving the FIRST occurrence of a key.
     * Later duplicates are ignored and reported (helps a lot on Windows).
     */
    private static Properties loadPropertiesPreserveFirst(Path path) throws Exception {
        Properties props = new Properties();
        List<String> dupKeys = new ArrayList<>();

        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        for (String raw : lines) {
            if (raw == null) continue;
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("#") || line.startsWith("!")) continue;

            int sep = findSeparatorIndex(line);
            if (sep <= 0) continue;
            String key = line.substring(0, sep).trim();
            String value = line.substring(sep + 1).trim();
            if (key.isEmpty()) continue;

            if (props.containsKey(key)) {
                dupKeys.add(key);
                continue;
            }
            props.setProperty(key, value);
        }

        if (!dupKeys.isEmpty()) {
            System.err.println("[NotifService] ATTENTION: cles dupliquees dans " + path.toAbsolutePath() + " (premiere occurrence gardee): " + String.join(", ", dupKeys));
        }

        return props;
    }

    private static int findSeparatorIndex(String line) {
        // Supports key=value and key:value. (Does not implement full Java .properties escaping; not needed here.)
        int eq = line.indexOf('=');
        int colon = line.indexOf(':');
        if (eq < 0) return colon;
        if (colon < 0) return eq;
        return Math.min(eq, colon);
    }

    /**
     * Build default candidate locations for app-secrets.properties.
     *
     * Why: in JavaFX apps, the working directory can change (e.g. launched from target/, from a jar,
     * or by double-click). In that case, ./app-secrets.properties won't be found even if it exists
     * at the project root.
     */
    private static Path[] buildDefaultSecretCandidates() {
        Set<Path> unique = new LinkedHashSet<>();

        // 1) Working directory
        unique.add(Paths.get("app-secrets.properties"));

        // 2) Near the running code (jar or classes folder) + parents
        try {
            URL loc = NotificationEmailSmsService.class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation();
            if (loc != null) {
                Path base = Paths.get(loc.toURI());
                if (Files.isRegularFile(base)) {
                    // jar -> use its directory
                    base = base.getParent();
                }
                Path cursor = base;
                for (int i = 0; i < 6 && cursor != null; i++) {
                    unique.add(cursor.resolve("app-secrets.properties"));
                    cursor = cursor.getParent();
                }
            }
        } catch (Exception ignored) {
            // best effort
        }

        // 3) User home fallback
        unique.add(Paths.get(System.getProperty("user.home", "."), ".app-secrets.properties"));

        return unique.toArray(new Path[0]);
    }

    private static boolean parseBool(String v) {
        if (v == null) return false;
        String s = v.trim().toLowerCase();
        return s.equals("1") || s.equals("true") || s.equals("yes") || s.equals("y") || s.equals("on");
    }

    private static int parseIntOrDefault(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return def;
        }
    }
}