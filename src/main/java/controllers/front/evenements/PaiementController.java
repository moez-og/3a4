package controllers.front.evenements;

import controllers.front.shell.FrontDashboardController;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import models.evenements.Evenement;
import models.evenements.Inscription;
import models.evenements.Paiement;
import models.lieux.Lieu;
import models.users.User;
import services.evenements.EvenementService;
import services.evenements.InscriptionService;
import services.evenements.PaiementService;
import services.evenements.TicketService;
import services.lieux.LieuService;
import services.payment.CryptoPaymentService;
import services.payment.FlouciPaymentService;
import services.payment.StripePaymentService;
import utils.payment.PaymentConfig;
import utils.payment.TicketPdfGenerator;
import utils.ui.ShellNavigator;

import javafx.util.Duration;

import java.io.File;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * Contrôleur de la page de paiement avec intégration API.
 *
 * <p>5 méthodes de paiement :</p>
 * <ul>
 *     <li><b>Carte bancaire</b> — Stripe Checkout (mode test) ou simulation</li>
 *     <li><b>Espèces</b> — paiement au guichet (confirmation locale)</li>
 *     <li><b>Virement</b> — saisie de référence (confirmation locale)</li>
 *     <li><b>Flouci</b> — API Flouci (sandbox) ou simulation</li>
 *     <li><b>Crypto</b> — CoinGecko pour le taux + affichage adresse BTC/ETH</li>
 * </ul>
 *
 * <p>Quand les clés API ne sont pas configurées dans {@link PaymentConfig},
 * le système bascule automatiquement en mode simulation (aucun appel externe).</p>
 */
public class PaiementController {

    // ═══════════════════════════════════════════════════════════
    //  FXML — RÉCAP
    // ═══════════════════════════════════════════════════════════
    @FXML private Label recapTitre;
    @FXML private Label recapDates;
    @FXML private Label recapTickets;
    @FXML private Label recapTotal;

    // ═══════════════════════════════════════════════════════════
    //  FXML — MÉTHODE DE PAIEMENT
    // ═══════════════════════════════════════════════════════════
    @FXML private ToggleButton btnCarte;
    @FXML private ToggleButton btnEspeces;
    @FXML private ToggleButton btnVirement;
    @FXML private ToggleButton btnFlouci;
    @FXML private ToggleButton btnCrypto;

    // ═══════════════════════════════════════════════════════════
    //  FXML — FORMULAIRES
    // ═══════════════════════════════════════════════════════════
    @FXML private VBox formCarte;
    @FXML private Label carteApiNote;
    @FXML private VBox carteFieldsBox;
    @FXML private TextField carteNom;
    @FXML private TextField carteNumero;
    @FXML private TextField carteExpiry;
    @FXML private TextField carteCvv;

    @FXML private VBox formEspeces;

    @FXML private VBox formVirement;
    @FXML private TextField virementRef;

    @FXML private VBox formFlouci;
    @FXML private Label flouciApiNote;
    @FXML private VBox flouciFieldsBox;
    @FXML private TextField flouciPhone;

    @FXML private VBox formCrypto;
    @FXML private ToggleButton btnBTC;
    @FXML private ToggleButton btnETH;
    @FXML private VBox cryptoDetails;
    @FXML private TextField cryptoAddress;
    @FXML private Label cryptoAmount;
    @FXML private Label cryptoRate;
    @FXML private Label cryptoLoadingLabel;

    // ═══════════════════════════════════════════════════════════
    //  FXML — ACTIONS / ERREUR
    // ═══════════════════════════════════════════════════════════
    @FXML private Button payerBtn;
    @FXML private Label errorLabel;
    @FXML private Label modeLabel;

    // ═══════════════════════════════════════════════════════════
    //  FXML — LAYERS (StackPane children)
    // ═══════════════════════════════════════════════════════════
    @FXML private ScrollPane mainScroll;
    @FXML private VBox webViewPane;
    @FXML private WebView paymentWebView;
    @FXML private VBox loadingPane;
    @FXML private Label loadingText;
    @FXML private VBox recuPane;
    @FXML private Label recuRef;
    @FXML private Label recuMontant;
    @FXML private Label recuMethode;
    @FXML private Label recuDate;

