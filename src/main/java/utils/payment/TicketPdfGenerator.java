package utils.payment;

import models.evenements.Evenement;
import models.evenements.Inscription;
import models.evenements.Paiement;
import models.users.User;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Génère un ticket/reçu PDF professionnel après paiement.
 *
 * Contenu du PDF :
 *  - En-tête avec titre de l'application
 *  - Infos événement (titre, dates, lieu)
 *  - Infos participant (nom, prénom, email)
 *  - Détails du paiement (référence, montant, méthode, date)
 *  - Code unique du ticket pour contrôle d'entrée
 *  - Pied de page
 */
public class TicketPdfGenerator {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.FRENCH);

    private static final DateTimeFormatter FMT_DATE =
            DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.FRENCH);

    // Couleurs
    private static final Color NAVY     = new Color(11, 37, 80);    // #0b2550
    private static final Color BLUE     = new Color(59, 130, 246);  // #3b82f6
    private static final Color GRAY     = new Color(100, 116, 139); // #64748b
    private static final Color LIGHT_BG = new Color(248, 250, 252); // #f8fafc
    private static final Color GREEN    = new Color(22, 163, 74);   // #16a34a
    private static final Color BORDER   = new Color(226, 232, 240); // #e2e8f0

    /**
     * Génère le PDF ticket et le sauvegarde dans le fichier spécifié.
     *
     * @param outputFile  Le fichier de sortie PDF
     * @param evenement   L'événement associé
     * @param inscription L'inscription de l'utilisateur
     * @param paiement    Le paiement effectué
     * @param user        L'utilisateur (peut être null)
     * @param ticketCode  Le code unique du ticket (ex: TIK-XXXXXXXX)
     * @param lieuName    Le nom du lieu (optionnel)
     */
    public static void generate(File outputFile, Evenement evenement, Inscription inscription,
                                Paiement paiement, User user, String ticketCode, String lieuName)
            throws IOException {

        try (PDDocument doc = new PDDocument()) {
            // Page A5 paysage (plus proche d'un ticket)
            PDPage page = new PDPage(new PDRectangle(595, 420)); // ~A5
            doc.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {

                float pageW = 595;
                float pageH = 420;
                float margin = 30;
                float y = pageH - margin;

                // ═══════════════════════════════════════════
                //  FOND HEADER
                // ═══════════════════════════════════════════
                cs.setNonStrokingColor(NAVY);
                cs.addRect(0, pageH - 75, pageW, 75);
                cs.fill();

                // ── Titre header ──
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA_BOLD, 20);
                cs.setNonStrokingColor(Color.WHITE);
                cs.newLineAtOffset(margin, pageH - 35);
                cs.showText("TICKET D'ENTREE");
                cs.endText();

                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 10);
                cs.setNonStrokingColor(new Color(255, 255, 255, 180));
                cs.newLineAtOffset(margin, pageH - 52);
                cs.showText("Votre confirmation de paiement et billet d'acces");
                cs.endText();

                // ── Code ticket en haut à droite ──
                cs.beginText();
                cs.setFont(PDType1Font.COURIER_BOLD, 14);
                cs.setNonStrokingColor(new Color(59, 130, 246));
                cs.newLineAtOffset(pageW - margin - 180, pageH - 38);
                cs.showText(ticketCode);
                cs.endText();

                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 8);
                cs.setNonStrokingColor(new Color(200, 200, 200));
                cs.newLineAtOffset(pageW - margin - 180, pageH - 52);
                cs.showText("Code de verification");
                cs.endText();

                y = pageH - 95;

                // ═══════════════════════════════════════════
                //  SECTION ÉVÉNEMENT
                // ═══════════════════════════════════════════
                y = drawSectionHeader(cs, "EVENEMENT", margin, y);

                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA_BOLD, 14);
                cs.setNonStrokingColor(NAVY);
                cs.newLineAtOffset(margin + 10, y);
                String eventTitle = sanitize(evenement.getTitre());
                if (eventTitle.length() > 50) eventTitle = eventTitle.substring(0, 47) + "...";
                cs.showText(eventTitle);
                cs.endText();
                y -= 18;

                // Dates
                String dateDebut = evenement.getDateDebut() != null ? evenement.getDateDebut().format(FMT) : "--";
                String dateFin = evenement.getDateFin() != null ? evenement.getDateFin().format(FMT) : "--";
                y = drawInfoLine(cs, "Dates", dateDebut + "  ->  " + dateFin, margin + 10, y);

                // Lieu
                y = drawInfoLine(cs, "Lieu", lieuName != null && !lieuName.isEmpty() ? sanitize(lieuName) : "Non specifie", margin + 10, y);

                // Type + Statut
                String type = evenement.getType() != null ? evenement.getType() : "--";
                String statut = evenement.getStatut() != null ? evenement.getStatut() : "--";
                y = drawInfoLine(cs, "Type", type + "  |  Statut : " + statut, margin + 10, y);

                y -= 8;

                // ═══════════════════════════════════════════
                //  SECTION PARTICIPANT
                // ═══════════════════════════════════════════
                y = drawSectionHeader(cs, "PARTICIPANT", margin, y);

                String nomComplet = "---";
                String email = "---";
                if (user != null) {
                    nomComplet = sanitize(safe(user.getNom()) + " " + safe(user.getPrenom()));
                    email = safe(user.getEmail());
                    if (email.isEmpty()) email = "---";
                }

                y = drawInfoLine(cs, "Nom", nomComplet, margin + 10, y);
                y = drawInfoLine(cs, "Email", email, margin + 10, y);
                y = drawInfoLine(cs, "Tickets", inscription.getNbTickets() + " ticket(s)", margin + 10, y);

                y -= 8;

                // ═══════════════════════════════════════════
                //  SECTION PAIEMENT
                // ═══════════════════════════════════════════
                y = drawSectionHeader(cs, "PAIEMENT", margin, y);

                y = drawInfoLine(cs, "Reference", safe(paiement.getReferenceCode()), margin + 10, y);
                y = drawInfoLine(cs, "Montant", String.format(Locale.FRENCH, "%.2f TND", paiement.getMontant()), margin + 10, y);
                y = drawInfoLine(cs, "Methode", formatMethode(paiement.getMethode()), margin + 10, y);
                String datePaie = paiement.getDatePaiement() != null ? paiement.getDatePaiement().format(FMT) : "--";
                y = drawInfoLine(cs, "Date", datePaie, margin + 10, y);

                // ── Statut PAYÉ en vert ──
                y -= 4;
                cs.setNonStrokingColor(GREEN);
                cs.addRect(margin + 10, y - 4, 90, 20);
                cs.fill();

                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA_BOLD, 10);
                cs.setNonStrokingColor(Color.WHITE);
                cs.newLineAtOffset(margin + 22, y);
                cs.showText("PAYE");
                cs.endText();

                // ═══════════════════════════════════════════
                //  SÉPARATEUR POINTILLÉ
                // ═══════════════════════════════════════════
                y -= 20;
                cs.setStrokingColor(BORDER);
                cs.setLineDashPattern(new float[]{4, 3}, 0);
                cs.moveTo(margin, y);
                cs.lineTo(pageW - margin, y);
                cs.stroke();
                cs.setLineDashPattern(new float[]{}, 0);

                // ═══════════════════════════════════════════
                //  CODE UNIQUE GRAND FORMAT
                // ═══════════════════════════════════════════
                y -= 20;
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 8);
                cs.setNonStrokingColor(GRAY);
                cs.newLineAtOffset(margin + 10, y);
                cs.showText("Presentez ce code a l'entree de l'evenement :");
                cs.endText();

                y -= 25;
                // Grand code centré
                float codeWidth = PDType1Font.COURIER_BOLD.getStringWidth(ticketCode) / 1000 * 22;
                float codeX = (pageW - codeWidth) / 2;
                cs.beginText();
                cs.setFont(PDType1Font.COURIER_BOLD, 22);
                cs.setNonStrokingColor(NAVY);
                cs.newLineAtOffset(codeX, y);
                cs.showText(ticketCode);
                cs.endText();

                // ═══════════════════════════════════════════
                //  PIED DE PAGE
                // ═══════════════════════════════════════════
                cs.setNonStrokingColor(BORDER);
                cs.addRect(0, 0, pageW, 25);
                cs.fill();

                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 7);
                cs.setNonStrokingColor(GRAY);
                cs.newLineAtOffset(margin, 9);
                cs.showText("Document genere automatiquement  |  Ce ticket est personnel et non cessible  |  Conservez-le precieusement");
                cs.endText();
            }

            doc.save(outputFile);
        }
    }

    // ── Helpers ──

    private static float drawSectionHeader(PDPageContentStream cs, String title, float x, float y)
            throws IOException {
        cs.setNonStrokingColor(LIGHT_BG);
        cs.addRect(x, y - 4, 535, 18);
        cs.fill();

        cs.setNonStrokingColor(BLUE);
        cs.addRect(x, y - 4, 3, 18);
        cs.fill();

        cs.beginText();
        cs.setFont(PDType1Font.HELVETICA_BOLD, 9);
        cs.setNonStrokingColor(NAVY);
        cs.newLineAtOffset(x + 10, y);
        cs.showText(title);
        cs.endText();

        return y - 22;
    }

    private static float drawInfoLine(PDPageContentStream cs, String label, String value,
                                      float x, float y) throws IOException {
        cs.beginText();
        cs.setFont(PDType1Font.HELVETICA, 9);
        cs.setNonStrokingColor(GRAY);
        cs.newLineAtOffset(x, y);
        cs.showText(label + " :");
        cs.endText();

        cs.beginText();
        cs.setFont(PDType1Font.HELVETICA_BOLD, 9);
        cs.setNonStrokingColor(NAVY);
        cs.newLineAtOffset(x + 80, y);
        String safeValue = sanitize(value);
        if (safeValue.length() > 65) safeValue = safeValue.substring(0, 62) + "...";
        cs.showText(safeValue);
        cs.endText();

        return y - 15;
    }

    private static String formatMethode(String m) {
        if (m == null) return "--";
        return switch (m) {
            case "CARTE_BANCAIRE" -> "Carte bancaire";
            case "ESPECES"        -> "Especes";
            case "VIREMENT"       -> "Virement bancaire";
            case "FLOUCI"         -> "Flouci";
            case "CRYPTO_BTC"     -> "Bitcoin (BTC)";
            case "CRYPTO_ETH"     -> "Ethereum (ETH)";
            default               -> m;
        };
    }

    /**
     * Sanitize string for PDFBox (remove non-WinAnsi characters)
     */
    private static String sanitize(String s) {
        if (s == null) return "";
        // Replace common accented chars and special chars
        return s.replace("é", "e").replace("è", "e").replace("ê", "e").replace("ë", "e")
                .replace("à", "a").replace("â", "a").replace("ä", "a")
                .replace("ù", "u").replace("û", "u").replace("ü", "u")
                .replace("ô", "o").replace("ö", "o")
                .replace("î", "i").replace("ï", "i")
                .replace("ç", "c")
                .replace("É", "E").replace("È", "E").replace("Ê", "E")
                .replace("À", "A").replace("Â", "A")
                .replace("Ù", "U").replace("Û", "U")
                .replace("Ô", "O").replace("Î", "I")
                .replace("Ç", "C")
                .replace("→", "->").replace("·", "-").replace("…", "...")
                .replaceAll("[^\\x20-\\x7E]", ""); // Remove anything non-printable ASCII
    }

    private static String safe(String s) { return s == null ? "" : s; }
}
