package controllers.front.sorties;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.beans.binding.Bindings;
import javafx.scene.shape.Rectangle;
import javafx.stage.Modality;
import javafx.stage.Stage;
import models.sorties.AnnonceSortie;
import models.sorties.ParticipationSortie;
import models.users.User;
import services.sorties.AnnonceSortieService;
import services.sorties.ParticipationSortieService;
import services.users.UserService;
import utils.ui.ShellNavigator;

import java.io.File;
import java.text.DecimalFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.nio.file.Path;
import java.nio.file.Paths;

public class SortieDetailsController {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DecimalFormat MONEY_FMT = new DecimalFormat("0.##");

    private static final char ZWSP = '\u200B';

    @FXML private VBox root;
    @FXML private ScrollPane detailsScroll;

    @FXML private VBox contentBox;
    @FXML private HBox mainRow;
    @FXML private VBox leftCol;
    @FXML private VBox sideCol;

    @FXML private StackPane heroBox;
    @FXML private ImageView heroImage;

    @FXML private Label title;
    @FXML private Label chipStatut;
    @FXML private Label chipActivite;
    @FXML private Label dateText;

    @FXML private Label locationLine;
    @FXML private Label meetingLine;

    @FXML private Label infoDate;
    @FXML private Label infoBudget;
    @FXML private Label infoPlaces;
    @FXML private Label infoStatut;
    @FXML private Label infoOrganisateur;

    @FXML private Label description;
    @FXML private FlowPane questionsPane;
    @FXML private Label questionsEmpty;

    @FXML private FlowPane acceptedPane;
    @FXML private Label acceptedEmpty;

    @FXML private VBox pendingSection;
    @FXML private VBox pendingList;
    @FXML private Label pendingEmpty;

    @FXML private Button btnParticiper;
    @FXML private Button btnRequests;
    @FXML private Button btnEdit;
    @FXML private Button btnDelete;
    @FXML private Button btnChat;

    private Stage primaryStage;
    private ShellNavigator navigator;
    private User currentUser;

    private int sortieId = -1;
    private AnnonceSortie current;

    private boolean pendingOpenRequests = false;

    private final AnnonceSortieService service = new AnnonceSortieService();
    private final UserService userService = new UserService();
    private final ParticipationSortieService participationService = new ParticipationSortieService();

    private final Rectangle heroClip = new Rectangle();

    public void setPrimaryStage(Stage stage) { this.primaryStage = stage; }
    public void setNavigator(ShellNavigator nav) { this.navigator = nav; }

    public void setCurrentUser(User u) {
        this.currentUser = u;
        // Re-render tout ce qui dépend de l'utilisateur connecté
        renderAcceptedParticipants();
        renderPendingRequests();
        applyControls();
    }

    public void setSortieId(int id) {
        this.sortieId = id;
        load();
    }

    /**
     * Used by the notifications center to jump to the "Demandes" section right after loading the sortie.
     * Safe to call before or after {@link #setSortieId(int)}.
     */
    public void openRequestsFromOutside() {
        pendingOpenRequests = true;
        if (current != null) {
            Platform.runLater(this::openRequests);
        }
    }