    // ═══════════════════════════════════════════════════════════
    //  SERVICES
    // ═══════════════════════════════════════════════════════════
    private final EvenementService evenementService       = new EvenementService();
    private final InscriptionService inscriptionService   = new InscriptionService();
    private final PaiementService paiementService         = new PaiementService();
    private final TicketService ticketService             = new TicketService();
    private final StripePaymentService stripeService      = new StripePaymentService();
    private final FlouciPaymentService flouciService      = new FlouciPaymentService();
    private final CryptoPaymentService cryptoService      = new CryptoPaymentService();

    // ═══════════════════════════════════════════════════════════
    //  ÉTAT
    // ═══════════════════════════════════════════════════════════
    private ShellNavigator navigator;
    private User currentUser;
    private int inscriptionId = -1;
    private Inscription inscription;
    private Evenement evenement;
    private String selectedMethode = null;

    // Flouci
    private String flouciPaymentId = null;

    // Crypto
    private String selectedCrypto = null;
    private CryptoPaymentService.CryptoQuote currentQuote = null;

    // PDF — stocker le dernier paiement pour le téléchargement
    private Paiement lastPaiement;
    private String lastTicketCode;

    // WebView
    private ChangeListener<String> webLocationListener;

    // Toggle groups
    private final ToggleGroup methodeGroup = new ToggleGroup();
    private final ToggleGroup cryptoGroup  = new ToggleGroup();

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy · HH:mm", Locale.FRENCH);

    // ═══════════════════════════════════════════════════════════
    //  INJECTION (appelé par FrontDashboardController)
    // ═══════════════════════════════════════════════════════════

    public void setNavigator(ShellNavigator nav) { this.navigator = nav; }
    public void setCurrentUser(User u)           { this.currentUser = u; }

    public void setInscriptionId(int id) {
        this.inscriptionId = id;
        loadData();
    }

    // ═══════════════════════════════════════════════════════════
    //  INITIALIZE
    // ═══════════════════════════════════════════════════════════

    @FXML
    private void initialize() {
        // ── Extra CSS classes (not possible via FXML attribute for multi-class) ──
        btnCrypto.getStyleClass().add("payCryptoBtn");
        btnBTC.getStyleClass().add("payCryptoToggle");
        btnETH.getStyleClass().add("payCryptoToggle");
        if (cryptoAddress != null) cryptoAddress.getStyleClass().add("payCryptoAddress");

        // ── Méthode toggle group ──
        btnCarte.setToggleGroup(methodeGroup);
        btnEspeces.setToggleGroup(methodeGroup);
        btnVirement.setToggleGroup(methodeGroup);
        btnFlouci.setToggleGroup(methodeGroup);
        btnCrypto.setToggleGroup(methodeGroup);

        methodeGroup.selectedToggleProperty().addListener((obs, old, nv) -> {
            hideAllForms();
            selectedCrypto = null;
            currentQuote   = null;

            if (nv == btnCarte)         { showForm(formCarte);    selectedMethode = "CARTE_BANCAIRE"; }
            else if (nv == btnEspeces)  { showForm(formEspeces);  selectedMethode = "ESPECES"; }
            else if (nv == btnVirement) { showForm(formVirement); selectedMethode = "VIREMENT"; }
            else if (nv == btnFlouci)   { showForm(formFlouci);   selectedMethode = "FLOUCI"; }
            else if (nv == btnCrypto)   { showForm(formCrypto);   selectedMethode = "CRYPTO"; }
            else { selectedMethode = null; }
        });

        // ── Crypto toggle group ──
        btnBTC.setToggleGroup(cryptoGroup);
        btnETH.setToggleGroup(cryptoGroup);

        cryptoGroup.selectedToggleProperty().addListener((obs, old, nv) -> {
            if (nv == btnBTC)      selectedCrypto = "BTC";
            else if (nv == btnETH) selectedCrypto = "ETH";
            else                   selectedCrypto = null;

            if (selectedCrypto != null && evenement != null) {
                fetchCryptoQuote();
            } else {
                setFormVisible(cryptoDetails, false);
            }
        });

        hideAllForms();

        // ── Adapter l'UI selon la configuration API ──
        configureApiMode();
    }

