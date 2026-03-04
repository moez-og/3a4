package controllers.front.sorties.album;

import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.media.MediaPlayer.Status;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;
import models.notifications.Notification;
import models.notifications.NotificationType;
import models.sorties.AnnonceSortie;
import models.sorties.ParticipationSortie;
import models.sorties.SortieMedia;
import models.sorties.SortieRecap;
import models.users.User;
import services.notifications.NotificationService;
import services.sorties.ParticipationSortieService;
import services.sorties.SortieMediaService;
import services.sorties.SortieRecapService;

import java.awt.Desktop;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class AlbumRecapController {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @FXML private Label lblStatusHint;
    @FXML private Label lblUpdateAvailable;
    @FXML private Button btnPickFiles;
    @FXML private Button btnRefresh;
    @FXML private Label lblCount;
    @FXML private Label lblEmpty;
    @FXML private ScrollPane galleryScroll;
    @FXML private TilePane galleryPane;

    @FXML private Label lblRecapMeta;
    @FXML private ProgressIndicator recapSpinner;
    @FXML private Button btnGenerate;
    @FXML private Button btnRegenerate;
    @FXML private ScrollPane versionsScroll;
    @FXML private VBox versionsBox;
    @FXML private MediaView mediaView;
    @FXML private Label lblNoVideo;
    @FXML private TextField tfVideoTitle;
    @FXML private TextField tfMood;
    @FXML private HBox recapMetaRow;

    @FXML private StackPane playerWrap;
    @FXML private VBox playerOverlay;
    @FXML private ProgressIndicator playerOverlaySpinner;
    @FXML private Label lblPlayerOverlay;

    private final SortieMediaService mediaService = new SortieMediaService();
    private final SortieRecapService recapService = new SortieRecapService();
    private final ParticipationSortieService participationService = new ParticipationSortieService();
    private final NotificationService notificationService = new NotificationService();

    private User currentUser;
    private AnnonceSortie currentSortie;

    private List<SortieMedia> currentMedia = new ArrayList<>();

    private boolean updateAvailable = false;

    private MediaPlayer recapPlayer;

    // Prevent layout oscillations (viewport width can fluctuate when scrollbars appear/disappear).
    private int lastGalleryCols = -1;
    private double lastGalleryTileW = -1;

    // Viewer state
    private Stage viewerStage;
    private ImageView viewerImage;
    private MediaView viewerMediaView;
    private MediaPlayer viewerPlayer;
    private Label viewerMeta;
    private int viewerIndex = 0;

    @FXML
    private void initialize() {
        refreshUiEmptyState();
        setupRecapPlayerSizing();
        setupGalleryResponsiveLayout();
        setRecapMetaVisible(false);
        setPlayerOverlay(false, "", false);

        // Allow replay by clicking the player area.
        if (playerWrap != null) {
            playerWrap.setOnMouseClicked(e -> {
                if (e.getButton() != MouseButton.PRIMARY) return;
                if (recapPlayer == null) return;
                try {
                    recapPlayer.seek(Duration.ZERO);
                    recapPlayer.play();
                    setPlayerOverlay(false, "", false);
                } catch (Exception ignored) {}
            });
        }
    }

    private void setupGalleryResponsiveLayout() {
        if (galleryScroll == null || galleryPane == null) return;

        galleryScroll.viewportBoundsProperty().addListener((obs, oldV, v) -> {
            if (v == null) return;
            // Reserve a bit of space so column computation doesn't flip-flop when the v-scrollbar
            // becomes needed (which shrinks the viewport width by ~14-18px).
            double w = v.getWidth() - 18;
            if (w <= 0) return;

            double gap = galleryPane.getHgap();

            // Choose columns first, then compute tile width to fill nicely.
            int cols = (int) Math.floor((w + gap) / (280 + gap));
            if (cols < 1) cols = 1;
            if (cols > 6) cols = 6;

            double tileW = (w - gap * Math.max(0, cols - 1)) / cols;
            tileW = Math.max(240, Math.min(360, tileW));

            // Avoid thrashing (tiny diffs can trigger re-layout loops).
            boolean colsChanged = cols != lastGalleryCols;
            boolean tileChanged = lastGalleryTileW < 0 || Math.abs(tileW - lastGalleryTileW) > 0.75;
            if (!colsChanged && !tileChanged) return;

            lastGalleryCols = cols;
            lastGalleryTileW = tileW;

            final int colsFinal = cols;
            final double tileWFinal = tileW;

            Platform.runLater(() -> {
                try {
                    galleryPane.setPrefColumns(colsFinal);
                    galleryPane.setPrefTileWidth(tileWFinal);
                } catch (Exception ignored) {}
            });
        });
    }

    private void setRecapMetaVisible(boolean show) {
        if (recapMetaRow != null) {
            recapMetaRow.setVisible(show);
            recapMetaRow.setManaged(show);
        }
    }

    private void setPlayerOverlay(boolean show, String message, boolean spinning) {
        if (playerOverlay == null) return;
        playerOverlay.setVisible(show);
        playerOverlay.setManaged(show);
        if (lblPlayerOverlay != null) lblPlayerOverlay.setText(message == null ? "" : message);
        if (playerOverlaySpinner != null) {
            playerOverlaySpinner.setVisible(spinning);
            playerOverlaySpinner.setManaged(spinning);
        }
    }

    private void setupRecapPlayerSizing() {
        if (playerWrap == null || mediaView == null) return;

        // Responsive sizing (fixes overflow / misplacement on different window sizes).
        mediaView.fitWidthProperty().bind(playerWrap.widthProperty().subtract(20));
        mediaView.fitHeightProperty().bind(playerWrap.heightProperty().subtract(20));
        mediaView.setSmooth(true);

        // Clip to rounded container for a clean, pro card look.
        Rectangle clip = new Rectangle();
        clip.setArcWidth(32);
        clip.setArcHeight(32);
        clip.widthProperty().bind(playerWrap.widthProperty());
        clip.heightProperty().bind(playerWrap.heightProperty());
        playerWrap.setClip(clip);
    }

    private void animateRecapIn() {
        if (mediaView == null) return;
        FadeTransition ft = new FadeTransition(Duration.millis(220), mediaView);
        ft.setFromValue(0.0);
        ft.setToValue(1.0);
        ft.play();
    }

    public void setContext(User user, AnnonceSortie sortie) {
        this.currentUser = user;
        this.currentSortie = sortie;

        boolean finished = sortie != null && "TERMINEE".equalsIgnoreCase(safe(sortie.getStatut()).trim());
        if (lblStatusHint != null) {
            lblStatusHint.setText(finished ? "Album disponible" : "Disponible après la sortie");
        }

        setEnabled(finished && user != null && user.getId() > 0);
        refresh();
    }

    private void setEnabled(boolean enabled) {
        if (btnPickFiles != null) btnPickFiles.setDisable(!enabled);
        if (btnGenerate != null) btnGenerate.setDisable(!enabled);
        if (btnRegenerate != null) btnRegenerate.setDisable(!enabled);
    }

    private String recapLabel(SortieRecap r) {
        if (r == null) return "";
        String label = safe(r.getVersionLabel()).trim();
        if (!label.isBlank()) return label;
        return (r.getVersion() <= 1) ? "Première version" : ("Version " + r.getVersion());
    }

    private boolean isEnabledForUpload() {
        return currentUser != null && currentUser.getId() > 0
                && currentSortie != null && currentSortie.getId() > 0
                && "TERMINEE".equalsIgnoreCase(safe(currentSortie.getStatut()).trim());
    }

    @FXML
    private void pickFiles() {
        if (!isEnabledForUpload()) return;

        FileChooser fc = new FileChooser();
        fc.setTitle("Choisir des photos / vidéos");
        fc.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.webp"),
                new FileChooser.ExtensionFilter("Vidéos", "*.mp4", "*.m4v", "*.mov"),
                new FileChooser.ExtensionFilter("Tout", "*.*")
        );

        List<File> files = fc.showOpenMultipleDialog(resolveStage());
        if (files == null || files.isEmpty()) return;
        handleUploadFiles(files);
    }

    @FXML
    public void refresh() {
        if (currentSortie == null || currentSortie.getId() <= 0) {
            currentMedia = new ArrayList<>();
            refreshUi();
            return;
        }

        try {
            currentMedia = mediaService.listBySortie(currentSortie.getId());
        } catch (Exception e) {
            currentMedia = new ArrayList<>();
            error("Album", "Impossible de charger les médias", safe(e.getMessage()));
        }

        refreshUi();
        refreshRecaps();
        refreshUpdateBadge();
    }

    private void refreshUi() {
        if (galleryPane != null) galleryPane.getChildren().clear();

        long count = currentMedia == null ? 0 : currentMedia.size();
        if (lblCount != null) lblCount.setText(count + (count <= 1 ? " média" : " médias"));

        boolean empty = (currentMedia == null || currentMedia.isEmpty());
        if (lblEmpty != null) { lblEmpty.setVisible(empty); lblEmpty.setManaged(empty); }

        if (!empty && galleryPane != null) {
            int i = 0;
            for (SortieMedia m : currentMedia) {
                final int idx = i;
                galleryPane.getChildren().add(mediaTile(m, idx));
                i++;
            }
        }

        refreshUiEmptyState();
    }

    private void refreshUiEmptyState() {
        if (galleryScroll != null) {
            galleryScroll.setDisable(false);
        }
        if (lblNoVideo != null) {
            // default: show unless we load a player
            if (recapPlayer == null) {
                lblNoVideo.setVisible(true);
                lblNoVideo.setManaged(true);
            }
        }
    }

    private Node mediaTile(SortieMedia m, int idx) {
        VBox tile = new VBox(8);
        tile.getStyleClass().add("albumTile");

        StackPane thumbWrap = new StackPane();
        thumbWrap.setMinHeight(170);
        thumbWrap.setPrefHeight(170);

        Rectangle clip = new Rectangle();
        clip.setArcWidth(24);
        clip.setArcHeight(24);
        clip.widthProperty().bind(thumbWrap.widthProperty());
        clip.heightProperty().bind(thumbWrap.heightProperty());
        thumbWrap.setClip(clip);

        if (m.isImage()) {
            Path p = mediaService.resolvePath(m.getFilePath());
            ImageView iv = new ImageView();
            iv.getStyleClass().add("albumThumb");
            iv.setPreserveRatio(true);
            iv.fitWidthProperty().bind(thumbWrap.widthProperty());
            iv.fitHeightProperty().bind(thumbWrap.heightProperty());
            iv.setSmooth(true);
            iv.setCache(true);
            try {
                if (p != null && Files.exists(p)) {
                    Image img = new Image(p.toUri().toString(), 1100, 800, true, true, true);
                    iv.setImage(img);
                }
            } catch (Exception ignored) {}
            thumbWrap.getChildren().add(iv);
        } else {
            Region placeholder = new Region();
            placeholder.setStyle("-fx-background-color: rgba(15,23,42,0.06);");
            placeholder.setMinHeight(170);
            placeholder.setPrefHeight(170);
            Label tag = new Label("VIDÉO");
            tag.getStyleClass().add("albumVideoTag");
            StackPane.setAlignment(tag, javafx.geometry.Pos.TOP_LEFT);
            StackPane.setMargin(tag, new javafx.geometry.Insets(10, 0, 0, 10));
            thumbWrap.getChildren().addAll(placeholder, tag);
        }

        String author = safe(m.getAuthorName()).trim();
        if (author.isBlank()) author = "Utilisateur #" + m.getUserId();

        String when = (m.getUploadedAt() == null) ? "" : DT.format(m.getUploadedAt());
        Label meta = new Label(author + (when.isBlank() ? "" : (" • " + when)));
        meta.getStyleClass().add("albumMetaLine");
        meta.setMinWidth(0);
        meta.setMaxWidth(Double.MAX_VALUE);
        meta.setWrapText(true);

        HBox actions = new HBox(10);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button del = new Button("Supprimer");
        del.getStyleClass().add("albumDeleteBtn");

        boolean canDelete = currentUser != null && currentUser.getId() > 0 && m.getUserId() == currentUser.getId();
        del.setVisible(canDelete);
        del.setManaged(canDelete);

        del.setOnAction(e -> {
            if (!canDelete) return;
            if (!confirm("Suppression", "Supprimer ce média ?")) return;

            try {
                boolean ok = mediaService.delete(m.getId(), currentUser.getId());
                if (ok) {
                    try {
                        Path p = mediaService.resolvePath(m.getFilePath());
                        if (p != null) Files.deleteIfExists(p);
                    } catch (Exception ignored) {}
                    refresh();
                } else {
                    info("Suppression", "Suppression refusée (auteur uniquement).");
                }
            } catch (Exception ex) {
                error("Suppression", "Impossible de supprimer", safe(ex.getMessage()));
            }
        });

        actions.getChildren().addAll(spacer, del);

        tile.getChildren().addAll(thumbWrap, meta, actions);

        tile.setOnMouseClicked(e -> {
            if (e.getButton() != MouseButton.PRIMARY) return;
            openViewer(idx);
        });

        return tile;
    }

    private void handleUploadFiles(List<File> files) {
        if (files == null || files.isEmpty()) return;
        if (currentSortie == null || currentSortie.getId() <= 0) return;
        if (currentUser == null || currentUser.getId() <= 0) return;

        int sortieId = currentSortie.getId();

        Path targetDir = Paths.get(System.getProperty("user.dir"), "uploads", "sorties", String.valueOf(sortieId));
        try {
            Files.createDirectories(targetDir);
        } catch (Exception e) {
            error("Upload", "Impossible de créer le dossier uploads", safe(e.getMessage()));
            return;
        }

        int added = 0;
        for (File f : files) {
            if (f == null || !f.exists() || !f.isFile()) continue;

            String name = safe(f.getName()).trim();
            String ext = extensionOf(name);
            String type = guessMediaType(ext);

            String outName = System.currentTimeMillis() + "_" + UUID.randomUUID().toString().replace("-", "") + (ext.isBlank() ? "" : ("." + ext));
            Path dest = targetDir.resolve(outName).normalize();

            try {
                Files.copy(f.toPath(), dest, StandardCopyOption.REPLACE_EXISTING);
                String rel = portableRel(Paths.get("uploads", "sorties", String.valueOf(sortieId), outName));
                mediaService.add(sortieId, currentUser.getId(), rel, type);
                added++;
            } catch (Exception ex) {
                System.err.println("Upload failed: " + ex.getMessage());
            }
        }

        if (added > 0) {
            info("Upload", added + " fichier(s) ajouté(s).");
        }
        refresh();
    }

    private void refreshRecaps() {
        if (currentSortie == null || currentSortie.getId() <= 0) return;

        List<SortieRecap> list;
        try {
            list = recapService.listBySortie(currentSortie.getId());
        } catch (Exception e) {
            list = new ArrayList<>();
        }

        boolean has = list != null && !list.isEmpty();
        if (btnGenerate != null) { btnGenerate.setVisible(!has); btnGenerate.setManaged(!has); }
        if (btnRegenerate != null) { btnRegenerate.setVisible(has); btnRegenerate.setManaged(has); }

        renderVersionsTimeline(list);

        if (has) {
            SortieRecap latest = list.get(0);
            loadRecap(latest);
        } else {
            unloadRecap();
        }

        if (lblRecapMeta != null) {
            lblRecapMeta.setText(has ? ("" + list.size() + " version(s)") : "");
        }
    }

    private void renderVersionsTimeline(List<SortieRecap> list) {
        if (versionsBox == null) return;
        versionsBox.getChildren().clear();

        if (list == null || list.isEmpty()) return;

        boolean canDelete = canDeleteRecaps();

        for (SortieRecap r : list) {
            boolean isFinal = "Version Finale".equalsIgnoreCase(safe(r.getVersionLabel()).trim());

            VBox card = new VBox(6);
            card.getStyleClass().add("recapVersionCard");
            if (isFinal) card.getStyleClass().add("final");

            HBox head = new HBox(10);
            head.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

            Label title = new Label(recapLabel(r));
            title.getStyleClass().add("recapVersionTitle");
            Region sp = new Region();
            HBox.setHgrow(sp, Priority.ALWAYS);

            Label badge = new Label("Version Finale");
            badge.getStyleClass().add("recapFinalBadge");
            badge.setVisible(isFinal);
            badge.setManaged(isFinal);

            head.getChildren().addAll(title, sp, badge);

            String when = (r.getGeneratedAt() == null) ? "" : DT.format(r.getGeneratedAt());
            String size = recapFileSize(r);
            String metaTxt = when;
            if (!size.isBlank()) metaTxt = metaTxt.isBlank() ? size : (metaTxt + " • " + size);
            Label meta = new Label(metaTxt);
            meta.getStyleClass().add("recapVersionMeta");

            HBox actions = new HBox(10);
            actions.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

            Button play = new Button("Lire");
            play.getStyleClass().add(isFinal ? "primaryBtn" : "ghostBtn");
            play.setOnAction(e -> loadRecap(r));

            Button del = new Button("Supprimer");
            del.getStyleClass().add("albumDeleteBtn");
            del.setVisible(canDelete);
            del.setManaged(canDelete);
            del.setOnAction(e -> {
                if (!canDelete) return;
                if (!confirm("Suppression", "Supprimer cette version du récap ?")) return;
                try {
                    Path p = recapService.resolvePath(r.getVideoPath());
                    boolean ok = recapService.delete(r.getId());
                    if (ok) {
                        try { if (p != null) Files.deleteIfExists(p); } catch (Exception ignored) {}
                        refresh();
                    }
                } catch (Exception ex) {
                    error("Suppression", "Impossible de supprimer", safe(ex.getMessage()));
                }
            });

            actions.getChildren().addAll(play, del);

            card.getChildren().addAll(head, meta, actions);
            versionsBox.getChildren().add(card);
        }
    }

    private String recapFileSize(SortieRecap r) {
        if (r == null) return "";
        try {
            Path p = recapService.resolvePath(r.getVideoPath());
            if (p == null || !Files.exists(p)) return "";
            long bytes = Files.size(p);
            if (bytes < 1024) return bytes + " B";
            double kb = bytes / 1024.0;
            if (kb < 1024) return String.format(Locale.ROOT, "%.0f KB", kb);
            double mb = kb / 1024.0;
            if (mb < 1024) return String.format(Locale.ROOT, "%.1f MB", mb);
            double gb = mb / 1024.0;
            return String.format(Locale.ROOT, "%.2f GB", gb);
        } catch (Exception ignored) {
            return "";
        }
    }

    private boolean canDeleteRecaps() {
        if (currentUser == null || currentUser.getId() <= 0) return false;
        if (currentSortie == null) return false;
        if (isAdmin(currentUser)) return true;
        return currentSortie.getUserId() == currentUser.getId();
    }

    private boolean isAdmin(User u) {
        if (u == null) return false;
        String role = safe(u.getRole()).trim().toLowerCase(Locale.ROOT);
        return role.contains("admin");
    }

    private void loadRecap(SortieRecap r) {
        if (r == null) return;

        try {
            Path p = recapService.resolvePath(r.getVideoPath());
            if (p == null || !Files.exists(p)) {
                unloadRecap();
                return;
            }

            disposeRecapPlayer();

            Media media = new Media(p.toUri().toString());
            recapPlayer = new MediaPlayer(media);
            recapPlayer.setAutoPlay(true);

            recapPlayer.setOnEndOfMedia(() -> Platform.runLater(() -> {
                try {
                    recapPlayer.seek(Duration.ZERO);
                    recapPlayer.pause();
                } catch (Exception ignored) {}
                setPlayerOverlay(true, "Terminé — clique sur le player ou sur Lire", false);
            }));

            if (mediaView != null) mediaView.setOpacity(0.0);

            setPlayerOverlay(true, "Chargement…", true);

            media.setOnError(() -> {
                try {
                    if (media.getError() != null) {
                        String msg = safe(media.getError().getMessage());
                        error("Vidéo", "Lecture impossible", msg);
                        setPlayerOverlay(true, "Lecture impossible", false);
                    }
                } catch (Exception ignored) {}
            });

            recapPlayer.setOnError(() -> {
                try {
                    if (recapPlayer.getError() != null) {
                        String msg = safe(recapPlayer.getError().getMessage());
                        error("Vidéo", "Lecture impossible", msg);
                        setPlayerOverlay(true, "Lecture impossible", false);
                    }
                } catch (Exception ignored) {}
            });

            recapPlayer.statusProperty().addListener((o, a, b) -> {
                if (b == null) return;
                if (b == Status.READY || b == Status.PLAYING) {
                    Platform.runLater(() -> setPlayerOverlay(false, "", false));
                } else if (b == Status.STALLED) {
                    Platform.runLater(() -> setPlayerOverlay(true, "Chargement…", true));
                }
            });

            recapPlayer.setOnReady(() -> {
                Platform.runLater(() -> {
                    try { recapPlayer.play(); } catch (Exception ignored) {}
                    animateRecapIn();
                    setPlayerOverlay(false, "", false);
                });
            });

            if (mediaView != null) mediaView.setMediaPlayer(recapPlayer);
            if (lblNoVideo != null) { lblNoVideo.setVisible(false); lblNoVideo.setManaged(false); }

            // Minimal UI: show meta row only if we have values (e.g., after generation in this session).
            boolean hasMeta = (tfVideoTitle != null && !safe(tfVideoTitle.getText()).isBlank())
                    || (tfMood != null && !safe(tfMood.getText()).isBlank());
            setRecapMetaVisible(hasMeta);

        } catch (Exception e) {
            unloadRecap();
            try {
                String msg = safe(e.getMessage());
                if (msg.isBlank()) msg = "Lecture impossible.";
                error("Vidéo", "Lecture impossible", msg);
            } catch (Exception ignored) {}
            setPlayerOverlay(true, "Lecture impossible", false);
        }
    }

    private void unloadRecap() {
        disposeRecapPlayer();
        if (mediaView != null) mediaView.setMediaPlayer(null);
        if (lblNoVideo != null) { lblNoVideo.setVisible(true); lblNoVideo.setManaged(true); }
        if (tfVideoTitle != null) tfVideoTitle.setText("");
        if (tfMood != null) tfMood.setText("");
        setRecapMetaVisible(false);
        setPlayerOverlay(false, "", false);
    }

    private void disposeRecapPlayer() {
        try {
            if (recapPlayer != null) {
                recapPlayer.stop();
                recapPlayer.dispose();
            }
        } catch (Exception ignored) {}
        recapPlayer = null;
    }

    private void refreshUpdateBadge() {
        if (lblUpdateAvailable == null || currentSortie == null) return;

        boolean show = false;
        try {
            LocalDateTime latestUpload = mediaService.latestUploadAt(currentSortie.getId());
            LocalDateTime latestRecap = recapService.latestGeneratedAt(currentSortie.getId());
            if (latestUpload != null && latestRecap != null && latestUpload.isAfter(latestRecap)) show = true;
        } catch (Exception ignored) {}

        updateAvailable = show;

        lblUpdateAvailable.setVisible(show);
        lblUpdateAvailable.setManaged(show);
    }

    public boolean isUpdateAvailable() {
        return updateAvailable;
    }

    @FXML
    private void generateRecap() {
        doGenerate(false);
    }

    @FXML
    private void regenerateRecap() {
        doGenerate(true);
    }

    private void doGenerate(boolean forceNewVersion) {
        if (currentSortie == null || currentSortie.getId() <= 0) return;
        if (currentUser == null || currentUser.getId() <= 0) return;

        if (currentMedia == null || currentMedia.size() < 3) {
            info("Récap", "Ajoute au moins 3 médias pour générer un récap.");
            return;
        }

        setRecapBusy(true);

        new Thread(() -> {
            try {
                int sortieId = currentSortie.getId();
                int version = recapService.nextVersion(sortieId);

                Path outDir = Paths.get(System.getProperty("user.dir"), "outputs", "sorties", String.valueOf(sortieId));
                Files.createDirectories(outDir);

                String outFile = "recap_v" + version + ".mp4";
                String inFile = "recap_input_v" + version + ".json";

                Path outAbs = outDir.resolve(outFile).normalize();
                Path inAbs = outDir.resolve(inFile).normalize();

                writeInputJson(inAbs, outAbs);

                RunResult rr = runPython(inAbs, outAbs);
                if (!rr.ok) {
                    Platform.runLater(() -> error("Récap IA", "Génération échouée", rr.message));
                    return;
                }

                String relVideo = portableRel(Paths.get("outputs", "sorties", String.valueOf(sortieId), outFile));
                recapService.create(sortieId, relVideo, version);
                notifyRecapUpdated(sortieId, version);

                Platform.runLater(() -> {
                    if (tfVideoTitle != null && !rr.title.isBlank()) tfVideoTitle.setText(rr.title);
                    if (tfMood != null && !rr.mood.isBlank()) tfMood.setText(rr.mood);
                    refresh();
                });

            } catch (Exception ex) {
                Platform.runLater(() -> error("Récap IA", "Impossible de générer", safe(ex.getMessage())));
            } finally {
                Platform.runLater(() -> setRecapBusy(false));
            }
        }, "recap-ai-worker").start();
    }

    private void setRecapBusy(boolean busy) {
        if (recapSpinner != null) {
            recapSpinner.setVisible(busy);
            recapSpinner.setManaged(busy);
        }
        if (btnGenerate != null) btnGenerate.setDisable(busy);
        if (btnRegenerate != null) btnRegenerate.setDisable(busy);
    }

    private void notifyRecapUpdated(int sortieId, int version) {
        try {
            if (currentUser == null || currentUser.getId() <= 0) return;
            if (currentSortie == null) return;

            List<ParticipationSortie> parts = participationService.getByAnnonce(sortieId);
            if (parts == null || parts.isEmpty()) return;

            String titre = safe(currentSortie.getTitre()).trim();
            if (titre.isBlank()) titre = "Sortie #" + sortieId;

            for (ParticipationSortie p : parts) {
                if (p == null) continue;
                if (!isAccepted(p)) continue;
                int uid = p.getUserId();
                if (uid <= 0) continue;
                if (uid == currentUser.getId()) continue;

                Notification n = new Notification();
                n.setReceiverId(uid);
                n.setSenderId(currentUser.getId());
                n.setType(NotificationType.SORTIE_RECAP_UPDATED);
                n.setTitle("Récap vidéo mis à jour");
                n.setBody("Le récap de \"" + titre + "\" a été mis à jour (v" + version + ").");
                n.setEntityType("sortie");
                n.setEntityId(sortieId);
                n.setMetadataJson("{\"sortieId\":" + sortieId + ",\"tab\":\"album\"}");

                notificationService.createOrRefreshNotification(n);
            }
        } catch (Exception ignored) {
        }
    }

    private boolean isAccepted(ParticipationSortie p) {
        if (p == null) return false;
        String s = safe(p.getStatut()).trim().toUpperCase(Locale.ROOT);
        return s.equals("CONFIRMEE") || s.equals("ACCEPTEE");
    }

    private void writeInputJson(Path inputJson, Path outAbs) throws IOException {
        int sortieId = currentSortie.getId();

        List<Map<String, String>> media = new ArrayList<>();
        for (SortieMedia m : currentMedia) {
            Path p = mediaService.resolvePath(m.getFilePath());
            if (p == null) continue;
            if (!Files.exists(p)) continue;
            Map<String, String> it = new HashMap<>();
            it.put("path", p.toString());
            it.put("type", m.isVideo() ? "VIDEO" : "IMAGE");
            media.add(it);
            if (media.size() >= 14) break;
        }

        String title = safe(currentSortie.getTitre());
        String city = safe(currentSortie.getVille());
        String activity = safe(currentSortie.getTypeActivite());
        String date = (currentSortie.getDateSortie() == null) ? "" : currentSortie.getDateSortie().toString();

        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"sortie\":{");
        sb.append("\"id\":").append(sortieId).append(",");
        sb.append("\"title\":\"").append(jsonEscape(title)).append("\",");
        sb.append("\"city\":\"").append(jsonEscape(city)).append("\",");
        sb.append("\"activity\":\"").append(jsonEscape(activity)).append("\",");
        sb.append("\"date\":\"").append(jsonEscape(date)).append("\"");
        sb.append("},");
        sb.append("\"media\":[");
        for (int i = 0; i < media.size(); i++) {
            Map<String, String> it = media.get(i);
            if (i > 0) sb.append(",");
            sb.append("{");
            sb.append("\"path\":\"").append(jsonEscape(it.get("path"))).append("\",");
            sb.append("\"type\":\"").append(jsonEscape(it.get("type"))).append("\"");
            sb.append("}");
        }
        sb.append("]");
        sb.append("}");

        Files.writeString(inputJson, sb.toString(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        if (Files.exists(outAbs)) {
            try { Files.delete(outAbs); } catch (Exception ignored) {}
        }
    }

    private RunResult runPython(Path inputJson, Path outAbs) {
        List<List<String>> candidates = new ArrayList<>();
        candidates.add(List.of("python", "scripts/sortie_recap_ai.py", "--input", inputJson.toString(), "--output", outAbs.toString()));
        candidates.add(List.of("py", "scripts/sortie_recap_ai.py", "--input", inputJson.toString(), "--output", outAbs.toString()));

        IOException lastStartError = null;

        for (List<String> cmd : candidates) {
            try {
                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.directory(new File(System.getProperty("user.dir")));
                // Keep JSON (stdout) clean; Python logs go to stderr.
                pb.redirectErrorStream(false);
                pb.environment().putIfAbsent("PYTHONIOENCODING", "utf-8");

                Process p = pb.start();

                final String[] stdout = new String[1];
                final String[] stderr = new String[1];
                final Exception[] outErr = new Exception[1];
                final Exception[] errErr = new Exception[1];

                Thread tOut = new Thread(() -> {
                    try {
                        stdout[0] = readStreamUtf8(p.getInputStream());
                    } catch (Exception ex) {
                        outErr[0] = ex;
                        stdout[0] = "";
                    }
                }, "recap-python-stdout");

                Thread tErr = new Thread(() -> {
                    try {
                        stderr[0] = readStreamUtf8(p.getErrorStream());
                    } catch (Exception ex) {
                        errErr[0] = ex;
                        stderr[0] = "";
                    }
                }, "recap-python-stderr");

                tOut.setDaemon(true);
                tErr.setDaemon(true);
                tOut.start();
                tErr.start();

                int code = p.waitFor();
                try { tOut.join(2000); } catch (InterruptedException ignored) {}
                try { tErr.join(2000); } catch (InterruptedException ignored) {}

                String out = (stdout[0] == null) ? "" : stdout[0].trim();
                String err = (stderr[0] == null) ? "" : stderr[0].trim();

                // Prefer structured JSON message from stdout.
                boolean ok = parseBooleanField(out, "ok");
                if (code != 0 || !ok) {
                    String msg = parseField(out, "message");
                    String errCode = parseField(out, "error");

                    if (msg.isBlank()) msg = out;
                    if (msg.isBlank()) msg = err;
                    if (msg.isBlank()) msg = "Le script Python a échoué (code " + code + ").";

                    if (!safe(errCode).isBlank()) {
                        msg = errCode + ": " + msg;
                    }

                    // Add stderr hint (truncated) for debugging, without flooding UI.
                    if (!err.isBlank() && !msg.contains(err)) {
                        String hint = err.length() > 600 ? err.substring(0, 600) + "…" : err;
                        msg += "\n\nDétails (stderr):\n" + hint;
                    }

                    if (outErr[0] != null || errErr[0] != null) {
                        msg += "\n\nNote: lecture des flux Python partiellement échouée.";
                    }

                    return RunResult.fail(msg);
                }

                return RunResult.ok(parseField(out, "title"), parseField(out, "mood"));

            } catch (IOException ioe) {
                lastStartError = ioe;
            } catch (Exception e) {
                return RunResult.fail(safe(e.getMessage()));
            }
        }

        String msg = "Python introuvable. Installe Python 3 et assure-toi que `python` (ou `py`) est dans PATH.";
        if (lastStartError != null && !safe(lastStartError.getMessage()).isBlank()) {
            msg += "\n" + safe(lastStartError.getMessage());
        }
        return RunResult.fail(msg);
    }

    private static String readStreamUtf8(InputStream is) throws IOException {
        if (is == null) return "";
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8 * 1024];
        int r;
        while ((r = is.read(buf)) >= 0) {
            if (r == 0) continue;
            bos.write(buf, 0, r);
        }
        return bos.toString(StandardCharsets.UTF_8);
    }

    private static boolean parseBooleanField(String jsonLike, String field) {
        if (jsonLike == null) return false;
        String s = jsonLike.trim();
        String needle = "\"" + field + "\"";
        int i = s.indexOf(needle);
        if (i < 0) return false;
        int colon = s.indexOf(':', i + needle.length());
        if (colon < 0) return false;

        int j = colon + 1;
        while (j < s.length() && Character.isWhitespace(s.charAt(j))) j++;
        if (j + 4 <= s.length() && s.startsWith("true", j)) return true;
        if (j + 5 <= s.length() && s.startsWith("false", j)) return false;
        return false;
    }

    private static String parseField(String jsonLike, String field) {
        if (jsonLike == null) return "";
        String s = jsonLike.trim();
        // Try to find: "field":"value"
        String needle = "\"" + field + "\"";
        int i = s.indexOf(needle);
        if (i < 0) return "";
        int colon = s.indexOf(':', i + needle.length());
        if (colon < 0) return "";
        int q1 = s.indexOf('"', colon + 1);
        if (q1 < 0) return "";
        int q2 = s.indexOf('"', q1 + 1);
        if (q2 < 0) return "";
        return s.substring(q1 + 1, q2);
    }

    private void openViewer(int idx) {
        if (currentMedia == null || currentMedia.isEmpty()) return;
        viewerIndex = Math.max(0, Math.min(currentMedia.size() - 1, idx));

        if (viewerStage == null) {
            viewerStage = new Stage();
            viewerStage.initOwner(resolveStage());
            viewerStage.initModality(Modality.APPLICATION_MODAL);
            viewerStage.setTitle("Média");

            BorderPane root = new BorderPane();
            root.setStyle("-fx-background-color: rgba(10,20,40,0.92);");

            StackPane center = new StackPane();
            center.setStyle("-fx-padding: 18;");

            viewerImage = new ImageView();
            viewerImage.setPreserveRatio(true);
            viewerImage.setFitWidth(980);
            viewerImage.setFitHeight(660);

            viewerMediaView = new MediaView();
            viewerMediaView.setPreserveRatio(true);
            viewerMediaView.setFitWidth(980);
            viewerMediaView.setFitHeight(660);

            center.getChildren().addAll(viewerImage, viewerMediaView);

            HBox top = new HBox(10);
            top.setStyle("-fx-padding: 12; -fx-alignment: center-left;");

            Button prev = new Button("←");
            Button next = new Button("→");
            Button close = new Button("Fermer");

            prev.getStyleClass().add("ghostBtn");
            next.getStyleClass().add("ghostBtn");
            close.getStyleClass().add("primaryBtn");

            viewerMeta = new Label("");
            viewerMeta.setStyle("-fx-text-fill: rgba(255,255,255,0.85); -fx-font-weight: 800;");

            Region sp = new Region();
            HBox.setHgrow(sp, Priority.ALWAYS);

            prev.setOnAction(e -> { viewerIndex = (viewerIndex - 1 + currentMedia.size()) % currentMedia.size(); renderViewer(); });
            next.setOnAction(e -> { viewerIndex = (viewerIndex + 1) % currentMedia.size(); renderViewer(); });
            close.setOnAction(e -> viewerStage.hide());

            top.getChildren().addAll(prev, next, viewerMeta, sp, close);

            root.setTop(top);
            root.setCenter(center);

            Scene scene = new Scene(root, 1060, 760);
            viewerStage.setScene(scene);
            viewerStage.setOnHidden(e -> disposeViewerPlayer());
        }

        renderViewer();
        viewerStage.showAndWait();
    }

    private void renderViewer() {
        if (viewerImage == null || viewerMediaView == null) return;
        if (currentMedia == null || currentMedia.isEmpty()) return;

        SortieMedia m = currentMedia.get(viewerIndex);

        if (viewerMeta != null) {
            String author = safe(m.getAuthorName()).trim();
            String when = (m.getUploadedAt() == null) ? "" : DT.format(m.getUploadedAt());
            viewerMeta.setText(author + (when.isBlank() ? "" : (" • " + when)));
        }

        if (m.isImage()) {
            disposeViewerPlayer();
            viewerMediaView.setVisible(false);
            viewerImage.setVisible(true);

            try {
                Path p = mediaService.resolvePath(m.getFilePath());
                if (p != null && Files.exists(p)) {
                    viewerImage.setImage(new Image(p.toUri().toString()));
                }
            } catch (Exception ignored) {}

        } else {
            viewerImage.setVisible(false);
            viewerMediaView.setVisible(true);

            try {
                Path p = mediaService.resolvePath(m.getFilePath());
                if (p == null || !Files.exists(p)) return;

                disposeViewerPlayer();
                Media media = new Media(p.toUri().toString());
                viewerPlayer = new MediaPlayer(media);
                viewerPlayer.setAutoPlay(true);
                viewerMediaView.setMediaPlayer(viewerPlayer);

                media.setOnError(() -> {
                    try {
                        String msg = (media.getError() == null) ? "" : safe(media.getError().getMessage());
                        if (msg.isBlank()) msg = "Lecture impossible.";
                        error("Vidéo", "Lecture impossible", msg);
                    } catch (Exception ignored) {}
                });

                viewerPlayer.setOnError(() -> {
                    try {
                        String msg = (viewerPlayer.getError() == null) ? "" : safe(viewerPlayer.getError().getMessage());
                        if (msg.isBlank()) msg = "Lecture impossible.";
                        error("Vidéo", "Lecture impossible", msg);
                    } catch (Exception ignored) {}
                });

            } catch (Exception ignored) {
                disposeViewerPlayer();
            }
        }
    }

    private void disposeViewerPlayer() {
        try {
            if (viewerPlayer != null) {
                viewerPlayer.stop();
                viewerPlayer.dispose();
            }
        } catch (Exception ignored) {}
        viewerPlayer = null;
        if (viewerMediaView != null) viewerMediaView.setMediaPlayer(null);
    }

    private Stage resolveStage() {
        Node any = btnPickFiles;
        if (any == null) any = galleryScroll;
        if (any == null) any = playerWrap;
        if (any == null) any = mediaView;
        if (any == null) return null;

        Scene sc = any.getScene();
        if (sc == null) return null;
        return (Stage) sc.getWindow();
    }

    private boolean confirm(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        Optional<ButtonType> res = a.showAndWait();
        return res.isPresent() && res.get() == ButtonType.OK;
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

    private static String extensionOf(String name) {
        if (name == null) return "";
        int i = name.lastIndexOf('.');
        if (i < 0 || i == name.length() - 1) return "";
        return name.substring(i + 1).toLowerCase(Locale.ROOT);
    }

    private static String guessMediaType(String ext) {
        String e = safe(ext).toLowerCase(Locale.ROOT);
        if (Set.of("mp4", "m4v", "mov", "mkv").contains(e)) return "VIDEO";
        return "IMAGE";
    }

    private static String portableRel(Path rel) {
        if (rel == null) return "";
        return rel.toString().replace('\\', '/');
    }

    private static String jsonEscape(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 32) out.append(' ');
                    else out.append(c);
                }
            }
        }
        return out.toString();
    }

    private static String safe(String s) { return s == null ? "" : s; }

    private static final class RunResult {
        final boolean ok;
        final String message;
        final String title;
        final String mood;

        private RunResult(boolean ok, String message, String title, String mood) {
            this.ok = ok;
            this.message = message;
            this.title = title;
            this.mood = mood;
        }

        static RunResult ok(String title, String mood) {
            return new RunResult(true, "", safe(title), safe(mood));
        }

        static RunResult fail(String msg) {
            return new RunResult(false, safe(msg), "", "");
        }
    }
}
