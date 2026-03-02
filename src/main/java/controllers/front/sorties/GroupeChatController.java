package controllers.front.sorties;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;
import models.sorties.AnnonceSortie;
import models.sorties.ChatMessage;
import models.sorties.PollSnapshot;
import models.users.User;
import services.sorties.ChatService;
import services.sorties.PollService;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class GroupeChatController {

    // ── FXML ──────────────────────────────────────────────────────────
    @FXML private Label   lblTitre;
    @FXML private Label   lblMembers;
    @FXML private VBox    messagesBox;
    @FXML private ScrollPane scrollPane;
    @FXML private TextField  tfMessage;
    @FXML private Button     btnSend;
    @FXML private Label      lblAccess;
    @FXML private Button     btnNewPoll;
    @FXML private VBox       pinnedBox;

    // ── State ─────────────────────────────────────────────────────────
    private User          currentUser;
    private AnnonceSortie annonce;
    private final ChatService chatService = new ChatService();
    private final PollService pollService = new PollService();
    private int lastMessageId = 0;
    private Timeline pollTimeline;
    private boolean canWrite = false;

    // pollId -> PollCard
    private final Map<Integer, PollCard> pollCards = new ConcurrentHashMap<>();

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("dd/MM HH:mm");

    // ── Init ──────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        chatService.ensureSchema();
        pollService.ensureSchema();

        tfMessage.setOnAction(e -> sendMessage());

        btnSend.setOnAction(e -> sendMessage());

        // Enter = envoyer
        tfMessage.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER) sendMessage();
        });
    }

    public void setContext(User user, AnnonceSortie annonce) {
        this.currentUser = user;
        this.annonce     = annonce;

        if (annonce != null) {
            lblTitre.setText("💬 Chat — " + safe(annonce.getTitre()));
        }

        checkAccess();
        loadMessages();
        startPolling();
        refreshPinnedAsync();
    }

    /**
     * Version admin : accès complet garanti, badge 👑 Admin affiché.
     */
    public void setContextAdmin(User adminUser, AnnonceSortie annonce) {
        this.currentUser = adminUser;
        this.annonce     = annonce;

        if (annonce != null) {
            lblTitre.setText("💬 Chat Admin — " + safe(annonce.getTitre()));
        }

        // Admin a toujours accès
        canWrite = true;
        lblAccess.setText("👑 Mode Administrateur — accès total");
        lblAccess.setStyle("-fx-text-fill: #e67e22; -fx-font-size: 12px; -fx-font-weight: 800;");
        tfMessage.setDisable(false);
        btnSend.setDisable(false);
        tfMessage.setPromptText("Écrire un message (Admin)...");
        if (btnNewPoll != null) btnNewPoll.setDisable(false);

        loadMessages();
        startPolling();
        refreshPinnedAsync();
    }

    // ── Accès ─────────────────────────────────────────────────────────

    private void checkAccess() {
        if (currentUser == null || annonce == null) {
            setAccessDenied("Connexion requise.");
            return;
        }

        canWrite = chatService.canAccess(annonce.getId(), currentUser.getId());

        if (canWrite) {
            boolean isOwner = annonce.getUserId() == currentUser.getId();
            lblAccess.setText(isOwner ? "👑 Vous êtes l'organisateur" : "✅ Vous êtes membre du groupe");
            lblAccess.setStyle("-fx-text-fill: #27ae60; -fx-font-size: 12px;");
            tfMessage.setDisable(false);
            btnSend.setDisable(false);
            tfMessage.setPromptText("Écrire un message...");
            if (btnNewPoll != null) btnNewPoll.setDisable(false);
        } else {
            setAccessDenied("Accès réservé aux membres acceptés.");
        }
    }

    private void setAccessDenied(String msg) {
        canWrite = false;
        lblAccess.setText("🔒 " + msg);
        lblAccess.setStyle("-fx-text-fill: #e74c3c; -fx-font-size: 12px;");
        tfMessage.setDisable(true);
        btnSend.setDisable(true);
        tfMessage.setPromptText(msg);
        if (btnNewPoll != null) btnNewPoll.setDisable(true);
    }

    // ── Messages ──────────────────────────────────────────────────────

    private void loadMessages() {
        if (annonce == null) return;
        try {
            List<ChatMessage> msgs = chatService.getMessages(annonce.getId());
            messagesBox.getChildren().clear();
            pollCards.clear();
            for (ChatMessage m : msgs) {
                messagesBox.getChildren().add(buildBubble(m));
                lastMessageId = Math.max(lastMessageId, m.getId());
            }
            updateMembersLabel(msgs);
            scrollToBottom();
            markReadAsync();
        } catch (Exception e) {
            System.err.println("[Chat] loadMessages: " + e.getMessage());
        }
    }

    private void pollNewMessages() {
        if (annonce == null) return;
        try {
            List<ChatMessage> newMsgs = chatService.getMessagesAfter(annonce.getId(), lastMessageId);
            if (!newMsgs.isEmpty()) {
                Platform.runLater(() -> {
                    for (ChatMessage m : newMsgs) {
                        messagesBox.getChildren().add(buildBubble(m));
                        lastMessageId = Math.max(lastMessageId, m.getId());
                    }
                    scrollToBottom();
                    markReadAsync();
                });
            }
        } catch (Exception e) {
            System.err.println("[Chat] poll: " + e.getMessage());
        }
    }

    private void markReadAsync() {
        if (currentUser == null || annonce == null) return;
        int upTo = lastMessageId;
        if (upTo <= 0) return;
        new Thread(() -> {
            try {
                chatService.markReadUpTo(annonce.getId(), currentUser.getId(), upTo);
            } catch (Exception ignored) {
            }
        }, "chat-mark-read").start();
    }

    private void refreshPollCardsAsync() {
        if (currentUser == null) return;
        if (pollCards.isEmpty()) return;
        new Thread(() -> {
            try {
                Map<Integer, PollSnapshot> snaps = new HashMap<>();
                for (Integer pollId : pollCards.keySet()) {
                    PollSnapshot s = pollService.getSnapshot(pollId, currentUser.getId());
                    if (s != null) snaps.put(pollId, s);
                }
                if (!snaps.isEmpty()) {
                    Platform.runLater(() -> {
                        for (var e : snaps.entrySet()) {
                            PollCard c = pollCards.get(e.getKey());
                            if (c != null) c.applySnapshot(e.getValue());
                        }
                    });
                }
            } catch (Exception ignored) {}
        }).start();
    }

    private void refreshPinnedAsync() {
        if (annonce == null || currentUser == null) return;
        new Thread(() -> {
            try {
                List<Integer> ids = pollService.listPinnedPollIds(annonce.getId());
                List<PollSnapshot> snaps = new ArrayList<>();
                for (Integer id : ids) {
                    PollSnapshot s = pollService.getSnapshot(id, currentUser.getId());
                    if (s != null) snaps.add(s);
                }
                Platform.runLater(() -> renderPinned(snaps));
            } catch (Exception ignored) {}
        }).start();
    }

    private void renderPinned(List<PollSnapshot> pinned) {
        if (pinnedBox == null) return;
        pinnedBox.getChildren().clear();

        if (pinned == null || pinned.isEmpty()) {
            pinnedBox.setVisible(false);
            pinnedBox.setManaged(false);
            return;
        }

        pinnedBox.setVisible(true);
        pinnedBox.setManaged(true);

        Label title = new Label("📌 Sondages épinglés");
        title.getStyleClass().add("chatPinnedTitle");
        pinnedBox.getChildren().add(title);

        for (PollSnapshot s : pinned) {
            VBox card = buildPinnedCard(s);
            pinnedBox.getChildren().add(card);
        }
    }

    private VBox buildPinnedCard(PollSnapshot s) {
        VBox root = new VBox(6);
        root.getStyleClass().add("pollCard");

        Label q = new Label(safe(s.getQuestion()));
        q.getStyleClass().add("pollQuestion");
        q.setWrapText(true);

        String meta = "Par " + safe(s.getCreatedByName()) + " · " + (s.isOpen() ? "Ouvert" : "Clôturé") + " · " + s.getTotalVoters() + " votant(s)";
        Label m = new Label(meta);
        m.getStyleClass().add("pollMeta");

        root.getChildren().addAll(q, m);

        root.setOnMouseClicked(e -> openPollDetails(s.getId()));
        return root;
    }

    private void openPollDetails(int pollId) {
        if (pollId <= 0 || currentUser == null) return;
        PollCard card = new PollCard(pollId);
        card.refresh();

        Dialog<Void> dlg = new Dialog<>();
        dlg.setTitle("Sondage");
        dlg.getDialogPane().getStyleClass().add("pollDialog");
        dlg.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dlg.getDialogPane().setContent(card);
        dlg.getDialogPane().setPrefWidth(560);
        dlg.getDialogPane().setPrefHeight(520);
        try {
            var url = getClass().getResource("/styles/sorties/chat.css");
            if (url != null) dlg.getDialogPane().getStylesheets().add(url.toExternalForm());
        } catch (Exception ignored) {}
        dlg.show();
    }

    @FXML
    private void sendMessage() {
        if (!canWrite || currentUser == null || annonce == null) return;

        String text = tfMessage.getText().trim();
        if (text.isEmpty()) return;

        tfMessage.clear();
        tfMessage.setDisable(true);

        new Thread(() -> {
            try {
                ChatMessage sent = chatService.send(annonce.getId(), currentUser.getId(), text);
                Platform.runLater(() -> {
                    if (sent != null) {
                        messagesBox.getChildren().add(buildBubble(sent));
                        lastMessageId = Math.max(lastMessageId, sent.getId());
                        scrollToBottom();
                    }
                    tfMessage.setDisable(false);
                    tfMessage.requestFocus();
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    tfMessage.setDisable(false);
                    showError("Envoi impossible : " + e.getMessage());
                });
            }
        }).start();
    }

    // ── Bulle de message ──────────────────────────────────────────────

    private HBox buildBubble(ChatMessage m) {
        boolean isMe = (currentUser != null && m.getSenderId() == currentUser.getId());
        boolean isOwner = (annonce != null && m.getSenderId() == annonce.getUserId());

        boolean isPoll = m.getPollId() != null && "POLL".equalsIgnoreCase(safe(m.getMessageType()));

        Node messageContent;
        if (isPoll) {
            PollCard card = new PollCard(m.getPollId());
            pollCards.put(m.getPollId(), card);
            card.refresh();
            messageContent = card;
        } else {
            Label contentLbl = new Label(safe(m.getContent()));
            contentLbl.setWrapText(true);
            contentLbl.setMaxWidth(420);
            contentLbl.getStyleClass().add("chatBubbleText");
            messageContent = contentLbl;
        }

        // Heure
        String timeStr = m.getSentAt() != null ? TIME_FMT.format(m.getSentAt()) : "";
        Label timeLbl = new Label(timeStr);
        timeLbl.getStyleClass().add("chatBubbleTime");

        // Nom expéditeur (visible pour les messages des autres)
        String displayName = safe(m.getSenderName());
        if (isOwner && !isMe) displayName += " 👑";

        VBox bubble = new VBox(6);
        if (isPoll) {
            bubble.getStyleClass().add("chatPollContainer");
        } else {
            bubble.getStyleClass().add("chatBubble");
            bubble.getStyleClass().add(isMe ? "chatBubbleMe" : "chatBubbleOther");
        }
        bubble.setMaxWidth(560);
        bubble.getChildren().addAll(messageContent, timeLbl);

        HBox row = new HBox(8);
        row.setPadding(new Insets(2, 12, 2, 12));

        if (isMe) {
            row.setAlignment(Pos.CENTER_RIGHT);
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            if (isOwner) {
                // Badge Admin visible dans notre propre bulle
                Label adminBadge = new Label("👑 Admin");
                adminBadge.getStyleClass().add("chatAdminBadge");
                VBox meWithBadge = new VBox(2, adminBadge, bubble);
                meWithBadge.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
                row.getChildren().addAll(spacer, meWithBadge);
            } else {
                row.getChildren().addAll(spacer, bubble);
            }
        } else {
            row.setAlignment(Pos.CENTER_LEFT);

            // Avatar initiales
            String initials = displayName.isBlank() ? "?" :
                    displayName.trim().substring(0, 1).toUpperCase();
            Label avatar = new Label(initials);
            avatar.getStyleClass().add("chatAvatar");
            avatar.getStyleClass().add(isOwner ? "chatAvatarOwner" : "chatAvatarMember");

            Label nameLbl = new Label(displayName);
            nameLbl.getStyleClass().add("chatSenderName");

            VBox withName = new VBox(2, nameLbl, bubble);
            withName.setAlignment(Pos.TOP_LEFT);

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            row.getChildren().addAll(avatar, withName, spacer);
        }

        return row;
    }

    // ── Polling auto (toutes les 3 secondes) ─────────────────────────

    private void startPolling() {
        pollTimeline = new Timeline(new KeyFrame(Duration.seconds(3), e -> {
            new Thread(() -> {
                pollNewMessages();
                refreshPollCardsAsync();
                refreshPinnedAsync();
            }).start();
        }));
        pollTimeline.setCycleCount(Timeline.INDEFINITE);
        pollTimeline.play();
    }

    public void stopPolling() {
        if (pollTimeline != null) pollTimeline.stop();
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private void scrollToBottom() {
        Platform.runLater(() -> scrollPane.setVvalue(1.0));
    }

    private void updateMembersLabel(List<ChatMessage> msgs) {
        // Compte les expéditeurs uniques
        long unique = msgs.stream().mapToInt(ChatMessage::getSenderId).distinct().count();
        lblMembers.setText(unique + " participant(s) actif(s)");
    }

    private void showError(String msg) {
        Label err = new Label("⚠ " + msg);
        err.setStyle("-fx-text-fill: #e74c3c; -fx-padding: 4 12;");
        messagesBox.getChildren().add(err);
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private boolean isAdmin() {
        return currentUser != null && "admin".equalsIgnoreCase(safe(currentUser.getRole()).trim());
    }

    private boolean isAnnonceOwner() {
        return currentUser != null && annonce != null && annonce.getUserId() == currentUser.getId();
    }

    // ─────────────────────────────────────────────────────────────────
    //  Création sondage (Facebook-like)
    // ─────────────────────────────────────────────────────────────────

    @FXML
    private void openCreatePoll() {
        if (!canWrite || currentUser == null || annonce == null) return;

        Dialog<PollDraft> dlg = new Dialog<>();
        dlg.setTitle("Nouveau sondage");
        dlg.getDialogPane().getStyleClass().add("pollDialog");

        ButtonType createBtn = new ButtonType("Créer", ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().getButtonTypes().addAll(createBtn, ButtonType.CANCEL);

        TextArea taQuestion = new TextArea();
        taQuestion.setPromptText("Question du sondage (ex: Quelle ambiance on donne à la sortie ?) …");
        taQuestion.setWrapText(true);
        taQuestion.setPrefRowCount(3);
        taQuestion.getStyleClass().add("pollCreateQuestion");

        CheckBox cbMulti = new CheckBox("Multi-choix (plusieurs réponses possibles)");
        CheckBox cbAllowAdd = new CheckBox("Autoriser les participants à ajouter des choix");
        cbAllowAdd.setSelected(true);
        CheckBox cbPin = new CheckBox("Épingler dans le chat");

        VBox optionsBox = new VBox(8);
        optionsBox.getStyleClass().add("pollCreateOptionsBox");

        Runnable addOptionRow = () -> {
            TextField tf = new TextField();
            tf.setPromptText("Choix (ex: Chill, Challenge, Photo-shoot…)");
            tf.getStyleClass().add("pollAddField");
            tf.getStyleClass().add("pollCreateOptionField");

            Button rm = new Button("✕");
            rm.getStyleClass().add("pollActionBtnSecondary");
            rm.setOnAction(e -> optionsBox.getChildren().remove(rm.getParent()));

            HBox row = new HBox(8, tf, rm);
            row.getStyleClass().add("pollCreateOptionRow");
            HBox.setHgrow(tf, Priority.ALWAYS);
            optionsBox.getChildren().add(row);
        };

        // 2 choix par défaut
        addOptionRow.run();
        addOptionRow.run();

        Button btnAdd = new Button("+ Ajouter un choix");
        btnAdd.getStyleClass().add("pollActionBtnSecondary");
        btnAdd.setOnAction(e -> addOptionRow.run());

        Label hint = new Label("Astuce : les participants pourront aussi ajouter des choix après, si tu actives l'option.");
        hint.getStyleClass().add("pollMeta");
        hint.setWrapText(true);

        VBox root = new VBox(10,
                new Label("Question"), taQuestion,
                new Separator(),
                new Label("Choix"), optionsBox, btnAdd,
                new Separator(),
                cbMulti, cbAllowAdd, cbPin,
                hint
        );
        root.getStyleClass().add("pollCreateRoot");
        root.setPadding(new Insets(10));
        dlg.getDialogPane().setContent(root);
        dlg.getDialogPane().setPrefWidth(560);
        try {
            var url = getClass().getResource("/styles/sorties/chat.css");
            if (url != null) dlg.getDialogPane().getStylesheets().add(url.toExternalForm());
        } catch (Exception ignored) {}

        Node okBtn = dlg.getDialogPane().lookupButton(createBtn);
        okBtn.disableProperty().bind(taQuestion.textProperty().isEmpty());

        dlg.setResultConverter(bt -> {
            if (bt != createBtn) return null;
            List<String> opts = new ArrayList<>();
            for (Node n : optionsBox.getChildren()) {
                if (n instanceof HBox h) {
                    for (Node c : h.getChildren()) {
                        if (c instanceof TextField tf) {
                            String v = tf.getText() == null ? "" : tf.getText().trim();
                            if (!v.isEmpty()) opts.add(v);
                        }
                    }
                }
            }
            return new PollDraft(
                    taQuestion.getText(),
                    cbMulti.isSelected(),
                    cbAllowAdd.isSelected(),
                    cbPin.isSelected(),
                    opts
            );
        });

        dlg.showAndWait().ifPresent(draft -> {
            new Thread(() -> {
                try {
                    int pollId = pollService.createPoll(
                            annonce.getId(),
                            currentUser.getId(),
                            draft.question,
                            draft.allowMulti,
                            draft.allowAddOptions,
                            draft.pinned,
                            draft.options
                    );

                    ChatMessage msg = chatService.sendPoll(annonce.getId(), currentUser.getId(), pollId, draft.question);

                    Platform.runLater(() -> {
                        messagesBox.getChildren().add(buildBubble(msg));
                        lastMessageId = Math.max(lastMessageId, msg.getId());
                        scrollToBottom();
                        refreshPinnedAsync();
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> showError("Sondage impossible : " + safe(ex.getMessage())));
                }
            }).start();
        });
    }

    private static class PollDraft {
        final String question;
        final boolean allowMulti;
        final boolean allowAddOptions;
        final boolean pinned;
        final List<String> options;

        PollDraft(String question, boolean allowMulti, boolean allowAddOptions, boolean pinned, List<String> options) {
            this.question = question;
            this.allowMulti = allowMulti;
            this.allowAddOptions = allowAddOptions;
            this.pinned = pinned;
            this.options = options;
        }
    }

    // ─────────────────────────────────────────────────────────────────
    //  Poll card component
    // ─────────────────────────────────────────────────────────────────

    private class PollCard extends VBox {
        private final int pollId;

        private final Label badge = new Label("SONDAGE");
        private final Label questionLbl = new Label();
        private final Label metaLbl = new Label();
        private final Label closedBanner = new Label("Clôturé");

        private final VBox optionsBox = new VBox(8);
        private final Button btnVote = new Button("Voter");
        private final Button btnClear = new Button("Retirer");
        private final Button btnPin = new Button("📌 Épingler");
        private final Button btnClose = new Button("Clôturer");

        private final TextField tfAdd = new TextField();
        private final Button btnAdd = new Button("+ Ajouter");

        private boolean allowMulti = false;
        private boolean allowAddOptions = true;
        private boolean open = true;
        private boolean pinned = false;
        private boolean canManage = false;

        // When user changes selection but hasn't submitted vote yet,
        // keep it stable across auto-refreshes.
        private boolean selectionDirty = false;
        private boolean applyingSnapshot = false;

        // selection UI
        private ToggleGroup tg;
        private final Map<Integer, CheckBox> multiBoxes = new HashMap<>();

        PollCard(int pollId) {
            this.pollId = pollId;
            getStyleClass().add("pollCard");

            badge.getStyleClass().add("pollBadge");
            questionLbl.getStyleClass().add("pollQuestion");
            questionLbl.setWrapText(true);

            metaLbl.getStyleClass().add("pollMeta");
            metaLbl.setWrapText(true);

            closedBanner.getStyleClass().add("pollClosedBanner");
            closedBanner.setVisible(false);
            closedBanner.setManaged(false);

            HBox header = new HBox(8, badge, questionLbl);
            header.getStyleClass().add("pollHeaderLine");
            header.setAlignment(Pos.CENTER_LEFT);

            HBox metaLine = new HBox(8, metaLbl, new Region(), closedBanner);
            HBox.setHgrow(metaLine.getChildren().get(1), Priority.ALWAYS);
            metaLine.setAlignment(Pos.CENTER_LEFT);

            btnVote.getStyleClass().add("pollActionBtn");
            btnClear.getStyleClass().add("pollActionBtnSecondary");
            btnPin.getStyleClass().add("pollActionBtnSecondary");
            btnClose.getStyleClass().add("pollActionBtnSecondary");

            btnVote.setOnAction(e -> submitVote());
            btnClear.setOnAction(e -> clearMyVote());
            btnPin.setOnAction(e -> togglePin());
            btnClose.setOnAction(e -> closePoll());

            tfAdd.getStyleClass().add("pollAddField");
            tfAdd.setPromptText("Ajouter une option…");
            btnAdd.getStyleClass().add("pollActionBtnSecondary");
            btnAdd.setOnAction(e -> addOptionInline());
            tfAdd.setOnAction(e -> addOptionInline());

            HBox addRow = new HBox(8, tfAdd, btnAdd);
            HBox.setHgrow(tfAdd, Priority.ALWAYS);

            HBox actions = new HBox(8, btnVote, btnClear, new Region(), btnPin, btnClose);
            actions.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(actions.getChildren().get(3), Priority.ALWAYS);

            setSpacing(10);
            getChildren().addAll(header, metaLine, optionsBox, addRow, actions);
        }

        void refresh() {
            if (currentUser == null) return;
            new Thread(() -> {
                try {
                    PollSnapshot s = pollService.getSnapshot(pollId, currentUser.getId());
                    if (s != null) Platform.runLater(() -> applySnapshot(s));
                } catch (Exception ignored) {}
            }).start();
        }

        void applySnapshot(PollSnapshot s) {
            if (s == null) return;
            questionLbl.setText(safe(s.getQuestion()));
            allowMulti = s.isAllowMulti();
            allowAddOptions = s.isAllowAddOptions();
            open = s.isOpen();
            pinned = s.isPinned();

            String meta = "Par " + safe(s.getCreatedByName()) + " · " + s.getTotalVoters() + " votant(s)";
            metaLbl.setText(meta);

            closedBanner.setVisible(!open);
            closedBanner.setManaged(!open);

            btnPin.setText(pinned ? "📌 Désépingler" : "📌 Épingler");

            canManage = isAdmin() || isAnnonceOwner() || (currentUser != null && s.getCreatedBy() == currentUser.getId());
            btnClose.setVisible(canManage);
            btnClose.setManaged(canManage);
            btnPin.setVisible(canManage);
            btnPin.setManaged(canManage);

            // options UI
            Set<Integer> preserve = selectionDirty ? captureSelectedOptionIds() : null;
            applyingSnapshot = true;
            try {
                optionsBox.getChildren().clear();
                multiBoxes.clear();
                tg = allowMulti ? null : new ToggleGroup();

                for (var opt : s.getOptions()) {
                    boolean selected = (preserve != null)
                            ? preserve.contains(opt.getId())
                            : s.getMyOptionIds().contains(opt.getId());
                    optionsBox.getChildren().add(buildOptionRow(opt.getId(), opt.getText(), opt.getVotes(), opt.getPercent(), selected));
                }
            } finally {
                applyingSnapshot = false;
            }

            // add option row
            boolean canAdd = open && canWrite && (allowAddOptions || canManage);
            tfAdd.setVisible(canAdd);
            tfAdd.setManaged(canAdd);
            btnAdd.setVisible(canAdd);
            btnAdd.setManaged(canAdd);

            // vote buttons
            btnVote.setDisable(!open || !canWrite);
            btnClear.setDisable(!open || !canWrite);
            btnClear.setVisible(!s.getMyOptionIds().isEmpty());
            btnClear.setManaged(!s.getMyOptionIds().isEmpty());
            btnVote.setText(s.getMyOptionIds().isEmpty() ? "Voter" : "Mettre à jour");
        }

        private Node buildOptionRow(int optionId, String text, int votes, double pct, boolean selected) {
            Label txt = new Label(safe(text));
            txt.getStyleClass().add("pollOptionText");
            txt.setWrapText(true);

            ProgressBar bar = new ProgressBar(Math.max(0, Math.min(1, pct)));
            bar.setPrefWidth(260);
            bar.getStyleClass().add("pollOptionBar");

            String stat = votes + " · " + (int) Math.round(pct * 100) + "%";
            Label stats = new Label(stat);
            stats.getStyleClass().add("pollOptionStats");

            Node selector;
            if (allowMulti) {
                CheckBox cb = new CheckBox();
                cb.setSelected(selected);
                selector = cb;
                multiBoxes.put(optionId, cb);
            } else {
                RadioButton rb = new RadioButton();
                rb.setToggleGroup(tg);
                rb.setSelected(selected);
                selector = rb;
            }

            selector.getStyleClass().add("pollSelector");

            VBox right = new VBox(3, bar, stats);
            HBox row = new HBox(8, selector, txt, new Region(), right);
            row.setUserData(optionId);
            row.setAlignment(Pos.TOP_LEFT);
            HBox.setHgrow(row.getChildren().get(2), Priority.ALWAYS);

            row.getStyleClass().add("pollOptionRow");
            if (selected) row.getStyleClass().add("selected");

            // click anywhere to toggle/select
            row.setOnMouseClicked(e -> {
                if (!open || !canWrite) return;
                // If user clicked directly on the selector control (or its skin), let it handle the click.
                if (isTargetInside(selector, e.getTarget())) return;
                if (selector instanceof CheckBox cb) {
                    cb.setSelected(!cb.isSelected());
                } else if (selector instanceof RadioButton rb) {
                    rb.setSelected(true);
                }
            });

            // keep selected style in sync
            if (selector instanceof CheckBox cb) {
                cb.selectedProperty().addListener((obs, was, is) -> {
                    if (!applyingSnapshot && was != is) selectionDirty = true;
                    if (is) {
                        if (!row.getStyleClass().contains("selected")) row.getStyleClass().add("selected");
                    } else {
                        row.getStyleClass().remove("selected");
                    }
                });
            } else if (selector instanceof RadioButton rb) {
                rb.selectedProperty().addListener((obs, was, is) -> {
                    if (!applyingSnapshot && was != is) selectionDirty = true;
                    if (is) {
                        if (!row.getStyleClass().contains("selected")) row.getStyleClass().add("selected");
                    } else {
                        row.getStyleClass().remove("selected");
                    }
                });
            }

            return row;
        }

        private Set<Integer> captureSelectedOptionIds() {
            Set<Integer> selected = new HashSet<>();
            if (allowMulti) {
                for (var e : multiBoxes.entrySet()) {
                    if (e.getValue().isSelected()) selected.add(e.getKey());
                }
                return selected;
            }

            for (Node n : optionsBox.getChildren()) {
                if (n instanceof HBox h) {
                    Object ud = h.getUserData();
                    if (ud instanceof Integer oid) {
                        Node first = h.getChildren().get(0);
                        if (first instanceof RadioButton rb && rb.isSelected()) {
                            selected.add(oid);
                        }
                    }
                }
            }
            return selected;
        }

        private boolean isTargetInside(Node root, Object target) {
            if (root == null) return false;
            if (!(target instanceof Node n)) return false;
            while (n != null) {
                if (n == root) return true;
                n = n.getParent();
            }
            return false;
        }

        private void submitVote() {
            if (!open || currentUser == null) return;

            Set<Integer> selected = new HashSet<>();
            if (allowMulti) {
                for (var e : multiBoxes.entrySet()) {
                    if (e.getValue().isSelected()) selected.add(e.getKey());
                }
            }

            // Better mapping: store optionId on row userData
            if (!allowMulti) {
                for (Node n : optionsBox.getChildren()) {
                    if (n instanceof HBox h) {
                        Object ud = h.getUserData();
                        if (ud instanceof Integer oid) {
                            Node first = h.getChildren().get(0);
                            if (first instanceof RadioButton rb && rb.isSelected()) {
                                selected.add(oid);
                            }
                        }
                    }
                }
            }

            btnVote.setDisable(true);

            new Thread(() -> {
                try {
                    PollSnapshot snap = pollService.getSnapshot(pollId, currentUser.getId());
                    boolean multi = snap != null && snap.isAllowMulti();
                    pollService.vote(pollId, currentUser.getId(), multi, selected);
                    PollSnapshot after = pollService.getSnapshot(pollId, currentUser.getId());
                    Platform.runLater(() -> {
                        btnVote.setDisable(false);
                        selectionDirty = false;
                        if (after != null) applySnapshot(after);
                        refreshPinnedAsync();
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        btnVote.setDisable(false);
                        showError("Vote impossible : " + safe(ex.getMessage()));
                    });
                }
            }).start();
        }

        private void clearMyVote() {
            if (currentUser == null) return;
            new Thread(() -> {
                try {
                    pollService.clearVote(pollId, currentUser.getId());
                    PollSnapshot after = pollService.getSnapshot(pollId, currentUser.getId());
                    Platform.runLater(() -> {
                        selectionDirty = false;
                        if (after != null) applySnapshot(after);
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> showError("Impossible : " + safe(ex.getMessage())));
                }
            }).start();
        }

        private void addOptionInline() {
            if (!open || currentUser == null) return;
            String v = tfAdd.getText() == null ? "" : tfAdd.getText().trim();
            if (v.isEmpty()) return;
            tfAdd.clear();
            btnAdd.setDisable(true);

            new Thread(() -> {
                try {
                    if (canManage) pollService.addOptionPrivileged(pollId, currentUser.getId(), v);
                    else pollService.addOption(pollId, currentUser.getId(), v);
                    PollSnapshot after = pollService.getSnapshot(pollId, currentUser.getId());
                    Platform.runLater(() -> {
                        btnAdd.setDisable(false);
                        if (after != null) applySnapshot(after);
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        btnAdd.setDisable(false);
                        showError("Ajout impossible : " + safe(ex.getMessage()));
                    });
                }
            }).start();
        }

        private void togglePin() {
            if (currentUser == null) return;
            boolean next = !pinned;

            new Thread(() -> {
                try {
                    if (isAdmin()) pollService.setPinnedAsAdmin(pollId, next);
                    else pollService.setPinnedAsOwner(pollId, currentUser.getId(), next);
                    PollSnapshot after = pollService.getSnapshot(pollId, currentUser.getId());
                    Platform.runLater(() -> {
                        if (after != null) applySnapshot(after);
                        refreshPinnedAsync();
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> showError("Épinglage impossible : " + safe(ex.getMessage())));
                }
            }).start();
        }

        private void closePoll() {
            if (currentUser == null) return;
            if (!open) return;

            Alert a = new Alert(Alert.AlertType.CONFIRMATION);
            a.setTitle("Clôturer le sondage");
            a.setHeaderText("Clôturer ce sondage ?");
            a.setContentText("Les votes resteront visibles, mais plus personne ne pourra voter.");
            if (a.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

            new Thread(() -> {
                try {
                    if (isAdmin()) pollService.closePollAsAdmin(pollId);
                    else pollService.closePollAsOwner(pollId, currentUser.getId());
                    PollSnapshot after = pollService.getSnapshot(pollId, currentUser.getId());
                    Platform.runLater(() -> {
                        if (after != null) applySnapshot(after);
                        refreshPinnedAsync();
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> showError("Clôture impossible : " + safe(ex.getMessage())));
                }
            }).start();
        }
    }
}