    @FXML
    private void initialize() {
        setupHeroCover();

        if (root != null) {
            root.setMinWidth(0);
            root.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        }
        if (detailsScroll != null) {
            detailsScroll.setMinWidth(0);
            detailsScroll.setFitToWidth(true);
            detailsScroll.setFitToHeight(true);
            VBox.setVgrow(detailsScroll, Priority.ALWAYS);

            if (contentBox != null) {
                contentBox.setMinWidth(0);
                contentBox.setMaxWidth(Double.MAX_VALUE);
            }
            if (mainRow != null) {
                mainRow.setMinWidth(0);
                mainRow.setMaxWidth(Double.MAX_VALUE);
            }
            if (leftCol != null) {
                leftCol.setMinWidth(0);
                leftCol.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(leftCol, Priority.ALWAYS);
            }
            if (sideCol != null) {
                sideCol.setMinWidth(0);
                double side = sideCol.getPrefWidth() > 0 ? sideCol.getPrefWidth() : 360;
                sideCol.setMaxWidth(side);
            }

            // Safe initial wrap to avoid a huge computed minWidth on first layout.
            if (questionsPane != null) {
                questionsPane.setMinWidth(0);
                questionsPane.setMaxWidth(Double.MAX_VALUE);
                if (questionsPane.getPrefWrapLength() <= 0) questionsPane.setPrefWrapLength(760);
            }
            if (acceptedPane != null) {
                acceptedPane.setMinWidth(0);
                acceptedPane.setMaxWidth(Double.MAX_VALUE);
                if (acceptedPane.getPrefWrapLength() <= 0) acceptedPane.setPrefWrapLength(760);
            }

            // Hard-stop: keep the whole content constrained to the viewport width.
            // This prevents any node reporting a growing preferred width from pushing the window wider.
            var applyViewport = Bindings.createDoubleBinding(() -> {
                Bounds b = detailsScroll.getViewportBounds();
                return (b == null) ? 0 : b.getWidth();
            }, detailsScroll.viewportBoundsProperty());

            Runnable syncToViewport = () -> {
                double w = applyViewport.get();
                if (w <= 1) return;

                // Keep only wrap-length updates. Forcing prefWidth/maxWidth here can create
                // a feedback loop with the ScrollPane skin which results in continuous relayouts.
                double side = 360;
                if (sideCol != null) {
                    side = sideCol.getPrefWidth() > 0 ? sideCol.getPrefWidth() : 360;
                    sideCol.setMaxWidth(side);
                }

                double leftAvailable = Math.max(240, w - side - 14);
                double wrap = Math.max(240, Math.min(900, leftAvailable - 40));

                if (questionsPane != null) questionsPane.setPrefWrapLength(wrap);
                if (acceptedPane != null) acceptedPane.setPrefWrapLength(wrap);
            };

            detailsScroll.viewportBoundsProperty().addListener((obs, o, b) -> syncToViewport.run());
            Platform.runLater(syncToViewport);
        }

        if (title != null) {
            title.setText("Détails");
            title.setMinWidth(0);
            title.setMaxWidth(Double.MAX_VALUE);
            title.setWrapText(true);
        }
        if (chipStatut != null) {
            chipStatut.setText("—");
            // Status chip should size to its text, not stretch.
            chipStatut.setMinWidth(Region.USE_PREF_SIZE);
            chipStatut.setMaxWidth(Region.USE_PREF_SIZE);
            chipStatut.setWrapText(false);
        }
        if (chipActivite != null) {
            chipActivite.setText("—");
            chipActivite.setMinWidth(0);
            chipActivite.setMaxWidth(Double.MAX_VALUE);
        }
        if (dateText != null) dateText.setText("—");
        if (description != null) {
            description.setText("");
            description.setMinWidth(0);
            description.setMaxWidth(Double.MAX_VALUE);
            description.setWrapText(true);
        }

        if (locationLine != null) {
            locationLine.setMinWidth(0);
            locationLine.setMaxWidth(Double.MAX_VALUE);
            locationLine.setWrapText(true);
        }
        if (meetingLine != null) {
            meetingLine.setMinWidth(0);
            meetingLine.setMaxWidth(Double.MAX_VALUE);
            meetingLine.setWrapText(true);
        }

        if (infoDate != null) { infoDate.setMinWidth(0); infoDate.setMaxWidth(Double.MAX_VALUE); infoDate.setWrapText(true); }
        if (infoBudget != null) { infoBudget.setMinWidth(0); infoBudget.setMaxWidth(Double.MAX_VALUE); infoBudget.setWrapText(true); }
        if (infoPlaces != null) { infoPlaces.setMinWidth(0); infoPlaces.setMaxWidth(Double.MAX_VALUE); infoPlaces.setWrapText(true); }
        if (infoStatut != null) {
            // Summary status should be a compact chip (width = text + padding)
            infoStatut.setMinWidth(Region.USE_PREF_SIZE);
            infoStatut.setMaxWidth(Region.USE_PREF_SIZE);
            infoStatut.setWrapText(false);
        }
        if (infoOrganisateur != null) { infoOrganisateur.setMinWidth(0); infoOrganisateur.setMaxWidth(Double.MAX_VALUE); infoOrganisateur.setWrapText(true); }

        if (questionsEmpty != null) {
            questionsEmpty.setVisible(false);
            questionsEmpty.setManaged(false);
        }

        if (acceptedEmpty != null) {
            acceptedEmpty.setVisible(false);
            acceptedEmpty.setManaged(false);
        }

        if (pendingEmpty != null) {
            pendingEmpty.setVisible(false);
            pendingEmpty.setManaged(false);
        }

        applyControls();
    }

