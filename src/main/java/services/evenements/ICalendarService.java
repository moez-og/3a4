package services.evenements;

import models.evenements.Evenement;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * ICalendarService — Export d'événements au format ICS (iCalendar RFC 5545).
 *
 * ════════════════════════════════════════════════════════════════
 *  100% GRATUIT — Aucune API, aucun compte, aucune clé nécessaire.
 *
 *  Le format ICS est le standard universel pour les calendriers.
 *  Les fichiers .ics peuvent être importés dans :
 *    • Google Calendar (gratuit)
 *    • Microsoft Outlook
 *    • Apple Calendar (iCal)
 *    • Thunderbird
 *    • Tout autre logiciel de calendrier compatible RFC 5545
 *
 *  Utilisation :
 *    1. Exporter les événements → génère un fichier .ics
 *    2. Double-cliquer sur le fichier .ics → s'ouvre automatiquement
 *       dans l'application de calendrier par défaut du système
 *    3. L'utilisateur peut choisir d'importer dans son calendrier
 * ════════════════════════════════════════════════════════════════
 */
public class ICalendarService {

    private static final DateTimeFormatter ICS_DATE_FMT =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");

    private static final String PRODID = "-//FinTokhrej//Events//FR";

    // ─────────────────────────────────────────────────────────────
    //  SINGLETON
    // ─────────────────────────────────────────────────────────────

    private static ICalendarService instance;

    public static ICalendarService getInstance() {
        if (instance == null) {
            instance = new ICalendarService();
        }
        return instance;
    }

    private ICalendarService() {}

    // ─────────────────────────────────────────────────────────────
    //  EXPORTER UN SEUL ÉVÉNEMENT → fichier .ics
    // ─────────────────────────────────────────────────────────────