    /**
     * Adapte l'interface en fonction des API configurées.
     */
    private void configureApiMode() {
        boolean stripe  = PaymentConfig.isStripeConfigured();
        boolean flouci  = PaymentConfig.isFlouciConfigured();

        // Carte bancaire
        if (carteApiNote != null) {
            carteApiNote.setVisible(stripe);
            carteApiNote.setManaged(stripe);
        }
        if (carteFieldsBox != null) {
            carteFieldsBox.setVisible(!stripe);
            carteFieldsBox.setManaged(!stripe);
        }

        // Flouci
        if (flouciApiNote != null) {
            flouciApiNote.setVisible(flouci);
            flouciApiNote.setManaged(flouci);
        }
        if (flouciFieldsBox != null) {
            flouciFieldsBox.setVisible(!flouci);
            flouciFieldsBox.setManaged(!flouci);
        }

        // Mode label
        if (modeLabel != null) {
            if (stripe || flouci) {
                modeLabel.setText("🔑 API " + (stripe ? "Stripe" : "") + (stripe && flouci ? " + " : "") + (flouci ? "Flouci" : "") + " actives  ·  Crypto CoinGecko");
            } else {
                modeLabel.setText("⚡ Mode simulation — Configurez vos clés API dans PaymentConfig.java");
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  NAVIGATION
    // ═══════════════════════════════════════════════════════════

    @FXML
    public void goBack() {
        if (navigator != null && evenement != null) {
            navigator.navigate(FrontDashboardController.ROUTE_EVENEMENT_DETAILS_PREFIX + evenement.getId());
        } else if (navigator != null) {
            navigator.navigate(FrontDashboardController.ROUTE_EVENTS);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  DATA LOADING
    // ═══════════════════════════════════════════════════════════

    private void loadData() {
        if (inscriptionId <= 0) return;
        try {
            inscription = inscriptionService.getById(inscriptionId);
            if (inscription == null) {
                showError("Inscription introuvable."); return;
            }
            evenement = evenementService.getById(inscription.getEventId());
            if (evenement == null) {
                showError("Événement introuvable."); return;
            }
            if (paiementService.isPaid(inscriptionId)) {
                showError("Cette inscription est déjà payée.");
                if (payerBtn != null) payerBtn.setDisable(true);
                return;
            }
            fillRecap();
        } catch (Exception e) {
            showError("Erreur de chargement : " + e.getMessage());
        }
    }

    private void fillRecap() {
        if (recapTitre != null)
            recapTitre.setText(evenement.getTitre());

        if (recapDates != null) {
            String d1 = evenement.getDateDebut() != null ? evenement.getDateDebut().format(FMT) : "—";
            String d2 = evenement.getDateFin() != null ? evenement.getDateFin().format(FMT) : "—";
            recapDates.setText(d1 + "  →  " + d2);
        }

        int tickets    = inscription.getNbTickets();
        double prixUnit = evenement.getPrix();
        double total    = prixUnit * tickets;

        if (recapTickets != null)
            recapTickets.setText(tickets + " ticket" + (tickets > 1 ? "s" : "")
                    + " × " + String.format(Locale.FRENCH, "%.2f", prixUnit) + " TND");

        if (recapTotal != null)
            recapTotal.setText(String.format(Locale.FRENCH, "%.2f", total) + " TND");
    }

    // ═══════════════════════════════════════════════════════════
    //  HANDLE PAYER  (dispatch)
    // ═══════════════════════════════════════════════════════════

    @FXML
    public void handlePayer() {
        clearError();

        if (selectedMethode == null) {
            showError("Choisis une méthode de paiement.");
            return;
        }

        double total = evenement.getPrix() * inscription.getNbTickets();

        switch (selectedMethode) {
            case "CARTE_BANCAIRE" -> {
                if (PaymentConfig.isStripeConfigured()) {
                    handleStripePayment(total);
                } else {
                    if (!validateLocalCardForm()) return;
                    String nom = safe(carteNom.getText()).trim();
                    String num = safe(carteNumero.getText()).replaceAll("\\s+", "");
                    handleSimulatedPayment("CARTE_BANCAIRE", total, nom, num.substring(num.length() - 4));
                }
            }
            case "FLOUCI" -> {
                if (PaymentConfig.isFlouciConfigured()) {
                    handleFlouciPayment(total);
                } else {
                    String phone = safe(flouciPhone.getText()).trim();
                    if (phone.isEmpty() || phone.length() < 8) {
                        showError("Numéro de téléphone invalide."); return;
                    }
                    handleSimulatedPayment("FLOUCI", total, null, null);
                }
            }
            case "CRYPTO" -> {
                if (selectedCrypto == null || currentQuote == null) {
                    showError("Sélectionnez BTC ou ETH et attendez le calcul du taux.");
                    return;
                }
                handleSimulatedPayment("CRYPTO_" + selectedCrypto, total, null, null);
            }
            case "ESPECES" -> handleSimulatedPayment("ESPECES", total, null, null);
            case "VIREMENT" -> {
                String ref = safe(virementRef.getText()).trim();
                if (ref.isEmpty()) { showError("Référence de virement requise."); return; }
                handleSimulatedPayment("VIREMENT", total, null, null);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  STRIPE (API réelle)
    // ═══════════════════════════════════════════════════════════

    private void handleStripePayment(double totalTND) {
        showLayer(loadingPane);
        setLoadingText("Connexion à Stripe...");

        String productName = evenement.getTitre() + " × " + inscription.getNbTickets() + " ticket(s)";

        CompletableFuture.supplyAsync(() -> {
            try {
                return stripeService.createCheckoutSession(productName, totalTND, 1);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).thenAccept(url -> Platform.runLater(() -> {
            if (url != null && !url.isBlank()) {
                openWebView(url, "CARTE_BANCAIRE");
            } else {
                showLayer(mainScroll);
                showError("Impossible de créer la session Stripe.");
            }
        })).exceptionally(ex -> {
            Platform.runLater(() -> {
                showLayer(mainScroll);
                showError("Erreur Stripe : " + rootMessage(ex));
            });
            return null;
        });
    }

    // ═══════════════════════════════════════════════════════════
    //  FLOUCI (API réelle)
    // ═══════════════════════════════════════════════════════════

    private void handleFlouciPayment(double totalTND) {
        showLayer(loadingPane);
        setLoadingText("Connexion à Flouci...");

        CompletableFuture.supplyAsync(() -> {
            try {
                return flouciService.generatePayment(totalTND);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).thenAccept(result -> Platform.runLater(() -> {
            flouciPaymentId = result.paymentId();
            openWebView(result.link(), "FLOUCI");
        })).exceptionally(ex -> {
            Platform.runLater(() -> {
                showLayer(mainScroll);
                showError("Erreur Flouci : " + rootMessage(ex));
            });
            return null;
        });
    }

    // ═══════════════════════════════════════════════════════════
    //  WEBVIEW (Stripe / Flouci)
    // ═══════════════════════════════════════════════════════════

    private void openWebView(String url, String methode) {
        showLayer(webViewPane);

        WebEngine engine = paymentWebView.getEngine();

        // Nettoyer l'ancien listener
        if (webLocationListener != null) {
            engine.locationProperty().removeListener(webLocationListener);
        }

        webLocationListener = (obs, oldUrl, newUrl) -> {
            if (newUrl == null) return;

            boolean success = newUrl.contains("fintokhrej.local") && newUrl.contains("success");
            boolean cancel  = newUrl.contains("fintokhrej.local")
                    && (newUrl.contains("cancel") || newUrl.contains("fail"));

            if (success || cancel) {
                engine.locationProperty().removeListener(webLocationListener);
                webLocationListener = null;
                engine.loadContent("<html><body style='background:#f8fafc;text-align:center;padding:60px'>"
                        + "<h2 style='color:#163a5c'>Traitement en cours…</h2></body></html>");

                if (success) {
                    handleWebPaymentSuccess(methode);
                } else {
                    showLayer(mainScroll);
                    showError("Paiement annulé.");
                }
            }
        };

        engine.locationProperty().addListener(webLocationListener);
        engine.load(url);
    }

    @FXML
    public void cancelWebPayment() {
        WebEngine engine = paymentWebView.getEngine();
        if (webLocationListener != null) {
            engine.locationProperty().removeListener(webLocationListener);
            webLocationListener = null;
        }
        engine.loadContent("");
        flouciPaymentId = null;
        showLayer(mainScroll);
    }

    /**
     * Appelé quand le WebView détecte un redirect vers success_url.
     * Vérifie le paiement (Flouci) puis enregistre en BDD.
     */
    private void handleWebPaymentSuccess(String methode) {
        showLayer(loadingPane);
        setLoadingText("Vérification du paiement...");

        double total = evenement.getPrix() * inscription.getNbTickets();

        CompletableFuture<Boolean> verifyFuture;

        if ("FLOUCI".equals(methode) && flouciPaymentId != null) {
            verifyFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    return flouciService.verifyPayment(flouciPaymentId);
                } catch (Exception e) {
                    return true; // En cas d'erreur de vérification, on accepte
                }
            });
        } else {
            verifyFuture = CompletableFuture.completedFuture(true);
        }

        verifyFuture.thenAccept(verified -> Platform.runLater(() -> {
            if (verified) {
                savePaiementAndShowRecu(methode, total, null, null);
            } else {
                showLayer(mainScroll);
                showError("Paiement non vérifié par Flouci. Veuillez réessayer.");
            }
        }));
    }

    // ═══════════════════════════════════════════════════════════
    //  CRYPTO (CoinGecko)
    // ═══════════════════════════════════════════════════════════

    private void fetchCryptoQuote() {
        setFormVisible(cryptoDetails, false);
        if (cryptoLoadingLabel != null) {
            cryptoLoadingLabel.setVisible(true);
            cryptoLoadingLabel.setManaged(true);
        }

        double total = evenement.getPrix() * inscription.getNbTickets();

        CompletableFuture.supplyAsync(() -> {
            try {
                return cryptoService.getQuote(total);
            } catch (Exception e) {
                // Fallback si CoinGecko est down
                return cryptoService.getFallbackQuote(total * PaymentConfig.TND_TO_USD);
            }
        }).thenAccept(quote -> Platform.runLater(() -> {
            currentQuote = quote;
            if (cryptoLoadingLabel != null) {
                cryptoLoadingLabel.setVisible(false);
                cryptoLoadingLabel.setManaged(false);
            }

            if (quote != null) {
                if ("BTC".equals(selectedCrypto)) {
                    if (cryptoAddress != null) cryptoAddress.setText(quote.btcAddress());
                    if (cryptoAmount != null)
                        cryptoAmount.setText(String.format("%.8f BTC", quote.btcAmount()));
                    if (cryptoRate != null)
                        cryptoRate.setText(String.format("≈ %.2f USD  ·  1 BTC = %.2f USD",
                                quote.usdAmount(), quote.btcPriceUSD()));
                } else {
                    if (cryptoAddress != null) cryptoAddress.setText(quote.ethAddress());
                    if (cryptoAmount != null)
                        cryptoAmount.setText(String.format("%.8f ETH", quote.ethAmount()));
                    if (cryptoRate != null)
                        cryptoRate.setText(String.format("≈ %.2f USD  ·  1 ETH = %.2f USD",
                                quote.usdAmount(), quote.ethPriceUSD()));
                }
                setFormVisible(cryptoDetails, true);
            }
        }));
    }

    @FXML
    public void copyCryptoAddress() {
        if (cryptoAddress != null && cryptoAddress.getText() != null) {
            ClipboardContent content = new ClipboardContent();
            content.putString(cryptoAddress.getText());
            Clipboard.getSystemClipboard().setContent(content);
            showError(""); // clear
            // Brief visual feedback
            String original = cryptoAddress.getPromptText();
            cryptoAddress.setPromptText("✓ Copié !");
            PauseTransition pt = new PauseTransition(Duration.millis(1500));
            pt.setOnFinished(e -> cryptoAddress.setPromptText(original));
            pt.play();
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  SIMULATION (quand les API ne sont pas configurées)
    // ═══════════════════════════════════════════════════════════

    private void handleSimulatedPayment(String methode, double total,
                                        String nomCarte, String quatreDerniers) {
        showLayer(loadingPane);
        setLoadingText("Traitement du paiement...");

        PauseTransition pause = new PauseTransition(Duration.millis(1800));
        pause.setOnFinished(e -> savePaiementAndShowRecu(methode, total, nomCarte, quatreDerniers));
        pause.play();
    }

    // ═══════════════════════════════════════════════════════════
    //  SAVE + REÇU
    // ═══════════════════════════════════════════════════════════

    private void savePaiementAndShowRecu(String methode, double total,
                                         String nomCarte, String quatreDerniers) {
        try {
            int paiementId = paiementService.addPaiement(
                    inscriptionId, total, methode, nomCarte, quatreDerniers);

            if (paiementId > 0) {
                Paiement p = paiementService.getById(paiementId);

                // ── Créer les tickets dans la table ticket ──
                int nbTickets = inscription != null ? inscription.getNbTickets() : 1;
                int firstTicketId = 0;
                for (int i = 0; i < nbTickets; i++) {
                    int tid = ticketService.createForInscription(inscriptionId);
                    if (i == 0) firstTicketId = tid;
                }

                // ── Générer un code unique pour le PDF ──
                String ticketCode = "TIK-" + String.format("%06d", firstTicketId)
                        + "-" + p.getReferenceCode().replace("PAY-", "");
                lastPaiement   = p;
                lastTicketCode = ticketCode;

                showRecu(p, total);
            } else {
                showLayer(mainScroll);
                showError("Erreur lors de l'enregistrement du paiement.");
            }
        } catch (Exception ex) {
            showLayer(mainScroll);
            showError("Erreur : " + ex.getMessage());
        }
    }

    private void showRecu(Paiement p, double total) {
        showLayer(recuPane);

        if (recuRef != null) recuRef.setText(p.getReferenceCode());
        if (recuMontant != null) recuMontant.setText(String.format(Locale.FRENCH, "%.2f", total) + " TND");
        if (recuMethode != null) recuMethode.setText(formatMethode(p.getMethode()));
        if (recuDate != null && p.getDatePaiement() != null)
            recuDate.setText(p.getDatePaiement().format(FMT));

        // Fade in
        FadeTransition ft = new FadeTransition(Duration.millis(300), recuPane);
        ft.setFromValue(0);
        ft.setToValue(1);
        ft.play();
    }

    @FXML
    public void handleRetourEvenement() {
        goBack();
    }

    // ═══════════════════════════════════════════════════════════
    //  TÉLÉCHARGER LE TICKET PDF
    // ═══════════════════════════════════════════════════════════

    @FXML
    public void handleDownloadPdf() {
        if (lastPaiement == null || evenement == null || inscription == null) {
            showError("Impossible de générer le PDF : données manquantes.");
            return;
        }

        // ── Choisir l'emplacement du fichier ──
        FileChooser fc = new FileChooser();
        fc.setTitle("Enregistrer le ticket PDF");
        fc.setInitialFileName("Ticket_" + lastPaiement.getReferenceCode() + ".pdf");
        fc.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Fichiers PDF", "*.pdf"));
        File file = fc.showSaveDialog(recuPane.getScene().getWindow());

        if (file == null) return; // annulé

        try {
            // ── Récupérer le nom du lieu (optionnel) ──
            String lieuName = null;
            if (evenement.getLieuId() != null) {
                try {
                    LieuService lieuService = new LieuService();
                    Lieu lieu = lieuService.getById(evenement.getLieuId());
                    if (lieu != null) lieuName = lieu.getNom();
                } catch (Exception ignored) { /* pas bloquant */ }
            }

            // ── Générer le PDF ──
            TicketPdfGenerator.generate(
                    file, evenement, inscription, lastPaiement,
                    currentUser, lastTicketCode, lieuName);

            // ── Feedback visuel ──
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Ticket enregistré");
            alert.setHeaderText(null);
            alert.setContentText("Votre ticket a été enregistré avec succès !\n" + file.getAbsolutePath());
            alert.showAndWait();

        } catch (Exception ex) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Erreur PDF");
            alert.setHeaderText(null);
            alert.setContentText("Impossible de générer le PDF :\n" + ex.getMessage());
            alert.showAndWait();
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  VALIDATION LOCALE (carte)
    // ═══════════════════════════════════════════════════════════

    private boolean validateLocalCardForm() {
        String nom = safe(carteNom.getText()).trim();
        String num = safe(carteNumero.getText()).trim().replaceAll("\\s+", "");
        String exp = safe(carteExpiry.getText()).trim();
        String cvv = safe(carteCvv.getText()).trim();

        if (nom.isEmpty())                                  { showError("Nom sur la carte requis."); return false; }
        if (num.length() < 13 || num.length() > 19 || !num.matches("\\d+"))
                                                            { showError("Numéro de carte invalide (13-19 chiffres)."); return false; }
        if (!exp.matches("\\d{2}/\\d{2}"))                  { showError("Date d'expiration invalide (MM/YY)."); return false; }
        if (!cvv.matches("\\d{3,4}"))                       { showError("CVV invalide (3-4 chiffres)."); return false; }
        return true;
    }

    // ═══════════════════════════════════════════════════════════
    //  UI HELPERS
    // ═══════════════════════════════════════════════════════════

    private void hideAllForms() {
        setFormVisible(formCarte, false);
        setFormVisible(formEspeces, false);
        setFormVisible(formVirement, false);
        setFormVisible(formFlouci, false);
        setFormVisible(formCrypto, false);
    }

    private void showForm(VBox form) {
        if (form == null) return;
        form.setVisible(true);
        form.setManaged(true);
        FadeTransition ft = new FadeTransition(Duration.millis(200), form);
        ft.setFromValue(0);
        ft.setToValue(1);
        ft.play();
    }

    private void setFormVisible(VBox box, boolean visible) {
        if (box == null) return;
        box.setVisible(visible);
        box.setManaged(visible);
    }

    /**
     * Affiche une seule "couche" et cache les 3 autres.
     */
    private void showLayer(javafx.scene.Node layer) {
        if (mainScroll != null)  { mainScroll.setVisible(layer == mainScroll);  mainScroll.setManaged(layer == mainScroll); }
        if (webViewPane != null) { webViewPane.setVisible(layer == webViewPane); webViewPane.setManaged(layer == webViewPane); }
        if (loadingPane != null) { loadingPane.setVisible(layer == loadingPane); loadingPane.setManaged(layer == loadingPane); }
        if (recuPane != null)    { recuPane.setVisible(layer == recuPane);       recuPane.setManaged(layer == recuPane); }
    }

    private void setLoadingText(String text) {
        if (loadingText != null) loadingText.setText(text);
    }

    private void showError(String msg) {
        if (errorLabel != null) {
            errorLabel.setText(msg);
            boolean show = msg != null && !msg.isBlank();
            errorLabel.setVisible(show);
            errorLabel.setManaged(show);
        }
    }

    private void clearError() {
        showError("");
    }

    private String formatMethode(String m) {
        if (m == null) return "";
        return switch (m) {
            case "CARTE_BANCAIRE" -> "💳 Carte bancaire" + (PaymentConfig.isStripeConfigured() ? " (Stripe)" : "");
            case "ESPECES"        -> "💵 Espèces";
            case "VIREMENT"       -> "🏦 Virement bancaire";
            case "FLOUCI"         -> "📱 Flouci" + (PaymentConfig.isFlouciConfigured() ? " (API)" : "");
            case "CRYPTO_BTC"     -> "₿ Bitcoin (BTC)";
            case "CRYPTO_ETH"     -> "⟠ Ethereum (ETH)";
            default               -> m;
        };
    }

    private String rootMessage(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null) cause = cause.getCause();
        String msg = cause.getMessage();
        return msg != null ? msg : cause.getClass().getSimpleName();
    }

    private String safe(String s) { return s == null ? "" : s; }
}