    private void setupHeroCover() {
        if (heroImage == null) return;

        // Reliable & stable: same approach as cards list (no viewport crop loops).
        heroImage.setVisible(true);
        // Critical: unmanaged => ImageView won't contribute to StackPane prefWidth,
        // preventing layout feedback loops that look like "zoom" / window expansion.
        heroImage.setManaged(false);
        heroImage.setLayoutX(0);
        heroImage.setLayoutY(0);
        heroImage.setPreserveRatio(false);
        heroImage.setSmooth(true);
        heroImage.setCache(true);
        heroImage.setViewport(null);
        heroImage.setScaleX(1.0);
        heroImage.setScaleY(1.0);

        // Premium slight color adjust, but without resizing feedback.
        ColorAdjust ca = new ColorAdjust();
        ca.setContrast(0.06);
        ca.setSaturation(0.06);
        ca.setBrightness(-0.02);
        heroImage.setEffect(ca);

        heroClip.setArcWidth(28);
        heroClip.setArcHeight(28);
        heroImage.setClip(heroClip);

        if (heroBox != null) {
            try {
                if (heroImage.fitWidthProperty().isBound()) heroImage.fitWidthProperty().unbind();
                if (heroImage.fitHeightProperty().isBound()) heroImage.fitHeightProperty().unbind();
            } catch (Exception ignored) {}

            // Bind drawing size to the container.
            heroImage.fitWidthProperty().bind(heroBox.widthProperty());
            heroImage.fitHeightProperty().bind(heroBox.heightProperty());

            heroClip.widthProperty().bind(heroBox.widthProperty());
            heroClip.heightProperty().bind(heroBox.heightProperty());
        }
    }


    @FXML
    private void goBack() {
        if (navigator != null) {
            navigator.navigate("sorties");
            return;
        }
        if (primaryStage != null) primaryStage.close();
    }

    @FXML
    private void edit() {
        if (current == null) return;
        if (navigator != null) navigator.navigate("sortie-edit:" + current.getId());
    }

    @FXML
    private void delete() {
        if (current == null) return;

        Alert a = new Alert(Alert.AlertType.CONFIRMATION);
        a.setTitle("Suppression");
        a.setHeaderText("Supprimer cette annonce ?");
        a.setContentText("Cette action est définitive.");

        var r = a.showAndWait();
        if (r.isEmpty() || r.get() != ButtonType.OK) return;

        try {
            service.delete(current.getId());
            if (navigator != null) navigator.navigate("sorties");
        } catch (Exception ex) {
            error("Erreur", "Suppression impossible", safe(ex.getMessage()));
        }
    }