    /**
     * Exporte un seul événement au format ICS.
     *
     * @param ev       l'événement à exporter
     * @param lieuName nom du lieu (pour LOCATION)
     * @param file     fichier .ics de destination
     * @return true si l'export a réussi
     */
    public boolean exportEvent(Evenement ev, String lieuName, File file) {
        try {
            String ics = buildIcsCalendar(List.of(ev), lieuName != null ? 
                    id -> lieuName : id -> "");
            writeToFile(ics, file);
            return true;
        } catch (Exception e) {
            System.err.println("[ICalendar] Erreur export: " + e.getMessage());
            return false;
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  EXPORTER PLUSIEURS ÉVÉNEMENTS → fichier .ics
    // ─────────────────────────────────────────────────────────────

    /**
     * Exporte une liste d'événements au format ICS.
     *
     * @param events       liste des événements
     * @param lieuResolver fonction qui résout le nom du lieu à partir de lieuId
     * @param file         fichier .ics de destination
     * @return résultat de l'export
     */
    public ExportResult exportEvents(List<Evenement> events,
                                     java.util.function.Function<Integer, String> lieuResolver,
                                     File file) {
        ExportResult result = new ExportResult();
        try {
            String ics = buildIcsCalendar(events, lieuResolver);
            writeToFile(ics, file);
            result.exported = events.size();
            result.filePath = file.getAbsolutePath();
            result.success = true;
        } catch (Exception e) {
            result.failed = events.size();
            result.error = e.getMessage();
            result.success = false;
        }
        return result;
    }

    // ─────────────────────────────────────────────────────────────
    //  GÉNÉRER LE CONTENU ICS (String)
    // ─────────────────────────────────────────────────────────────

    /**
     * Génère le contenu ICS complet pour une liste d'événements.
     */
    public String buildIcsCalendar(List<Evenement> events,
                                   java.util.function.Function<Integer, String> lieuResolver) {
        StringBuilder sb = new StringBuilder();

        // En-tête iCalendar
        sb.append("BEGIN:VCALENDAR\r\n");
        sb.append("VERSION:2.0\r\n");
        sb.append("PRODID:").append(PRODID).append("\r\n");
        sb.append("CALSCALE:GREGORIAN\r\n");
        sb.append("METHOD:PUBLISH\r\n");
        sb.append("X-WR-CALNAME:FinTokhrej - Événements\r\n");
        sb.append("X-WR-TIMEZONE:Africa/Tunis\r\n");

        // Timezone definition
        sb.append("BEGIN:VTIMEZONE\r\n");
        sb.append("TZID:Africa/Tunis\r\n");
        sb.append("BEGIN:STANDARD\r\n");
        sb.append("DTSTART:19700101T000000\r\n");
        sb.append("TZOFFSETFROM:+0100\r\n");
        sb.append("TZOFFSETTO:+0100\r\n");
        sb.append("TZNAME:CET\r\n");
        sb.append("END:STANDARD\r\n");
        sb.append("END:VTIMEZONE\r\n");

        // Chaque événement
        for (Evenement ev : events) {
            sb.append(buildVEvent(ev, lieuResolver));
        }

        sb.append("END:VCALENDAR\r\n");
        return sb.toString();
    }

    /**
     * Construit un bloc VEVENT pour un événement.
     */
    private String buildVEvent(Evenement ev,
                               java.util.function.Function<Integer, String> lieuResolver) {
        StringBuilder sb = new StringBuilder();

        String uid = "fintokhrej-event-" + ev.getId() + "@fintokhrej.tn";
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime debut = ev.getDateDebut() != null ? ev.getDateDebut() : now;
        LocalDateTime fin = ev.getDateFin() != null ? ev.getDateFin() : debut.plusHours(2);
        String lieuName = lieuResolver != null ? lieuResolver.apply(ev.getLieuId()) : "";

        sb.append("BEGIN:VEVENT\r\n");
        sb.append("UID:").append(uid).append("\r\n");
        sb.append("DTSTAMP:").append(formatIcsDate(now)).append("\r\n");
        sb.append("DTSTART;TZID=Africa/Tunis:").append(formatIcsDate(debut)).append("\r\n");
        sb.append("DTEND;TZID=Africa/Tunis:").append(formatIcsDate(fin)).append("\r\n");
        sb.append("SUMMARY:").append(escapeIcs(safeStr(ev.getTitre()))).append("\r\n");

        // Description détaillée
        StringBuilder desc = new StringBuilder();
        if (ev.getDescription() != null && !ev.getDescription().isBlank()) {
            desc.append(ev.getDescription()).append("\\n\\n");
        }
        desc.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━\\n");
        desc.append("Statut: ").append(safeStr(ev.getStatut())).append("\\n");
        desc.append("Type: ").append(safeStr(ev.getType())).append("\\n");
        desc.append("Capacité: ").append(ev.getCapaciteMax()).append(" places\\n");
        if (ev.getPrix() > 0) {
            desc.append("Prix: ").append(ev.getPrix()).append(" TND\\n");
        }
        desc.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━\\n");
        desc.append("Exporté depuis FinTokhrej (ID: ").append(ev.getId()).append(")");
        sb.append("DESCRIPTION:").append(escapeIcs(desc.toString())).append("\r\n");

        // Lieu
        if (lieuName != null && !lieuName.isBlank() && !lieuName.equals("Sans lieu")) {
            sb.append("LOCATION:").append(escapeIcs(lieuName)).append("\r\n");
        }

        // Catégorie = statut
        sb.append("CATEGORIES:").append(safeStr(ev.getStatut())).append(",")
                .append(safeStr(ev.getType())).append("\r\n");

        // Statut ICS
        String status = safeStr(ev.getStatut()).toUpperCase();
        switch (status) {
            case "OUVERT" -> sb.append("STATUS:CONFIRMED\r\n");
            case "FERME" -> sb.append("STATUS:CONFIRMED\r\n");
            case "ANNULE" -> sb.append("STATUS:CANCELLED\r\n");
            default -> sb.append("STATUS:TENTATIVE\r\n");
        }

        // Priorité
        sb.append("PRIORITY:5\r\n");

        // Transparence
        sb.append("TRANSP:OPAQUE\r\n");

        // Alarme : rappel 1h avant
        sb.append("BEGIN:VALARM\r\n");
        sb.append("TRIGGER:-PT1H\r\n");
        sb.append("ACTION:DISPLAY\r\n");
        sb.append("DESCRIPTION:Rappel: ").append(escapeIcs(safeStr(ev.getTitre()))).append("\r\n");
        sb.append("END:VALARM\r\n");

        // Alarme : rappel 15 min avant
        sb.append("BEGIN:VALARM\r\n");
        sb.append("TRIGGER:-PT15M\r\n");
        sb.append("ACTION:DISPLAY\r\n");
        sb.append("DESCRIPTION:Bientôt: ").append(escapeIcs(safeStr(ev.getTitre()))).append("\r\n");
        sb.append("END:VALARM\r\n");

        sb.append("END:VEVENT\r\n");
        return sb.toString();
    }

    // ─────────────────────────────────────────────────────────────
    //  OUVRIR LE FICHIER ICS AVEC L'APP PAR DÉFAUT
    // ─────────────────────────────────────────────────────────────

    /**
     * Ouvre un fichier .ics avec l'application de calendrier par défaut du système.
     * (Google Calendar, Outlook, Apple Calendar, etc.)
     */
    public boolean openWithDefaultApp(File file) {
        try {
            if (!file.exists()) {
                System.err.println("[ICalendar] Fichier introuvable: " + file.getAbsolutePath());
                return false;
            }

            // java.awt.Desktop pour ouvrir avec l'app par défaut
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().open(file);
                return true;
            } else {
                // Fallback Windows
                String os = System.getProperty("os.name").toLowerCase();
                if (os.contains("win")) {
                    new ProcessBuilder("cmd", "/c", "start", "", file.getAbsolutePath()).start();
                    return true;
                } else if (os.contains("mac")) {
                    new ProcessBuilder("open", file.getAbsolutePath()).start();
                    return true;
                } else {
                    new ProcessBuilder("xdg-open", file.getAbsolutePath()).start();
                    return true;
                }
            }
        } catch (Exception e) {
            System.err.println("[ICalendar] Erreur ouverture: " + e.getMessage());
            return false;
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  RÉSULTAT D'EXPORT
    // ─────────────────────────────────────────────────────────────

    public static class ExportResult {
        public int exported;
        public int failed;
        public String filePath;
        public String error;
        public boolean success;

        @Override
        public String toString() {
            if (success) {
                return exported + " événement(s) exporté(s) avec succès";
            }
            return "Échec: " + error;
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  HELPERS
    // ─────────────────────────────────────────────────────────────

    private String formatIcsDate(LocalDateTime ldt) {
        return ldt.format(ICS_DATE_FMT);
    }

    /**
     * Échappe les caractères spéciaux pour le format ICS.
     * RFC 5545 : les virgules, points-virgules et antislashs doivent être échappés.
     */
    private String escapeIcs(String text) {
        if (text == null) return "";
        return text
                .replace("\\", "\\\\")
                .replace(",", "\\,")
                .replace(";", "\\;")
                .replace("\n", "\\n")
                .replace("\r", "");
    }

    private void writeToFile(String content, File file) throws IOException {
        // Créer le répertoire parent si nécessaire
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            writer.write(content);
        }
    }

    private static String safeStr(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}
