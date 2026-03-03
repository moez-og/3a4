package controllers.front.offres;

import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DateCell;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import models.offres.CodePromo;
import models.offres.Offre;
import models.offres.ReservationOffre;
import models.users.User;
import services.offres.CodePromoService;
import services.offres.OffreService;
import services.offres.ReservationOffreService;
import utils.ui.FrontOfferContext;
import utils.ui.ShellNavigator;

import java.sql.Date;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class OffresController {
    @FXML private ComboBox<String> filtreLieuCombo;
    @FXML private VBox offersListBox;

    @FXML private Label detailTitre;
    @FXML private Label detailLieu;
    @FXML private Label detailType;
    @FXML private Label detailPourcentage;
    @FXML private Label detailDates;
    @FXML private Label detailStatut;
    @FXML private Label detailDescription;
    @FXML private Label urgencyBanner;
    @FXML private Label detailPourcentageNew;
    @FXML private Label promoInfo;
    @FXML private Button btnGetPromo;
    @FXML private ImageView qrImageView;
    @FXML private Button btnReserver;
    @FXML private Label lblReservInfo;

    private ShellNavigator navigator;
    private User currentUser;
    private OffreService offreService;
    private CodePromoService codePromoService;
    private ReservationOffreService reservationService;

    private final List<Offre> allOffres = new ArrayList<>();
    private Map<Integer, String> lieuxMap;
    private Offre selectedOffre;

    public void setNavigator(ShellNavigator navigator) {
        this.navigator = navigator;
    }

    public void setCurrentUser(User currentUser) {
        this.currentUser = currentUser;
    }

    @FXML
    private void initialize() {
        filtreLieuCombo.getItems().setAll("Tous les lieux");
        filtreLieuCombo.setValue("Tous les lieux");
        filtreLieuCombo.valueProperty().addListener((obs, oldV, newV) -> renderOffersList());
        loadData();
    }

    @FXML
    private void onOpenAllOffers() {
        filtreLieuCombo.setValue("Tous les lieux");
        renderOffersList();
    }

    @FXML
    private void onGetPromo() {
        if (selectedOffre == null) {
            showInfo("Offre", "Sélectionne une offre d'abord.");
            return;
        }

        if (currentUser == null || currentUser.getId() <= 0) {
            showInfo("Authentification requise", "Tu dois être connecté pour obtenir un code promo.");
            return;
        }

        String role = safe(currentUser.getRole()).trim().toLowerCase();
        if (!"abonne".equals(role)) {
            showInfo("Accès refusé", "Seul un utilisateur abonné peut obtenir un code promo.");
            return;
        }

        try {
            if (codePromoService == null) {
                codePromoService = new CodePromoService();
            }
            CodePromo promo = codePromoService.genererOuRecupererCodePromo(selectedOffre.getId(), currentUser.getId());
            promoInfo.setText("Code promo actif · Expire le " + formatDate(promo.getDate_expiration()));
            qrImageView.setImage(new Image(promo.getQr_image_url(), true));
            qrImageView.setVisible(true);
            qrImageView.setManaged(true);
        } catch (Exception e) {
            showError("Code Promo", e.getMessage());
        }
    }

    private void loadData() {
        try {
            if (offreService == null) {
                offreService = new OffreService();
            }

            if (!offreService.supportsLieuAssociation()) {
                // The DB schema doesn't support linking offers to lieux; keep screen usable.
                filtreLieuCombo.getItems().setAll("Tous les lieux");
                filtreLieuCombo.setValue("Tous les lieux");
                filtreLieuCombo.setDisable(true);
            } else {
                filtreLieuCombo.setDisable(false);
            }
            lieuxMap = offreService.obtenirMapNomsLieux();

            List<Offre> dispo = offreService.obtenirOffresDisponibles();
            allOffres.clear();
            allOffres.addAll(dispo);

            for (String lieu : lieuxMap.values()) {
                if (!filtreLieuCombo.getItems().contains(lieu)) {
                    filtreLieuCombo.getItems().add(lieu);
                }
            }

            Integer selectedLieuId = FrontOfferContext.consumeSelectedLieuId();
            if (selectedLieuId != null && lieuxMap.containsKey(selectedLieuId)) {
                filtreLieuCombo.setValue(lieuxMap.get(selectedLieuId));
            }

            renderOffersList();

            Integer selectedOffreId = FrontOfferContext.consumeSelectedOffreId();
            if (selectedOffreId != null) {
                for (Offre o : allOffres) {
                    if (o.getId() == selectedOffreId) {
                        selectOffre(o);
                        break;
                    }
                }
            }

            if (selectedOffre == null && !allOffres.isEmpty()) {
                selectOffre(allOffres.get(0));
            }
        } catch (SQLException e) {
            showError("Offres", "Impossible de charger les offres: " + e.getMessage());
        } catch (Exception e) {
            showError("Offres", e.getClass().getSimpleName() + ": " + safe(e.getMessage()));
        }
    }

    private void renderOffersList() {
        offersListBox.getChildren().clear();
        if (lieuxMap == null) {
            Label empty = new Label("Offres indisponibles (connexion BD / schéma non prêt).");
            empty.getStyleClass().add("offres-empty");
            offersListBox.getChildren().add(empty);
            return;
        }

        String filter = safe(filtreLieuCombo.getValue());

        for (Offre offre : allOffres) {
            String lieuName = resolveLieuName(offre);
            if (!"Tous les lieux".equals(filter) && !lieuName.equals(filter)) {
                continue;
            }
            offersListBox.getChildren().add(buildOffreCard(offre, lieuName));
        }

        if (offersListBox.getChildren().isEmpty()) {
            Label empty = new Label("Aucune offre disponible pour ce lieu.");
            empty.getStyleClass().add("offres-empty");
            offersListBox.getChildren().add(empty);
        }
    }

    /** True when the offer expires today or tomorrow (within 24 h). */
    private boolean isExpiringSoon(Offre offre) {
        if (offre == null || offre.getDate_fin() == null) return false;
        long daysLeft = ChronoUnit.DAYS.between(LocalDate.now(), offre.getDate_fin().toLocalDate());
        return daysLeft >= 0 && daysLeft <= 1;
    }

    private Node buildOffreCard(Offre offre, String lieuName) {
        boolean urgent = isExpiringSoon(offre);
        float pct = offre.getPourcentage();

        VBox card = new VBox(10);
        card.getStyleClass().add("offreCard");
        if (urgent) card.getStyleClass().add("offreCardUrgent");

        // ── Row 1 : urgency badge (only when urgent) ───────────
        if (urgent) {
            Label badge = new Label("🔥 À ne pas rater ! 24h et l'offre est finie !!!");
            badge.getStyleClass().add("cardUrgencyBadge");
            badge.setWrapText(true);
            card.getChildren().add(badge);
        }

        // ── Row 2 : type pill  +  spacer  +  lieu pill ─────────
        Label typeTag = new Label(safe(offre.getType()).isEmpty() ? "Offre" : safe(offre.getType()));
        typeTag.getStyleClass().add("cardTypeTag");

        Label lieuTag = new Label("📍 " + lieuName);
        lieuTag.getStyleClass().add("cardLieuTag");

        Region tagSpacer = new Region();
        HBox.setHgrow(tagSpacer, Priority.ALWAYS);
        HBox tagsRow = new HBox(8, typeTag, tagSpacer, lieuTag);
        tagsRow.setAlignment(Pos.CENTER_LEFT);

        // ── Row 3 : title ──────────────────────────────────────
        Label title = new Label(safe(offre.getTitre()));
        title.getStyleClass().add("cardTitle");
        title.setWrapText(true);

        // ── Row 4 : discount hero badge ────────────────────────
        Node discountNode;
        if (urgent) {
            // crossed-out old + new doubled on same row, styled like the hero badge
            float doubled = pct * 2;
            Label oldPct = new Label(formatPercent(pct));
            oldPct.getStyleClass().add("cardMetaOld");
            Label arrow = new Label("  →  ");
            arrow.setStyle("-fx-text-fill: white; -fx-font-weight: 900;");
            Label newPct = new Label("-" + formatPercent(doubled) + " 🔥");
            newPct.setStyle("-fx-text-fill: white; -fx-font-size: 22px; -fx-font-weight: 900;");
            HBox discountBox = new HBox(4, oldPct, arrow, newPct);
            discountBox.setAlignment(Pos.CENTER_LEFT);
            discountBox.getStyleClass().add("cardDiscountBadgeUrgent");
            discountNode = discountBox;
        } else {
            Label discountLbl = new Label("-" + formatPercent(pct));
            discountLbl.getStyleClass().add("cardDiscountBadge");
            discountNode = discountLbl;
        }

        // ── Row 5 : validity dates ─────────────────────────────
        String dateText = "📅 " + formatDate(offre.getDate_debut()) + " — " + formatDate(offre.getDate_fin());
        Label dateLine = new Label(dateText);
        dateLine.getStyleClass().add("cardDateLine");

        // ── Row 6 : action button ──────────────────────────────
        Button btn = new Button("Voir l'offre →");
        btn.getStyleClass().add("actionBtn");
        btn.setOnAction(e -> selectOffre(offre));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actions = new HBox(spacer, btn);
        actions.getStyleClass().add("cardActions");
        actions.setAlignment(Pos.CENTER_RIGHT);

        card.getChildren().addAll(tagsRow, title, discountNode, dateLine, actions);
        card.setOnMouseClicked(e -> selectOffre(offre));
        return card;
    }

    private void selectOffre(Offre offre) {
        selectedOffre = offre;
        String lieuName = resolveLieuName(offre);
        boolean urgent = isExpiringSoon(offre);

        detailTitre.setText(safe(offre.getTitre()));
        detailLieu.setText("Lieu: " + lieuName);
        detailType.setText("Type: " + safe(offre.getType()));
        detailDates.setText("Validité: " + formatDate(offre.getDate_debut()) + " → " + formatDate(offre.getDate_fin()));
        updateStatutChip(offre.getStatut());
        detailDescription.setText(safe(offre.getDescription()));

        // ── Urgency: crossed-out old % + doubled new % ─────────
        if (urgent) {
            float doubled = offre.getPourcentage() * 2;
            detailPourcentage.setText("Réduction: " + formatPercent(offre.getPourcentage()));
            detailPourcentage.getStyleClass().removeAll("cardLine", "cardLineStrike");
            detailPourcentage.getStyleClass().add("cardLineStrike");
            detailPourcentageNew.setText("→ " + formatPercent(doubled) + " 🔥");
            detailPourcentageNew.setVisible(true);
            detailPourcentageNew.setManaged(true);
            urgencyBanner.setVisible(true);
            urgencyBanner.setManaged(true);
        } else {
            detailPourcentage.setText("Réduction: " + formatPercent(offre.getPourcentage()));
            detailPourcentage.getStyleClass().removeAll("cardLineStrike");
            if (!detailPourcentage.getStyleClass().contains("cardLine"))
                detailPourcentage.getStyleClass().add("cardLine");
            detailPourcentageNew.setVisible(false);
            detailPourcentageNew.setManaged(false);
            urgencyBanner.setVisible(false);
            urgencyBanner.setManaged(false);
        }

        promoInfo.setText("Aucun code promo généré.");
        qrImageView.setImage(null);
        qrImageView.setVisible(false);
        qrImageView.setManaged(false);
        btnGetPromo.setDisable(false);

        // ── Bouton réservation ──────────────────────────────────
        lblReservInfo.setVisible(false);
        lblReservInfo.setManaged(false);

        boolean hasLieu = offre.getLieu_id() > 0;
        btnReserver.setDisable(!hasLieu);

        if (hasLieu) {
            // Afficher la disponibilité pour aujourd'hui en aperçu
            try {
                if (reservationService == null) reservationService = new ReservationOffreService();
                int occupe = reservationService.compterPersonnes(offre.getLieu_id(), LocalDate.now());
                int restant = ReservationOffreService.MAX_PERSONNES_PAR_JOUR - occupe;
                lblReservInfo.setText("Disponibilité aujourd'hui : " + restant + " place(s) restante(s)");
                lblReservInfo.setVisible(true);
                lblReservInfo.setManaged(true);
            } catch (Exception ignored) {}
        }
    }

    // ─────────────────────────────────────────────────────────────────
    //  RÉSERVATION – ouverture du dialogue
    // ─────────────────────────────────────────────────────────────────

    @FXML
    private void onReserver() {
        if (selectedOffre == null) {
            showInfo("Réservation", "Sélectionne une offre d'abord.");
            return;
        }
        if (selectedOffre.getLieu_id() <= 0) {
            showInfo("Réservation", "Cette offre n'est associée à aucun lieu.");
            return;
        }
        if (currentUser == null || currentUser.getId() <= 0) {
            showInfo("Connexion requise", "Tu dois être connecté pour faire une réservation.");
            return;
        }
        if (reservationService == null) reservationService = new ReservationOffreService();

        openReservationDialog(selectedOffre);
    }

    private void openReservationDialog(Offre offre) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Réserver · " + safe(offre.getTitre()));

        // ── Titre ──────────────────────────────────────────────
        Label headline = new Label("Réservation");
        headline.setStyle("-fx-font-size: 16; -fx-font-weight: bold;");

        String lieuName = resolveLieuName(offre);
        Label subTitle = new Label("Lieu : " + lieuName);
        subTitle.setStyle("-fx-text-fill: #666;");

        // ── DatePicker (contrainte à la validité de l'offre) ──
        Label lblDate = new Label("Date souhaitée *");
        lblDate.setStyle("-fx-font-weight: bold;");
        DatePicker datePicker = new DatePicker(LocalDate.now());

        LocalDate minDate = offre.getDate_debut() != null
                ? offre.getDate_debut().toLocalDate() : LocalDate.now();
        LocalDate maxDate = offre.getDate_fin() != null
                ? offre.getDate_fin().toLocalDate() : LocalDate.now().plusYears(1);
        // n'autoriser que les dates dans la plage de validité et >= aujourd'hui
        LocalDate effectiveMin = minDate.isBefore(LocalDate.now()) ? LocalDate.now() : minDate;

        datePicker.setDayCellFactory(dp -> new DateCell() {
            @Override
            public void updateItem(LocalDate item, boolean empty) {
                super.updateItem(item, empty);
                setDisable(empty || item.isBefore(effectiveMin) || item.isAfter(maxDate));
            }
        });
        if (!LocalDate.now().isBefore(effectiveMin) && !LocalDate.now().isAfter(maxDate)) {
            datePicker.setValue(LocalDate.now());
        } else {
            datePicker.setValue(effectiveMin);
        }

        // ── Disponibilité dynamique ────────────────────────────
        Label lblDispo = new Label();
        lblDispo.setStyle("-fx-text-fill: #0077cc;");
        lblDispo.setWrapText(true);

        Runnable refreshDispo = () -> {
            LocalDate d = datePicker.getValue();
            if (d == null) return;
            try {
                int occupe = reservationService.compterPersonnes(offre.getLieu_id(), d);
                int restant = ReservationOffreService.MAX_PERSONNES_PAR_JOUR - occupe;
                if (restant <= 0) {
                    lblDispo.setText("⚠  Complet pour cette date.");
                    lblDispo.setStyle("-fx-text-fill: #c0392b;");
                } else {
                    lblDispo.setText("✔  " + restant + " place(s) disponible(s) sur "
                            + ReservationOffreService.MAX_PERSONNES_PAR_JOUR);
                    lblDispo.setStyle("-fx-text-fill: #27ae60;");
                }
            } catch (SQLException e) {
                lblDispo.setText("Impossible de vérifier la disponibilité.");
                lblDispo.setStyle("-fx-text-fill: #999;");
            }
        };
        datePicker.valueProperty().addListener((obs, ov, nv) -> refreshDispo.run());
        refreshDispo.run();

        // ── Nombre de personnes ────────────────────────────────
        Label lblNb = new Label("Nombre de personnes *");
        lblNb.setStyle("-fx-font-weight: bold;");
        Spinner<Integer> spinnerNb = new Spinner<>(1, 20, 1);
        spinnerNb.setEditable(true);
        spinnerNb.setMaxWidth(Double.MAX_VALUE);

        // ── Note optionnelle ───────────────────────────────────
        Label lblNote = new Label("Note (optionnelle)");
        lblNote.setStyle("-fx-font-weight: bold;");
        TextArea taNote = new TextArea();
        taNote.setPromptText("Ex: table en terrasse, allergie, occasion spéciale...");
        taNote.setPrefRowCount(3);
        taNote.setWrapText(true);

        // ── Résultat ───────────────────────────────────────────
        Label resultLabel = new Label();
        resultLabel.setWrapText(true);
        resultLabel.setVisible(false);
        resultLabel.setManaged(false);

        // ── Boutons ────────────────────────────────────────────
        Button btnAnnuler = new Button("Annuler");
        btnAnnuler.setOnAction(e -> dialog.close());

        Button btnConfirm = new Button("Confirmer la réservation");
        btnConfirm.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-weight: bold;");
        btnConfirm.setDefaultButton(true);
        btnConfirm.setOnAction(e -> {
            LocalDate date = datePicker.getValue();
            int nbPersonnes;
            try {
                nbPersonnes = spinnerNb.getValue();
            } catch (Exception ex) {
                nbPersonnes = 1;
            }
            if (date == null) {
                showResult(resultLabel, "⚠  Veuillez choisir une date.", "#c0392b");
                return;
            }
            if (date.isBefore(effectiveMin) || date.isAfter(maxDate)) {
                showResult(resultLabel,
                        "⚠  Date hors de la période de validité de l'offre ("
                        + formatDate(offre.getDate_debut()) + " → " + formatDate(offre.getDate_fin()) + ").",
                        "#c0392b");
                return;
            }
            try {
                ReservationOffre r = new ReservationOffre(
                        currentUser.getId(),
                        offre.getId(),
                        offre.getLieu_id(),
                        Date.valueOf(date),
                        nbPersonnes,
                        "EN_ATTENTE",
                        taNote.getText().trim().isEmpty() ? null : taNote.getText().trim()
                );
                reservationService.creer(r);
                showResult(resultLabel,
                        "✔  Réservation confirmée ! (#" + r.getId() + ")\n"
                        + "  Lieu : " + lieuName + "\n"
                        + "  Date : " + formatLocalDate(date) + "  ·  " + nbPersonnes + " personne(s)\n"
                        + "  Statut : EN_ATTENTE",
                        "#27ae60");
                btnConfirm.setDisable(true);
                refreshDispo.run();
                // rafraîchir l'aperçu dans le panneau principal
                try {
                    int occupe = reservationService.compterPersonnes(offre.getLieu_id(), LocalDate.now());
                    int restant = ReservationOffreService.MAX_PERSONNES_PAR_JOUR - occupe;
                    lblReservInfo.setText("Disponibilité aujourd'hui : " + restant + " place(s) restante(s)");
                    lblReservInfo.setVisible(true);
                    lblReservInfo.setManaged(true);
                } catch (Exception ignored) {}
            } catch (IllegalStateException ex) {
                showResult(resultLabel, "⚠  " + ex.getMessage(), "#c0392b");
            } catch (SQLException ex) {
                showResult(resultLabel, "✗  Erreur BD : " + ex.getMessage(), "#c0392b");
            }
        });

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        HBox footer = new HBox(10, btnAnnuler, sp, btnConfirm);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setPadding(new Insets(12, 0, 0, 0));

        VBox form = new VBox(10,
                subTitle,
                lblDate, datePicker, lblDispo,
                lblNb, spinnerNb,
                lblNote, taNote,
                resultLabel,
                footer
        );
        form.setPadding(new Insets(0, 4, 4, 4));

        VBox content = new VBox(12, headline, form);
        content.setPadding(new Insets(20));

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        Scene scene = new Scene(scroll, 460, 560);
        dialog.setScene(scene);
        dialog.setResizable(false);
        dialog.centerOnScreen();
        dialog.showAndWait();
    }

    // ─────────────────────────────────────────────────────────────────
    //  MES RÉSERVATIONS
    // ─────────────────────────────────────────────────────────────────

    @FXML
    private void onMesReservations() {
        if (currentUser == null || currentUser.getId() <= 0) {
            showInfo("Connexion requise", "Tu dois être connecté pour voir tes réservations.");
            return;
        }
        if (reservationService == null) reservationService = new ReservationOffreService();

        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Mes réservations");

        Label headline = new Label("Mes réservations");
        headline.setStyle("-fx-font-size: 16; -fx-font-weight: bold;");

        VBox listBox = new VBox(10);
        listBox.setPadding(new Insets(0, 4, 4, 4));

        Runnable loadList = () -> {
            listBox.getChildren().clear();
            try {
                List<ReservationOffre> list = reservationService.mesReservations(currentUser.getId());
                if (list.isEmpty()) {
                    Label empty = new Label("Aucune réservation trouvée.");
                    empty.setStyle("-fx-text-fill: #888;");
                    listBox.getChildren().add(empty);
                    return;
                }
                for (ReservationOffre r : list) {
                    listBox.getChildren().add(buildReservCard(r, dialog, listBox));
                }
            } catch (SQLException ex) {
                Label err = new Label("Erreur : " + ex.getMessage());
                err.setStyle("-fx-text-fill: #c0392b;");
                listBox.getChildren().add(err);
            }
        };

        loadList.run();
        // Store reference for annulation
        dialog.setUserData(loadList);

        ScrollPane scroll = new ScrollPane(listBox);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setPrefHeight(420);

        Button btnClose = new Button("Fermer");
        btnClose.setOnAction(e -> dialog.close());
        HBox footer = new HBox(btnClose);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setPadding(new Insets(10, 0, 0, 0));

        VBox content = new VBox(12, headline, scroll, footer);
        content.setPadding(new Insets(20));

        Scene scene = new Scene(content, 500, 520);
        dialog.setScene(scene);
        dialog.centerOnScreen();
        dialog.showAndWait();
    }

    private Node buildReservCard(ReservationOffre r, Stage parentDialog, VBox listBox) {
        VBox card = new VBox(6);
        card.setStyle("-fx-background-color: #f8faff; -fx-background-radius: 8; -fx-padding: 12; -fx-border-color: #dde3f0; -fx-border-radius: 8;");

        String statut = safe(r.getStatut());
        String color = switch (statut.toUpperCase()) {
            case "CONFIRMÉE" -> "#27ae60";
            case "ANNULÉE", "REFUSÉE" -> "#c0392b";
            case "EN_ATTENTE" -> "#e67e22";
            default -> "#888";
        };

        Label lblStatut = new Label("● " + statut);
        lblStatut.setStyle("-fx-text-fill: " + color + "; -fx-font-weight: bold;");

        Label lblInfo = new Label(
                "Réservation #" + r.getId() + "  ·  Offre #" + r.getOffreId()
                + (lieuxMap != null ? "  ·  " + lieuxMap.getOrDefault(r.getLieuId(), "Lieu #" + r.getLieuId()) : ""));
        lblInfo.setStyle("-fx-font-weight: bold;");

        Label lblDate = new Label("Date : " + (r.getDateReservation() != null
                ? formatLocalDate(r.getDateReservation().toLocalDate()) : "-")
                + "  ·  " + r.getNombrePersonnes() + " personne(s)");

        if (r.getNote() != null && !r.getNote().isBlank()) {
            Label lblNote = new Label("Note : " + r.getNote());
            lblNote.setStyle("-fx-text-fill: #555;");
            lblNote.setWrapText(true);
            card.getChildren().addAll(lblStatut, lblInfo, lblDate, lblNote);
        } else {
            card.getChildren().addAll(lblStatut, lblInfo, lblDate);
        }

        if (!"ANNULÉE".equalsIgnoreCase(statut) && !"REFUSÉE".equalsIgnoreCase(statut)) {
            Button btnAnnuler = new Button("Annuler cette réservation");
            btnAnnuler.setStyle("-fx-text-fill: #c0392b; -fx-border-color: #c0392b; -fx-background-color: transparent;");
            btnAnnuler.setOnAction(e -> {
                Optional<ButtonType> choice = new Alert(Alert.AlertType.CONFIRMATION,
                        "Annuler la réservation #" + r.getId() + " ?",
                        ButtonType.OK, ButtonType.CANCEL).showAndWait();
                if (choice.isPresent() && choice.get() == ButtonType.OK) {
                    try {
                        reservationService.annuler(r.getId(), currentUser.getId());
                        // Reload
                        listBox.getChildren().clear();
                        try {
                            List<ReservationOffre> list2 = reservationService.mesReservations(currentUser.getId());
                            if (list2.isEmpty()) {
                                Label empty = new Label("Aucune réservation trouvée.");
                                empty.setStyle("-fx-text-fill: #888;");
                                listBox.getChildren().add(empty);
                            } else {
                                for (ReservationOffre r2 : list2) listBox.getChildren().add(buildReservCard(r2, parentDialog, listBox));
                            }
                        } catch (SQLException ex2) {
                            Label err = new Label("Erreur : " + ex2.getMessage());
                            listBox.getChildren().add(err);
                        }
                    } catch (Exception ex) {
                        showError("Annulation", ex.getMessage());
                    }
                }
            });
            card.getChildren().add(btnAnnuler);
        }

        return card;
    }

    private void updateStatutChip(String statut) {
        String s = safe(statut).trim();
        if (s.isEmpty()) s = "-";
        detailStatut.setText(s);

        detailStatut.getStyleClass().removeAll("status-ouverte", "status-cloturee", "status-annulee");
        String low = s.toLowerCase();
        if ("active".equals(low)) {
            detailStatut.getStyleClass().add("status-ouverte");
        } else if (low.contains("expir")) {
            detailStatut.getStyleClass().add("status-annulee");
        } else {
            detailStatut.getStyleClass().add("status-cloturee");
        }
    }

    private String formatPercent(float p) {
        if (Math.floor(p) == p) return ((int) p) + "%";
        return p + "%";
    }

    private String formatDate(java.sql.Date d) {
        if (d == null) return "-";
        LocalDate l = d.toLocalDate();
        return l.getDayOfMonth() + "/" + l.getMonthValue() + "/" + l.getYear();
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private String resolveLieuName(Offre offre) {
        if (offre == null) return "Sans lieu";
        if (offre.getLieu_id() <= 0) return "Sans lieu";
        if (lieuxMap == null) return "Lieu #" + offre.getLieu_id();
        return lieuxMap.getOrDefault(offre.getLieu_id(), "Lieu #" + offre.getLieu_id());
    }

    private String formatLocalDate(LocalDate d) {
        if (d == null) return "-";
        return d.getDayOfMonth() + "/" + d.getMonthValue() + "/" + d.getYear();
    }

    private void showResult(Label lbl, String text, String hexColor) {
        lbl.setText(text);
        lbl.setStyle("-fx-text-fill: " + hexColor + ";");
        lbl.setVisible(true);
        lbl.setManaged(true);
    }

    private void showInfo(String title, String message) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(message);
        a.showAndWait();
    }

    private void showError(String title, String message) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle("Erreur");
        a.setHeaderText(title);
        a.setContentText(message);
        a.showAndWait();
    }
}