    @FXML
    private void participer() {
        if (current == null || currentUser == null) return;

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/front/sorties/ParticipationDialogView.fxml"));
            Parent root = loader.load();

            ParticipationDialogController c = loader.getController();

            Stage dialog = new Stage();
            dialog.initModality(Modality.APPLICATION_MODAL);
            if (heroImage != null && heroImage.getScene() != null) {
                dialog.initOwner(heroImage.getScene().getWindow());
            }
            dialog.setTitle("Participation");
            dialog.setScene(new Scene(root));

            c.setDialogStage(dialog);
            c.setContext(currentUser, current);
            c.setOnSuccess(this::load);

            dialog.showAndWait();

        } catch (Exception e) {
            error("Erreur", "Ouverture impossible", safe(e.getMessage()));
        }
    }

    @FXML
    private void copyInfos() {
        if (current == null) return;

        String when = (current.getDateSortie() == null) ? "—" : DT_FMT.format(current.getDateSortie());
        String budget = (current.getBudgetMax() <= 0)
                ? "Aucun budget"
                : (MONEY_FMT.format(current.getBudgetMax()) + " TND max");

        String txt = "Annonce de sortie\n" +
                "Titre: " + safe(current.getTitre()) + "\n" +
                "Ville: " + safe(current.getVille()) + "\n" +
                "Région: " + safe(current.getLieuTexte()) + "\n" +
                "Activité: " + safe(current.getTypeActivite()) + "\n" +
                "Date: " + when + "\n" +
                "Budget: " + budget + "\n" +
                "Places: " + current.getNbPlaces() + "\n" +
                "Point de rencontre: " + safe(current.getPointRencontre());

        ClipboardContent c = new ClipboardContent();
        c.putString(txt);
        Clipboard.getSystemClipboard().setContent(c);

        info("Copié", "Les infos ont été copiées.");
    }

    private void load() {
        if (sortieId <= 0) return;
        try {
            current = service.getById(sortieId);
            if (current == null) {
                error("Introuvable", "Annonce introuvable", "ID: " + sortieId);
                return;
            }
            render(current);
            renderAcceptedParticipants();
            updateEffectiveStatusByCapacity();
            renderPendingRequests();
            applyControls();

            if (pendingOpenRequests) {
                pendingOpenRequests = false;
                Platform.runLater(this::openRequests);
            }
        } catch (Exception e) {
            error("Erreur", "Chargement impossible", safe(e.getMessage()));
        }
    }

    private void updateEffectiveStatusByCapacity() {
        if (current == null) return;

        int capacity = current.getNbPlaces();
        if (capacity <= 0) {
            applyStatusUi(safe(current.getStatut()));
            return;
        }

        int acceptedPlaces = 0;
        try {
            acceptedPlaces = participationService.countAcceptedPlaces(current.getId());
        } catch (Exception ignored) {}

        String st = safe(current.getStatut()).trim().toUpperCase();
        if (st.isEmpty()) st = "OUVERTE";

        // If full and still marked open, treat it as closed in the UI.
        if (acceptedPlaces >= capacity && "OUVERTE".equalsIgnoreCase(st)) {
            st = "CLOTUREE";
            try {
                current.setStatut(st);
            } catch (Exception ignored) {}
        }

        applyStatusUi(st);
    }

    private void applyStatusUi(String statut) {
        String st = safe(statut).trim();
        if (st.isEmpty()) st = "—";

        if (chipStatut != null) {
            chipStatut.setText(st);
            chipStatut.getStyleClass().removeIf(s -> s != null && s.startsWith("status-"));
            if (!"—".equals(st)) chipStatut.getStyleClass().add("status-" + st.toLowerCase());

            chipStatut.setMinWidth(Region.USE_PREF_SIZE);
            chipStatut.setMaxWidth(Region.USE_PREF_SIZE);
            chipStatut.setWrapText(false);
        }
        if (infoStatut != null) {
            infoStatut.setText("—".equals(st) ? "—" : breakLongTokens(st));

            // Make the summary status look like a real chip (same as list view)
            infoStatut.getStyleClass().remove("infoV");
            if (!infoStatut.getStyleClass().contains("statusChip")) infoStatut.getStyleClass().add("statusChip");
            if (!infoStatut.getStyleClass().contains("detailsStatusChip")) infoStatut.getStyleClass().add("detailsStatusChip");

            infoStatut.getStyleClass().removeIf(s -> s != null && s.startsWith("status-"));
            if (!"—".equals(st)) infoStatut.getStyleClass().add("status-" + st.toLowerCase());

            infoStatut.setMinWidth(Region.USE_PREF_SIZE);
            infoStatut.setMaxWidth(Region.USE_PREF_SIZE);
            infoStatut.setWrapText(false);
        }
    }

    private void render(AnnonceSortie a) {
        if (heroImage != null) {
            Image im = loadImageOrFallback(a.getImageUrl());
            heroImage.setImage(im);
            // Ensure no leftover crop/scale from previous attempts.
            heroImage.setViewport(null);
            heroImage.setScaleX(1.0);
            heroImage.setScaleY(1.0);
        }

        if (title != null) title.setText(breakLongTokens(safe(a.getTitre())));

        String st = safe(a.getStatut()).trim();
        if (chipStatut != null) {
            chipStatut.setText(st.isEmpty() ? "—" : st);
            chipStatut.getStyleClass().removeIf(s -> s.startsWith("status-"));
            if (!st.isEmpty()) chipStatut.getStyleClass().add("status-" + st.toLowerCase());
        }

        if (chipActivite != null) chipActivite.setText(breakLongTokens(safe(a.getTypeActivite())));

        String when = (a.getDateSortie() == null) ? "—" : DT_FMT.format(a.getDateSortie());
        if (dateText != null) dateText.setText("📅 " + when);

        if (locationLine != null) {
            String loc = (safe(a.getVille()) + " • " + safe(a.getLieuTexte())).trim();
            locationLine.setText("📍 " + breakLongTokens(loc));
        }
        if (meetingLine != null) meetingLine.setText("🤝 " + breakLongTokens(safe(a.getPointRencontre())));

        if (infoDate != null) infoDate.setText(when);

        String budget = (a.getBudgetMax() <= 0)
                ? "Aucun budget"
                : (MONEY_FMT.format(a.getBudgetMax()) + " TND max");

        if (infoBudget != null) infoBudget.setText(breakLongTokens(budget));
        if (infoPlaces != null) infoPlaces.setText(a.getNbPlaces() + " place(s)");
        if (infoStatut != null) infoStatut.setText(st.isEmpty() ? "—" : breakLongTokens(st));

        String org = "Utilisateur #" + a.getUserId();
        try {
            User u = userService.trouverParId(a.getUserId());
            if (u != null) {
                String full = (safe(u.getPrenom()) + " " + safe(u.getNom())).trim();
                if (!full.isEmpty()) org = full;
            }
        } catch (Exception ignored) {}
        if (infoOrganisateur != null) infoOrganisateur.setText(breakLongTokens(org));

        String desc = safe(a.getDescription()).trim();
        if (description != null) description.setText(desc.isEmpty() ? "Aucune description." : breakLongTokens(desc));

        if (questionsPane != null) {
            questionsPane.getChildren().clear();
            List<String> qs = a.getQuestions();

            if (qs != null) {
                for (String q : qs) {
                    String s = safe(q).trim();
                    if (s.isEmpty()) continue;

                    Label chip = new Label("❓ " + breakLongTokens(s));
                    chip.setWrapText(true);
                    chip.setMinWidth(0);
                    chip.setMaxWidth(Double.MAX_VALUE);
                    chip.getStyleClass().add("qChip");
                    chip.setPadding(new Insets(8, 10, 8, 10));
                    questionsPane.getChildren().add(chip);
                }
            }

            boolean realEmpty = questionsPane.getChildren().isEmpty();
            if (questionsEmpty != null) {
                questionsEmpty.setVisible(realEmpty);
                questionsEmpty.setManaged(realEmpty);
            }
        }
    }

    private void renderAcceptedParticipants() {
        if (acceptedPane == null || current == null) return;

        acceptedPane.getChildren().clear();

        // ── Créateur affiché en premier avec badge Admin ──────────────
        String ownerName = "Organisateur #" + current.getUserId();
        try {
            User owner = userService.trouverParId(current.getUserId());
            if (owner != null) {
                String full = (safe(owner.getPrenom()) + " " + safe(owner.getNom())).trim();
                if (!full.isEmpty()) ownerName = full;
            }
        } catch (Exception ignored) {}

        Label ownerChip = new Label("👑 " + breakLongTokens(ownerName) + " (Admin)");
        ownerChip.setWrapText(true);
        ownerChip.setMinWidth(0);
        ownerChip.setMaxWidth(Double.MAX_VALUE);
        ownerChip.getStyleClass().addAll("qChip", "chipOwner");
        ownerChip.setPadding(new Insets(8, 10, 8, 10));
        ownerChip.setStyle("-fx-background-color: #fff3e0; -fx-border-color: #e67e22; -fx-border-radius: 20; -fx-background-radius: 20; -fx-text-fill: #c0392b; -fx-font-weight: 800;");
        acceptedPane.getChildren().add(ownerChip);

        // ── Participants acceptés ─────────────────────────────────────
        List<ParticipationSortie> parts;
        try {
            parts = participationService.getByAnnonce(current.getId());
        } catch (Exception e) {
            parts = List.of();
        }

        for (ParticipationSortie p : parts) {
            if (!isAccepted(p)) continue;

            String name = "Utilisateur #" + p.getUserId();
            try {
                User u = userService.trouverParId(p.getUserId());
                if (u != null) {
                    String full = (safe(u.getPrenom()) + " " + safe(u.getNom())).trim();
                    if (!full.isEmpty()) name = full;
                }
            } catch (Exception ignored) {}

            String places = p.getNbPlaces() > 1 ? (" (" + p.getNbPlaces() + " places)") : "";
            Label chip = new Label("👤 " + breakLongTokens(name + places));
            chip.setWrapText(true);
            chip.setMinWidth(0);
            chip.setMaxWidth(Double.MAX_VALUE);
            chip.getStyleClass().add("qChip");
            chip.setPadding(new Insets(8, 10, 8, 10));
            acceptedPane.getChildren().add(chip);
        }

        // Si aucun participant accepté en dehors du créateur, on affiche quand même
        // (le créateur est déjà affiché)
        if (acceptedEmpty != null) {
            acceptedEmpty.setVisible(false);
            acceptedEmpty.setManaged(false);
        }
    }
    /**
     * Prevents a single very long token (URL / long id with no spaces) from forcing huge preferred widths.
     * Inserts zero-width spaces at safe boundaries.
     */
    private static String breakLongTokens(String s) {
        if (s == null) return "";
        String t = s.trim();
        if (t.isEmpty()) return t;

        StringBuilder out = new StringBuilder(t.length() + 16);
        int run = 0;

        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            out.append(c);

            boolean boundary = Character.isWhitespace(c)
                    || c == '/' || c == '\\' || c == '-' || c == '_' || c == '.' || c == ','
                    || c == ':' || c == ';' || c == '?' || c == '&' || c == '=' || c == '#';

            if (boundary) {
                run = 0;
                // Add a wrap opportunity after common URL delimiters.
                if (!Character.isWhitespace(c)) out.append(ZWSP);
            } else {
                run++;
                // If we have a very long uninterrupted sequence, inject wrap opportunities.
                if (run >= 24) {
                    out.append(ZWSP);
                    run = 0;
                }
            }
        }

        return out.toString();
    }

    private void applyControls() {
        boolean owner = (currentUser != null && current != null && current.getUserId() == currentUser.getId());

        if (btnEdit != null) { btnEdit.setVisible(owner); btnEdit.setManaged(owner); }
        if (btnDelete != null) { btnDelete.setVisible(owner); btnDelete.setManaged(owner); }
        if (btnRequests != null) { btnRequests.setVisible(owner); btnRequests.setManaged(owner); }

        // Bouton Chat : visible si membre accepté OU créateur
        if (btnChat != null) {
            boolean showChat = currentUser != null && current != null &&
                    new services.sorties.ChatService().canAccess(current.getId(), currentUser.getId());
            btnChat.setVisible(showChat);
            btnChat.setManaged(showChat);
        }

        // Demandes intégrées
        if (pendingSection != null) {
            pendingSection.setVisible(owner);
            pendingSection.setManaged(owner);
        }

        if (btnParticiper != null) {
            boolean show = (currentUser != null && current != null && !owner);
            btnParticiper.setVisible(show);
            btnParticiper.setManaged(show);
            if (!show) return;

            ParticipationSortie ex = null;
            try {
                ex = participationService.getByAnnonceAndUser(current.getId(), currentUser.getId());
            } catch (Exception ignored) {}

            if (ex != null) {
                String s = safe(ex.getStatut()).trim().toUpperCase();
                if (s.equals("EN_ATTENTE")) {
                    btnParticiper.setText("Demande en attente");
                } else if (s.equals("CONFIRMEE") || s.equals("ACCEPTEE")) {
                    btnParticiper.setText("Participation confirmée");
                } else if (s.equals("REFUSEE") || s.equals("REFUSÉE")) {
                    btnParticiper.setText("Demande refusée");
                } else {
                    btnParticiper.setText("Gérer participation");
                }
                btnParticiper.setDisable(false);
            } else {
                boolean canCreate = "OUVERTE".equalsIgnoreCase(safe(current.getStatut()));
                btnParticiper.setText("Participer");
                btnParticiper.setDisable(!canCreate);
            }
        }
    }

    @FXML
    private void openRequests() {
        // Plus de fenêtre séparée : on scrolle vers la section "Demandes".
        scrollToPendingSection();
    }

    @FXML
    private void refreshPending() {
        renderPendingRequests();
    }

    private void renderPendingRequests() {
        if (pendingList == null || pendingEmpty == null) return;

        pendingList.getChildren().clear();

        if (current == null || currentUser == null) {
            pendingEmpty.setVisible(true);
            pendingEmpty.setManaged(true);
            return;
        }

        boolean owner = current.getUserId() == currentUser.getId();
        if (pendingSection != null) {
            pendingSection.setVisible(owner);
            pendingSection.setManaged(owner);
        }
        if (!owner) return;

        List<ParticipationSortie> parts;
        try {
            parts = participationService.getByAnnonce(current.getId());
        } catch (Exception e) {
            pendingEmpty.setVisible(true);
            pendingEmpty.setManaged(true);
            pendingEmpty.setText("Erreur chargement demandes: " + safe(e.getMessage()));
            return;
        }

        List<ParticipationSortie> pending = parts.stream().filter(this::isPending).toList();

        boolean empty = pending.isEmpty();
        pendingEmpty.setText("Aucune demande en attente.");
        pendingEmpty.setVisible(empty);
        pendingEmpty.setManaged(empty);

        if (!empty) {
            for (ParticipationSortie p : pending) {
                pendingList.getChildren().add(pendingRow(p));
            }
        }
    }

    private Node pendingRow(ParticipationSortie p) {
        VBox box = new VBox(8);
        box.getStyleClass().add("detailsItem");

        String name = "Utilisateur #" + p.getUserId();
        try {
            User u = userService.trouverParId(p.getUserId());
            if (u != null) {
                String full = (safe(u.getPrenom()) + " " + safe(u.getNom())).trim();
                if (!full.isEmpty()) name = full;
            }
        } catch (Exception ignored) {}

        Label title = new Label(breakLongTokens(name));
        title.setStyle("-fx-font-weight: 900; -fx-text-fill: rgba(15,42,68,0.95);");
        title.setMinWidth(0);
        title.setMaxWidth(Double.MAX_VALUE);
        title.setWrapText(true);

        Label meta = new Label(Math.max(1, p.getNbPlaces()) + " place(s) demandée(s)");
        meta.setStyle("-fx-text-fill: rgba(15,42,68,0.65); -fx-font-weight: 850;");
        meta.setMinWidth(0);
        meta.setMaxWidth(Double.MAX_VALUE);
        meta.setWrapText(true);

        String comment = safe(p.getCommentaire()).trim();
        Label sub = new Label(breakLongTokens(comment));
        sub.setWrapText(true);
        sub.setMinWidth(0);
        sub.setMaxWidth(Double.MAX_VALUE);
        sub.setStyle("-fx-text-fill: rgba(15,42,68,0.78); -fx-font-weight: 800;");
        sub.setVisible(!comment.isEmpty());
        sub.setManaged(!comment.isEmpty());

        Button accept = new Button("Accepter");
        accept.getStyleClass().add("primaryBtn");
        Button refuse = new Button("Refuser");
        refuse.getStyleClass().add("dangerBtn");

        accept.setOnAction(e -> {
            try {
                if (current == null) return;
                int accepted = participationService.countAcceptedPlaces(current.getId());
                int remaining = Math.max(0, current.getNbPlaces() - accepted);
                if (p.getNbPlaces() > remaining) {
                    error("Places insuffisantes", "Impossible d'accepter",
                            "Places restantes: " + remaining + ". Demandées: " + p.getNbPlaces());
                    return;
                }

                participationService.updateStatus(p.getId(), "CONFIRMEE");
                renderAcceptedParticipants();
                updateEffectiveStatusByCapacity();
                renderPendingRequests();
                applyControls();
                info("Acceptée", "La demande a été acceptée.");

            } catch (Exception ex) {
                error("Erreur", "Action impossible", safe(ex.getMessage()));
            }
        });

        refuse.setOnAction(e -> {
            try {
                participationService.updateStatus(p.getId(), "REFUSEE");
                renderPendingRequests();
                info("Refusée", "La demande a été refusée.");

            } catch (Exception ex) {
                error("Erreur", "Action impossible", safe(ex.getMessage()));
            }
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox actions = new HBox(10, spacer, accept, refuse);
        actions.setAlignment(Pos.CENTER_LEFT);

        box.getChildren().addAll(title, meta, sub, actions);
        return box;
    }

    private void scrollToPendingSection() {
        if (detailsScroll == null || pendingSection == null) return;
        if (!pendingSection.isManaged() || !pendingSection.isVisible()) return;
        if (detailsScroll.getContent() == null) return;

        Platform.runLater(() -> {
            try {
                Node content = detailsScroll.getContent();

                Bounds contentScene = content.localToScene(content.getBoundsInLocal());
                Bounds nodeScene = pendingSection.localToScene(pendingSection.getBoundsInLocal());

                double deltaY = nodeScene.getMinY() - contentScene.getMinY();
                double contentHeight = content.getBoundsInLocal().getHeight();
                double viewport = detailsScroll.getViewportBounds().getHeight();

                double denom = Math.max(1, contentHeight - viewport);
                double v = Math.max(0, Math.min(1, deltaY / denom));
                detailsScroll.setVvalue(v);
            } catch (Exception ignored) {
            }
        });
    }

    private boolean isAccepted(ParticipationSortie p) {
        if (p == null) return false;
        String s = safe(p.getStatut()).trim().toUpperCase();
        return s.equals("ACCEPTEE") || s.equals("CONFIRMEE");
    }

    private boolean isPending(ParticipationSortie p) {
        if (p == null) return false;
        return "EN_ATTENTE".equalsIgnoreCase(safe(p.getStatut()).trim());
    }

    private void info(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }

    private void error(String title, String header, String content) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle(title);
        a.setHeaderText(header);
        a.setContentText(content);
        a.showAndWait();
    }

    private String safe(String s) { return s == null ? "" : s; }

    private Image loadImageOrFallback(String pathOrUrl) {
        Image im = loadImageOrNull(pathOrUrl);
        if (im != null) return im;
        return new Image(getClass().getResource("/images/demo/hero/hero.jpg").toExternalForm(), false);
    }

    private Image loadImageOrNull(String pathOrUrl) {
        try {
            if (pathOrUrl == null || pathOrUrl.trim().isEmpty()) return null;
            String p = pathOrUrl.trim();

            // Strip quotes if persisted with them.
            if ((p.startsWith("\"") && p.endsWith("\"")) || (p.startsWith("'") && p.endsWith("'"))) {
                p = p.substring(1, p.length() - 1).trim();
            }

            // Normalize common DB escaping (e.g., C:\\Users\\...).
            while (p.contains("\\\\\\\\")) {
                p = p.replace("\\\\\\\\", "\\\\");
            }

            // file: URI already.
            if (p.startsWith("file:")) return new Image(p, false);

            // Windows absolute path / UNC path -> convert to URI safely.
            // Examples: C:\Users\... or C:/Users/... or \\SERVER\share\file.jpg
            boolean looksWindowsAbs = p.matches("^[A-Za-z]:[\\\\/].*") || p.startsWith("\\\\");
            if (looksWindowsAbs) {
                Path pp = Paths.get(p).toAbsolutePath().normalize();
                File f = pp.toFile();
                if (f.exists()) return new Image(f.toURI().toString(), false);
            }

            File f = new File(p);
            if (f.exists()) return new Image(f.toURI().toString(), false);
            if (p.startsWith("http://") || p.startsWith("https://")) return new Image(p, true);
            if (p.startsWith("/")) {
                var u = getClass().getResource(p);
                if (u != null) return new Image(u.toExternalForm(), false);
            }
        } catch (Exception ignored) {}
        return null;
    }
    @FXML
    private void openChat() {
        if (current == null || currentUser == null) return;
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/front/sorties/GroupeChatView.fxml"));
            javafx.scene.Parent chatRoot = loader.load();

            GroupeChatController ctrl = loader.getController();
            ctrl.setContext(currentUser, current);

            Stage chatStage = new Stage();
            chatStage.initModality(Modality.NONE); // non-bloquant pour permettre d'utiliser l'app en même temps
            chatStage.setTitle("Chat — " + safe(current.getTitre()));
            chatStage.setResizable(true);

            Scene scene = new Scene(chatRoot, 680, 600);
            try {
                var url = getClass().getResource("/styles/sorties/chat.css");
                if (url != null) scene.getStylesheets().add(url.toExternalForm());
            } catch (Exception ignored) {}

            chatStage.setScene(scene);
            chatStage.setOnCloseRequest(e -> ctrl.stopPolling());
            chatStage.show();
        } catch (Exception ex) {
            error("Chat", "Ouverture impossible", "Impossible d'ouvrir le chat : " + ex.getMessage());
        }
    }


}