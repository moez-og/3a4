package controllers.front.lieux;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import models.lieux.Lieu;
import models.lieux.LieuHoraire;
import services.lieux.LieuService;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class ChatbotController {

    @FXML private VBox       messagesBox;
    @FXML private ScrollPane messagesScroll;
    @FXML private TextField  inputField;
    @FXML private Button     sendBtn;
    @FXML private Label      statusLabel;

    private final LieuService    lieuService = new LieuService();
    private final ExecutorService executor    = Executors.newSingleThreadExecutor();
    private List<Lieu> allLieux = new ArrayList<>();

    @FXML
    public void initialize() {
        inputField.setOnAction(e -> sendMessage());
        executor.submit(() -> {
            try { allLieux = lieuService.getAll(); }
            catch (Exception ignored) {}
        });
        addBotMessage("👋 Bonjour ! Je suis votre assistant Travel Guide.\n\n"
            + "Je connais tous les lieux de la base de données. Voici ce que vous pouvez me demander :\n\n"
            + "🔍 Recherche   — « lieux à Tunis », « cafés à La Marsa »\n"
            + "🏷 Catégorie  — « restaurants », « musées », « plages »\n"
            + "🕐 Horaires   — « horaires de [nom du lieu] »\n"
            + "📞 Contact    — « téléphone de [nom du lieu] »\n"
            + "💰 Budget     — « lieux moins de 30 TND », « lieux gratuits »\n"
            + "📋 Détails    — « infos sur [nom du lieu] »\n"
            + "📊 Stats      — « combien de lieux », « liste des villes »\n"
            + "✅ Ouvert     — « lieux ouverts maintenant »\n\n"
            + "Que puis-je faire pour vous ? 🗺️");
    }

    @FXML
    public void sendMessage() {
        String text = inputField.getText().trim();
        if (text.isEmpty()) return;
        inputField.clear();
        inputField.setDisable(true);
        sendBtn.setDisable(true);
        setStatus("Recherche en cours...", true);
        addUserMessage(text);
        executor.submit(() -> {
            String response = processQuery(text);
            Platform.runLater(() -> {
                addBotMessage(response);
                inputField.setDisable(false);
                sendBtn.setDisable(false);
                inputField.requestFocus();
                setStatus("", false);
            });
        });
    }

    private String processQuery(String input) {
        if (allLieux.isEmpty()) {
            try { allLieux = lieuService.getAll(); }
            catch (Exception e) { return "Impossible de charger les lieux : " + e.getMessage(); }
        }
        String q = input.toLowerCase(Locale.ROOT).trim();

        if (matches(q, "bonjour","salut","bonsoir","hello","coucou","salam"))
            return "Bonjour ! Comment puis-je vous aider à découvrir des lieux ?";
        if (matches(q, "merci","thank","parfait","super","excellent","bravo"))
            return "Avec plaisir ! N'hésitez pas si vous avez d'autres questions.";
        if (matches(q, "aide","help","que peux","quoi demander"))
            return getHelpMessage();
        if (matches(q, "combien","nombre","total","stats","statistique"))
            return getStats();
        if (matches(q, "villes","liste ville","quelles villes","destinations"))
            return getVilles();
        if (matches(q, "categorie","catégorie","types de lieu"))
            return getCategories();
        if (matches(q, "ouvert maintenant","ouvert aujourd","encore ouvert","ouverts maintenant"))
            return getLieuxOuvertsMaintenant();
        if (matches(q, "horaire","heure","ouvre","ferme","ouverture","fermeture"))
            return getHoraires(q);
        if (matches(q, "telephone","téléphone","contact","appeler","numéro","numero"))
            return getContact(q);
        if (matches(q, "site web","website","instagram","réseaux","reseaux"))
            return getSiteWeb(q);
        if (matches(q, "budget","prix","coût","cout","tarif","moins de","gratuit"))
            return getBudget(q);

        String ville = detecterVille(q);
        if (ville != null) {
            String cat = detecterCategorie(q);
            return getLieuxParVilleEtCat(ville, cat);
        }
        String cat = detecterCategorie(q);
        if (cat != null) return getLieuxParCategorie(cat);

        Lieu lieu = chercherLieu(q);
        if (lieu != null) return getDetailLieu(lieu);

        List<Lieu> resultats = rechercheGenerale(q);
        if (!resultats.isEmpty())
            return formaterListeLieux("Voici ce que j'ai trouvé pour \"" + input + "\" :", resultats, 8);

        return "Je n'ai pas trouvé de résultat pour \"" + input + "\".\n\n"
            + "Essayez :\n"
            + "- Une ville : « lieux à Tunis »\n"
            + "- Une catégorie : « restaurants »\n"
            + "- Un nom de lieu : « infos sur [nom] »\n"
            + "- Tapez « aide » pour toutes les commandes.";
    }

    private String getStats() {
        long pub  = allLieux.stream().filter(l -> "PUBLIC".equalsIgnoreCase(safe(l.getType()))).count();
        Map<String,Long> parVille = allLieux.stream()
            .filter(l -> !safe(l.getVille()).isBlank())
            .collect(Collectors.groupingBy(Lieu::getVille, Collectors.counting()));
        Map<String,Long> parCat = allLieux.stream()
            .filter(l -> !safe(l.getCategorie()).isBlank())
            .collect(Collectors.groupingBy(Lieu::getCategorie, Collectors.counting()));

        StringBuilder sb = new StringBuilder("Statistiques des lieux :\n\n");
        sb.append("Total : ").append(allLieux.size()).append(" lieux\n");
        sb.append("Publics : ").append(pub).append(" | Privés : ").append(allLieux.size()-pub).append("\n\n");
        sb.append("Par ville :\n");
        parVille.entrySet().stream().sorted(Map.Entry.<String,Long>comparingByValue().reversed()).limit(6)
            .forEach(e -> sb.append("  - ").append(e.getKey()).append(" : ").append(e.getValue()).append("\n"));
        sb.append("\nPar catégorie :\n");
        parCat.entrySet().stream().sorted(Map.Entry.<String,Long>comparingByValue().reversed()).limit(6)
            .forEach(e -> sb.append("  - ").append(e.getKey()).append(" : ").append(e.getValue()).append("\n"));
        return sb.toString();
    }

    private String getVilles() {
        List<String> villes = allLieux.stream().map(Lieu::getVille)
            .filter(v -> v != null && !v.isBlank()).distinct().sorted().collect(Collectors.toList());
        if (villes.isEmpty()) return "Aucune ville disponible.";
        return "Villes disponibles (" + villes.size() + ") :\n\n"
            + villes.stream().map(v -> "  - " + v).collect(Collectors.joining("\n"))
            + "\n\nTapez « lieux à [ville] » pour explorer.";
    }

    private String getCategories() {
        Map<String,Long> cats = allLieux.stream()
            .filter(l -> !safe(l.getCategorie()).isBlank())
            .collect(Collectors.groupingBy(Lieu::getCategorie, Collectors.counting()));
        StringBuilder sb = new StringBuilder("Catégories disponibles :\n\n");
        cats.entrySet().stream().sorted(Map.Entry.<String,Long>comparingByValue().reversed())
            .forEach(e -> sb.append("  - ").append(e.getKey()).append(" (").append(e.getValue()).append(")\n"));
        return sb.toString();
    }

    private String getLieuxOuvertsMaintenant() {
        String auj = jourActuel();
        LocalTime now = LocalTime.now();
        List<Lieu> ouverts = allLieux.stream().filter(l -> {
            if (l.getHoraires() == null) return false;
            return l.getHoraires().stream().anyMatch(h -> auj.equalsIgnoreCase(h.getJour()) && h.isOuvert()
                && (dansPlage(now, h.getHeureOuverture1(), h.getHeureFermeture1())
                 || dansPlage(now, h.getHeureOuverture2(), h.getHeureFermeture2())));
        }).collect(Collectors.toList());
        if (ouverts.isEmpty()) return "Aucun lieu actuellement ouvert selon les horaires enregistrés.";
        return formaterListeLieux("Lieux ouverts maintenant (" + ouverts.size() + ") :", ouverts, 10);
    }

    private String getHoraires(String q) {
        Lieu lieu = chercherLieu(q);
        if (lieu == null) return "Précisez le nom du lieu.\nEx : « horaires de Le Baroque »";
        return formaterHoraires(lieu);
    }

    private String getContact(String q) {
        Lieu lieu = chercherLieu(q);
        if (lieu == null) return "Précisez le nom du lieu.\nEx : « téléphone de Le Baroque »";
        StringBuilder sb = new StringBuilder("Contact — " + lieu.getNom() + " :\n\n");
        if (!safe(lieu.getTelephone()).isBlank())  sb.append("  Téléphone : ").append(lieu.getTelephone()).append("\n");
        if (!safe(lieu.getSiteWeb()).isBlank())    sb.append("  Site web  : ").append(lieu.getSiteWeb()).append("\n");
        if (!safe(lieu.getInstagram()).isBlank())  sb.append("  Instagram : ").append(lieu.getInstagram()).append("\n");
        if (!safe(lieu.getAdresse()).isBlank())    sb.append("  Adresse   : ").append(lieu.getAdresse()).append("\n");
        return sb.toString();
    }

    private String getSiteWeb(String q) {
        Lieu lieu = chercherLieu(q);
        if (lieu == null) return "Précisez le nom du lieu.";
        StringBuilder sb = new StringBuilder("Liens — " + lieu.getNom() + " :\n\n");
        if (!safe(lieu.getSiteWeb()).isBlank())   sb.append("  Web : ").append(lieu.getSiteWeb()).append("\n");
        if (!safe(lieu.getInstagram()).isBlank()) sb.append("  Instagram : ").append(lieu.getInstagram()).append("\n");
        if (sb.toString().equals("Liens — " + lieu.getNom() + " :\n\n")) sb.append("  Aucun lien disponible.");
        return sb.toString();
    }

    private String getBudget(String q) {
        if (matches(q, "gratuit","free","pas cher","sans frais")) {
            List<Lieu> gr = allLieux.stream()
                .filter(l -> l.getBudgetMin() == null || l.getBudgetMin() == 0)
                .filter(l -> "PUBLIC".equalsIgnoreCase(safe(l.getType())))
                .collect(Collectors.toList());
            return gr.isEmpty() ? "Aucun lieu gratuit trouvé." : formaterListeLieux("Lieux gratuits / publics :", gr, 8);
        }
        int max = extraireNombre(q);
        if (max > 0) {
            List<Lieu> f = allLieux.stream()
                .filter(l -> l.getBudgetMax() == null || l.getBudgetMax() <= max
                    || (l.getBudgetMin() != null && l.getBudgetMin() <= max))
                .collect(Collectors.toList());
            return f.isEmpty() ? "Aucun lieu avec budget <= " + max + " TND."
                : formaterListeLieux("Lieux (budget <= " + max + " TND) :", f, 8);
        }
        List<Lieu> avecB = allLieux.stream()
            .filter(l -> l.getBudgetMin() != null || l.getBudgetMax() != null)
            .collect(Collectors.toList());
        if (avecB.isEmpty()) return "Aucune information de budget disponible.";
        StringBuilder sb = new StringBuilder("Lieux avec budget :\n\n");
        avecB.stream().limit(10).forEach(l -> {
            sb.append("  - ").append(l.getNom()).append(" : ");
            if (l.getBudgetMin() != null && l.getBudgetMax() != null)
                sb.append(l.getBudgetMin()).append(" – ").append(l.getBudgetMax()).append(" TND");
            else if (l.getBudgetMin() != null) sb.append("dès ").append(l.getBudgetMin()).append(" TND");
            else sb.append("max ").append(l.getBudgetMax()).append(" TND");
            sb.append("\n");
        });
        return sb.toString();
    }

    private String getLieuxParVilleEtCat(String ville, String cat) {
        List<Lieu> lieux = allLieux.stream()
            .filter(l -> safe(l.getVille()).toLowerCase().contains(ville.toLowerCase()))
            .filter(l -> cat == null || safe(l.getCategorie()).toLowerCase().contains(cat.toLowerCase()))
            .collect(Collectors.toList());
        if (lieux.isEmpty())
            return "Aucun lieu trouvé" + (cat != null ? " de type " + cat : "") + " à " + ville + ".";
        String titre = (cat != null ? cap(cat) + " à " : "Lieux à ") + cap(ville) + " (" + lieux.size() + ") :";
        return formaterListeLieux(titre, lieux, 10);
    }

    private String getLieuxParCategorie(String cat) {
        List<Lieu> lieux = allLieux.stream()
            .filter(l -> safe(l.getCategorie()).toLowerCase().contains(cat.toLowerCase()))
            .collect(Collectors.toList());
        if (lieux.isEmpty()) return "Aucun lieu de catégorie \"" + cat + "\" trouvé.";
        return formaterListeLieux(cap(cat) + " (" + lieux.size() + ") :", lieux, 10);
    }

    private String getDetailLieu(Lieu l) {
        StringBuilder sb = new StringBuilder(l.getNom() + "\n\n");
        sb.append("  Ville     : ").append(safe(l.getVille())).append("\n");
        sb.append("  Adresse   : ").append(safe(l.getAdresse())).append("\n");
        sb.append("  Catégorie : ").append(safe(l.getCategorie())).append("\n");
        sb.append("  Type      : ").append(safe(l.getType())).append("\n");
        if (!safe(l.getDescription()).isBlank())
            sb.append("  Desc.     : ").append(l.getDescription()).append("\n");
        if (!safe(l.getTelephone()).isBlank())
            sb.append("  Tél.      : ").append(l.getTelephone()).append("\n");
        if (!safe(l.getSiteWeb()).isBlank())
            sb.append("  Web       : ").append(l.getSiteWeb()).append("\n");
        if (!safe(l.getInstagram()).isBlank())
            sb.append("  Instagram : ").append(l.getInstagram()).append("\n");
        if (l.getBudgetMin() != null || l.getBudgetMax() != null) {
            sb.append("  Budget    : ");
            if (l.getBudgetMin() != null && l.getBudgetMax() != null)
                sb.append(l.getBudgetMin()).append(" – ").append(l.getBudgetMax()).append(" TND");
            else if (l.getBudgetMin() != null) sb.append("dès ").append(l.getBudgetMin()).append(" TND");
            else sb.append("max ").append(l.getBudgetMax()).append(" TND");
            sb.append("\n");
        }
        if (l.getHoraires() != null && !l.getHoraires().isEmpty()) {
            sb.append("\n  Horaires :\n");
            l.getHoraires().stream().filter(LieuHoraire::isOuvert).forEach(h -> {
                sb.append("    ").append(jourFr(h.getJour())).append(" : ")
                  .append(safe(h.getHeureOuverture1())).append(" – ").append(safe(h.getHeureFermeture1()));
                if (!safe(h.getHeureOuverture2()).isBlank())
                    sb.append(" / ").append(h.getHeureOuverture2()).append(" – ").append(safe(h.getHeureFermeture2()));
                sb.append("\n");
            });
        }
        return sb.toString();
    }

    private String formaterHoraires(Lieu l) {
        if (l.getHoraires() == null || l.getHoraires().isEmpty())
            return "Aucun horaire enregistré pour " + l.getNom() + ".";
        String auj = jourActuel();
        LocalTime now = LocalTime.now();
        StringBuilder sb = new StringBuilder("Horaires — " + l.getNom() + " (" + safe(l.getVille()) + ") :\n\n");
        String[] ORDRE = {"LUNDI","MARDI","MERCREDI","JEUDI","VENDREDI","SAMEDI","DIMANCHE"};
        for (String j : ORDRE) {
            boolean today = j.equalsIgnoreCase(auj);
            Optional<LieuHoraire> opt = l.getHoraires().stream()
                .filter(h -> j.equalsIgnoreCase(h.getJour())).findFirst();
            sb.append(today ? "  >> " : "     ").append(jourFr(j)).append(" : ");
            if (opt.isEmpty() || !opt.get().isOuvert()) {
                sb.append("Fermé");
            } else {
                LieuHoraire h = opt.get();
                sb.append(safe(h.getHeureOuverture1())).append(" – ").append(safe(h.getHeureFermeture1()));
                if (!safe(h.getHeureOuverture2()).isBlank())
                    sb.append(" / ").append(h.getHeureOuverture2()).append(" – ").append(safe(h.getHeureFermeture2()));
                if (today) {
                    boolean ouvert = dansPlage(now, h.getHeureOuverture1(), h.getHeureFermeture1())
                                  || dansPlage(now, h.getHeureOuverture2(), h.getHeureFermeture2());
                    sb.append(ouvert ? "  [OUVERT]" : "  [FERMÉ]");
                }
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private String formaterListeLieux(String titre, List<Lieu> lieux, int max) {
        StringBuilder sb = new StringBuilder(titre).append("\n\n");
        lieux.stream().limit(max).forEach(l -> {
            sb.append("  - ").append(l.getNom());
            if (!safe(l.getVille()).isBlank())     sb.append(" (").append(l.getVille()).append(")");
            if (!safe(l.getCategorie()).isBlank()) sb.append(" [").append(l.getCategorie()).append("]");
            if (!safe(l.getAdresse()).isBlank())   sb.append("\n    Adresse : ").append(l.getAdresse());
            if (!safe(l.getTelephone()).isBlank()) sb.append(" | Tel : ").append(l.getTelephone());
            sb.append("\n");
        });
        if (lieux.size() > max)
            sb.append("\n  ... et ").append(lieux.size()-max).append(" autre(s). Affinez votre recherche.");
        sb.append("\nTapez le nom d'un lieu pour plus de détails.");
        return sb.toString();
    }

    private Lieu chercherLieu(String q) {
        Optional<Lieu> exact = allLieux.stream()
            .filter(l -> safe(l.getNom()).equalsIgnoreCase(q)).findFirst();
        if (exact.isPresent()) return exact.get();
        Optional<Lieu> inQ = allLieux.stream()
            .filter(l -> !safe(l.getNom()).isBlank() && q.contains(safe(l.getNom()).toLowerCase()))
            .max(Comparator.comparingInt(l -> l.getNom().length()));
        if (inQ.isPresent()) return inQ.get();
        return allLieux.stream()
            .filter(l -> !safe(l.getNom()).isBlank() && safe(l.getNom()).toLowerCase().contains(q))
            .findFirst().orElse(null);
    }

    private String detecterVille(String q) {
        return allLieux.stream().map(Lieu::getVille)
            .filter(v -> v != null && !v.isBlank())
            .distinct()
            .sorted(Comparator.comparingInt(String::length).reversed())
            .filter(v -> q.contains(v.toLowerCase()))
            .findFirst().orElse(null);
    }

    private String detecterCategorie(String q) {
        Map<String,String> aliases = new LinkedHashMap<>();
        aliases.put("restaurant","RESTO"); aliases.put("resto","RESTO"); aliases.put("manger","RESTO");
        aliases.put("café","CAFE"); aliases.put("cafe","CAFE"); aliases.put("coffee","CAFE");
        aliases.put("musée","MUSEE"); aliases.put("musee","MUSEE");
        aliases.put("plage","PLAGE");
        aliases.put("parc","PARC_ATTRACTION"); aliases.put("attraction","PARC_ATTRACTION");
        aliases.put("bar","RESTO_BAR");
        aliases.put("shopping","CENTRE_COMMERCIAL"); aliases.put("mall","CENTRE_COMMERCIAL");
        aliases.put("public","LIEU_PUBLIC");

        List<String> cats = allLieux.stream().map(Lieu::getCategorie)
            .filter(c -> c != null && !c.isBlank()).distinct().collect(Collectors.toList());

        for (Map.Entry<String,String> e : aliases.entrySet())
            if (q.contains(e.getKey()) && cats.stream().anyMatch(c -> c.equalsIgnoreCase(e.getValue())))
                return e.getValue();

        return cats.stream().filter(c -> q.contains(c.toLowerCase())).findFirst().orElse(null);
    }

    private List<Lieu> rechercheGenerale(String q) {
        return allLieux.stream().filter(l ->
            safe(l.getNom()).toLowerCase().contains(q) ||
            safe(l.getVille()).toLowerCase().contains(q) ||
            safe(l.getAdresse()).toLowerCase().contains(q) ||
            safe(l.getCategorie()).toLowerCase().contains(q) ||
            safe(l.getDescription()).toLowerCase().contains(q)
        ).collect(Collectors.toList());
    }

    private boolean matches(String q, String... keys) {
        for (String k : keys) if (q.contains(k)) return true;
        return false;
    }

    private int extraireNombre(String q) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)").matcher(q);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    private boolean dansPlage(LocalTime now, String o, String f) {
        if (o == null || o.isBlank() || f == null || f.isBlank()) return false;
        try {
            return !now.isBefore(LocalTime.parse(o.length()>=5?o.substring(0,5):o))
                && now.isBefore(LocalTime.parse(f.length()>=5?f.substring(0,5):f));
        } catch (Exception e) { return false; }
    }

    private String jourActuel() {
        return switch (LocalDate.now().getDayOfWeek()) {
            case MONDAY -> "LUNDI"; case TUESDAY -> "MARDI"; case WEDNESDAY -> "MERCREDI";
            case THURSDAY -> "JEUDI"; case FRIDAY -> "VENDREDI"; case SATURDAY -> "SAMEDI";
            case SUNDAY -> "DIMANCHE";
        };
    }

    private String jourFr(String j) {
        return switch (j.toUpperCase()) {
            case "LUNDI"->"Lundi"; case "MARDI"->"Mardi"; case "MERCREDI"->"Mercredi";
            case "JEUDI"->"Jeudi"; case "VENDREDI"->"Vendredi"; case "SAMEDI"->"Samedi";
            case "DIMANCHE"->"Dimanche"; default->j;
        };
    }

    private String cap(String s) {
        if (s == null || s.isBlank()) return s;
        return s.substring(0,1).toUpperCase() + s.substring(1).toLowerCase();
    }

    private String safe(String s) { return s == null ? "" : s; }

    private String getHelpMessage() {
        return "Commandes disponibles :\n\n"
            + "Recherche par ville : « lieux à Tunis », « cafés à La Marsa »\n"
            + "Catégorie          : « restaurants », « musées », « plages »\n"
            + "Détails d'un lieu  : « infos sur Le Baroque »\n"
            + "Horaires           : « horaires de [lieu] »\n"
            + "Contact            : « téléphone de [lieu] »\n"
            + "Budget             : « lieux moins de 50 TND », « lieux gratuits »\n"
            + "Ouverts maintenant : « lieux ouverts maintenant »\n"
            + "Statistiques       : « combien de lieux », « liste des villes »";
    }

    private void addUserMessage(String text) {
        HBox row = new HBox();
        row.setAlignment(Pos.CENTER_RIGHT);
        row.setPadding(new Insets(4, 8, 4, 60));
        Label bubble = new Label(text);
        bubble.setWrapText(true);
        bubble.setMaxWidth(400);
        bubble.getStyleClass().add("chatBubbleUser");
        row.getChildren().add(bubble);
        messagesBox.getChildren().add(row);
        scrollToBottom();
    }

    private void addBotMessage(String text) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.TOP_LEFT);
        row.setPadding(new Insets(4, 60, 4, 8));
        Label avatar = new Label("🤖");
        avatar.getStyleClass().add("chatAvatar");
        avatar.setMinSize(34, 34);
        avatar.setMaxSize(34, 34);
        Label bubble = new Label(text);
        bubble.setWrapText(true);
        bubble.setMaxWidth(500);
        bubble.getStyleClass().add("chatBubbleBot");
        HBox.setHgrow(bubble, Priority.SOMETIMES);
        row.getChildren().addAll(avatar, bubble);
        messagesBox.getChildren().add(row);
        scrollToBottom();
    }

    private void setStatus(String text, boolean visible) {
        if (statusLabel != null) { statusLabel.setText(text); statusLabel.setVisible(visible); statusLabel.setManaged(visible); }
    }

    private void scrollToBottom() { Platform.runLater(() -> messagesScroll.setVvalue(1.0)); }

    @FXML
    public void clearHistory() {
        messagesBox.getChildren().clear();
        addBotMessage("Conversation effacée. Comment puis-je vous aider ?");
    }
}
