package controllers.front.sorties;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.util.Duration;
import models.sorties.AnnonceSortie;
import models.sorties.ChatMessage;
import models.sorties.PollSnapshot;
import models.sorties.TaskSnapshot;
import models.users.User;
import services.sorties.ChatService;
import services.sorties.PollService;
import services.sorties.TaskService;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
    @FXML private Button     btnTasks;
    @FXML private VBox       pinnedBox;

    // ── State ─────────────────────────────────────────────────────────
    private User          currentUser;
    private AnnonceSortie annonce;
    private final ChatService chatService = new ChatService();
    private final PollService pollService = new PollService();
    private final TaskService taskService = new TaskService();
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
        taskService.ensureSchema();

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
        refreshTaskBadgeAsync();
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
        if (btnTasks != null) btnTasks.setDisable(false);

        loadMessages();
        startPolling();
        refreshPinnedAsync();
        refreshTaskBadgeAsync();
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
            if (btnTasks != null) btnTasks.setDisable(false);
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
        if (btnTasks != null) btnTasks.setDisable(true);
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

    private void refreshTaskBadgeAsync() {
        if (annonce == null || currentUser == null) return;
        if (btnTasks == null) return;

        new Thread(() -> {
            try {
                TaskService.TaskStats stats = taskService.getStats(annonce.getId());
                long mine = taskService.countMyOpenTasks(annonce.getId(), currentUser.getId());

                String base = "🧩 Tâches";
                String suffix;
                if (stats.total <= 0) {
                    suffix = "";
                } else {
                    suffix = "  " + stats.done + "/" + stats.total;
                }
                String mineBadge = (mine > 0) ? "  •" + mine : "";

                Platform.runLater(() -> btnTasks.setText(base + suffix + mineBadge));
            } catch (Exception ignored) {
            }
        }, "tasks-badge").start();
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

        boolean isSystem = "SYSTEM".equalsIgnoreCase(safe(m.getMessageType()));
        if (isSystem) return buildSystemRow(m);

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

        // Nom expéditeur
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

        if (isPoll && messageContent instanceof PollCard pc) {
            pc.playAppear();
        }

        HBox row = new HBox(8);
        row.setPadding(new Insets(2, 12, 2, 12));

        if (isMe) {
            row.setAlignment(Pos.CENTER_RIGHT);
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            if (isOwner) {
                Label adminBadge = new Label("👑 Admin");
                adminBadge.getStyleClass().add("chatAdminBadge");
                VBox meWithBadge = new VBox(2, adminBadge, bubble);
                meWithBadge.setAlignment(Pos.CENTER_RIGHT);
                row.getChildren().addAll(spacer, meWithBadge);
            } else {
                row.getChildren().addAll(spacer, bubble);
            }
        } else {
            row.setAlignment(Pos.CENTER_LEFT);

            String initials = displayName.isBlank() ? "?" : displayName.trim().substring(0, 1).toUpperCase();
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

    private static void microAppear(Node node) {
        if (node == null) return;
        node.setOpacity(0);
        node.setTranslateY(8);

        FadeTransition ft = new FadeTransition(Duration.millis(160), node);
        ft.setFromValue(0);
        ft.setToValue(1);

        TranslateTransition tt = new TranslateTransition(Duration.millis(160), node);
        tt.setFromY(8);
        tt.setToY(0);

        ft.play();
        tt.play();
    }

    private static void microPulse(Node node) {
        if (node == null) return;
        ScaleTransition st = new ScaleTransition(Duration.millis(120), node);
        st.setFromX(1);
        st.setFromY(1);
        st.setToX(1.02);
        st.setToY(1.02);
        st.setAutoReverse(true);
        st.setCycleCount(2);
        st.play();
    }

    private HBox buildSystemRow(ChatMessage m) {
        String timeStr = m.getSentAt() != null ? TIME_FMT.format(m.getSentAt()) : "";
        Label lbl = new Label(safe(m.getContent()));
        lbl.setWrapText(true);
        lbl.setMaxWidth(560);
        lbl.getStyleClass().add("chatSystemMessage");

        Label time = new Label(timeStr);
        time.getStyleClass().add("chatSystemTime");

        VBox box = new VBox(4, lbl, time);
        box.setAlignment(Pos.CENTER);

        HBox row = new HBox(box);
        row.setAlignment(Pos.CENTER);
        row.setPadding(new Insets(6, 12, 6, 12));
        return row;
    }

    // ── Polling auto ──────────────────────────────────────────────────

    private void startPolling() {
        pollTimeline = new Timeline(new KeyFrame(Duration.seconds(3), e -> {
            new Thread(() -> {
                pollNewMessages();
                refreshPollCardsAsync();
                refreshPinnedAsync();
                refreshTaskBadgeAsync();
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

        List<TextField> optionFields = new ArrayList<>();

        Runnable syncRemoveButtons = () -> {
            boolean disable = optionFields.size() <= 2;
            for (Node n : optionsBox.getChildren()) {
                if (!(n instanceof HBox h)) continue;
                for (Node c : h.getChildren()) {
                    if (c instanceof Button b && b.getStyleClass().contains("pollActionBtnSecondary")) {
                        b.setDisable(disable);
                    }
                }
            }
        };

        Runnable updateCreateDisabled = () -> {
            Node btn = dlg.getDialogPane().lookupButton(createBtn);
            if (!(btn instanceof Button b)) return;

            String q = taQuestion.getText() == null ? "" : taQuestion.getText().trim();
            int filled = 0;
            for (TextField tf : optionFields) {
                String v = tf.getText() == null ? "" : tf.getText().trim();
                if (!v.isEmpty()) filled++;
            }

            b.setDisable(q.isEmpty() || filled < 2);
        };

        Runnable addOptionRow = () -> {
            TextField tf = new TextField();
            tf.setPromptText("Choix (ex: Chill, Challenge, Photo-shoot…)"); // robust
            tf.getStyleClass().add("pollAddField");
            tf.getStyleClass().add("pollCreateOptionField");

            optionFields.add(tf);
            tf.textProperty().addListener((obs, ov, nv) -> updateCreateDisabled.run());

            Button rm = new Button("✕");
            rm.getStyleClass().add("pollActionBtnSecondary");
            rm.setOnAction(e -> {
                if (optionFields.size() <= 2) return;
                optionFields.remove(tf);
                optionsBox.getChildren().remove(rm.getParent());
                syncRemoveButtons.run();
                updateCreateDisabled.run();
            });

            HBox row = new HBox(8, tf, rm);
            row.getStyleClass().add("pollCreateOptionRow");
            HBox.setHgrow(tf, Priority.ALWAYS);
            optionsBox.getChildren().add(row);

            syncRemoveButtons.run();
            updateCreateDisabled.run();
        };

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

        taQuestion.textProperty().addListener((obs, ov, nv) -> updateCreateDisabled.run());
        updateCreateDisabled.run();

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
                        refreshTaskBadgeAsync();
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> showError("Sondage impossible : " + safe(ex.getMessage())));
                }
            }).start();
        });
    }

    // ─────────────────────────────────────────────────────────────────
    //  Board de tâches — stylé & dynamique
    // ─────────────────────────────────────────────────────────────────

    @FXML
    private void openTasksBoard() {
        if (!canWrite || currentUser == null || annonce == null) return;

        TaskBoard board = new TaskBoard();

        Dialog<Void> dlg = new Dialog<>();
        dlg.setTitle("Tâches — " + safe(annonce.getTitre()));
        dlg.getDialogPane().getStyleClass().add("taskDialog");
        dlg.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dlg.getDialogPane().setContent(board);
        dlg.getDialogPane().setPrefWidth(900);
        dlg.getDialogPane().setPrefHeight(600);

        try {
            var url = getClass().getResource("/styles/sorties/chat.css");
            if (url != null) dlg.getDialogPane().getStylesheets().add(url.toExternalForm());
        } catch (Exception ignored) {}

        dlg.setOnShown(e -> board.startAutoRefresh());
        dlg.setOnHidden(e -> board.stopAutoRefresh());

        dlg.show();
    }

    private void pushSystemMessageAsync(String content) {
        if (annonce == null || currentUser == null) return;
        String c = safe(content).trim();
        if (c.isEmpty()) return;

        new Thread(() -> {
            try {
                ChatMessage msg = chatService.sendSystem(annonce.getId(), currentUser.getId(), c);
                if (msg != null) {
                    Platform.runLater(() -> {
                        messagesBox.getChildren().add(buildBubble(msg));
                        lastMessageId = Math.max(lastMessageId, msg.getId());
                        scrollToBottom();
                    });
                }
            } catch (Exception ignored) {}
        }, "chat-system-msg").start();
    }

    private class TaskBoard extends VBox {

        private static final DataFormat TASK_ID_FORMAT = new DataFormat("app/task-id");

        private final Label title = new Label("🧩 Répartition des tâches");
        private final Label sub = new Label();
        private final ProgressBar progress = new ProgressBar(0);

        private final TextField tfTitle = new TextField();
        private final TextArea  taDesc  = new TextArea();
        private final ComboBox<TaskService.Assignee> cbAssign = new ComboBox<>();

        private final Button btnAdd = new Button("+ Ajouter");
        private final Button btnAuto = new Button("⚙ Auto-répartir");
        private final Button btnRefresh = new Button("⟳ Rafraîchir");

        private final VBox todoList  = new VBox(10);
        private final VBox doingList = new VBox(10);
        private final VBox doneList  = new VBox(10);

        private Timeline auto;

        private List<TaskService.Assignee> cachedAssignees = List.of();

        TaskBoard() {
            getStyleClass().add("taskBoardRoot");
            setSpacing(12);
            setPadding(new Insets(12));

            title.getStyleClass().add("taskBoardTitle");
            sub.getStyleClass().add("taskBoardSub");
            progress.getStyleClass().add("taskProgressBar");

            tfTitle.setPromptText("Titre de la tâche (ex: Préparer playlist, Ramener eau, Faire recap…)");
            tfTitle.getStyleClass().add("taskField");

            taDesc.setPromptText("Détails (optionnel)…");
            taDesc.setPrefRowCount(2);
            taDesc.setWrapText(true);
            taDesc.getStyleClass().add("taskField");

            cbAssign.getStyleClass().add("taskField");
            cbAssign.setPromptText("Assigner à…");

            btnAdd.getStyleClass().add("pollActionBtn");
            btnAuto.getStyleClass().add("pollActionBtnSecondary");
            btnRefresh.getStyleClass().add("pollActionBtnSecondary");

            btnAdd.setOnAction(e -> createTask());
            tfTitle.setOnAction(e -> createTask());
            btnAuto.setOnAction(e -> autoAssign());
            btnRefresh.setOnAction(e -> refreshAsync());

            HBox row1 = new HBox(10, tfTitle, cbAssign, btnAdd);
            row1.getStyleClass().add("taskCreateRow");
            HBox.setHgrow(tfTitle, Priority.ALWAYS);

            HBox row2 = new HBox(10, taDesc, new Region(), btnAuto, btnRefresh);
            row2.getStyleClass().add("taskCreateRow");
            HBox.setHgrow(taDesc, Priority.ALWAYS);
            HBox.setHgrow(row2.getChildren().get(1), Priority.ALWAYS);

            VBox header = new VBox(6, title, sub, progress, row1, row2);
            header.getStyleClass().add("taskBoardHeader");

            Node todoCol  = buildColumn("À faire", "TODO", todoList);
            Node doingCol = buildColumn("En cours", "DOING", doingList);
            Node doneCol  = buildColumn("Terminé", "DONE", doneList);

            HBox columns = new HBox(12, todoCol, doingCol, doneCol);
            columns.getStyleClass().add("taskColumns");
            HBox.setHgrow(todoCol, Priority.ALWAYS);
            HBox.setHgrow(doingCol, Priority.ALWAYS);
            HBox.setHgrow(doneCol, Priority.ALWAYS);

            getChildren().addAll(header, columns);
            refreshAsync();
        }

        private Node buildColumn(String label, String status, VBox list) {
            Label t = new Label(label);
            t.getStyleClass().add("taskColumnTitle");

            Label badge = new Label("0");
            badge.getStyleClass().add("taskCountBadge");
            badge.setUserData(status);

            HBox head = new HBox(8, t, badge);
            head.setAlignment(Pos.CENTER_LEFT);

            list.getStyleClass().add("taskColumnList");
            ScrollPane sp = new ScrollPane(list);
            sp.setFitToWidth(true);
            sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            sp.getStyleClass().add("taskColumnScroll");

            VBox col = new VBox(10, head, sp);
            col.getStyleClass().add("taskColumn");
            VBox.setVgrow(sp, Priority.ALWAYS);

            col.setUserData(status);

            // Drag & Drop: drop on column to change task status
            col.setOnDragOver(evt -> {
                Dragboard db = evt.getDragboard();
                if (db.hasContent(TASK_ID_FORMAT)) {
                    evt.acceptTransferModes(TransferMode.MOVE);
                }
                evt.consume();
            });
            col.setOnDragEntered(evt -> {
                Dragboard db = evt.getDragboard();
                if (db.hasContent(TASK_ID_FORMAT)) {
                    if (!col.getStyleClass().contains("taskDropTarget")) col.getStyleClass().add("taskDropTarget");
                }
            });
            col.setOnDragExited(evt -> col.getStyleClass().remove("taskDropTarget"));
            col.setOnDragDropped(evt -> {
                Dragboard db = evt.getDragboard();
                boolean success = false;
                if (db.hasContent(TASK_ID_FORMAT)) {
                    try {
                        int taskId = Integer.parseInt(String.valueOf(db.getContent(TASK_ID_FORMAT)));
                        success = true;
                        changeStatusById(taskId, status);
                    } catch (Exception ignored) {}
                }
                evt.setDropCompleted(success);
                evt.consume();
            });
            return col;
        }

        void startAutoRefresh() {
            stopAutoRefresh();
            auto = new Timeline(new KeyFrame(Duration.seconds(3), e -> refreshAsync()));
            auto.setCycleCount(Timeline.INDEFINITE);
            auto.play();
        }

        void stopAutoRefresh() {
            if (auto != null) auto.stop();
            auto = null;
        }

        private void refreshAsync() {
            if (annonce == null) return;
            new Thread(() -> {
                try {
                    List<TaskSnapshot> tasks = taskService.listTasks(annonce.getId());
                    List<TaskService.Assignee> assignees = taskService.listEligibleAssignees(annonce.getId());
                    TaskService.TaskStats stats = taskService.getStats(annonce.getId());

                    final long myOpen = (currentUser != null)
                            ? taskService.countMyOpenTasks(annonce.getId(), currentUser.getId())
                            : 0;

                    Platform.runLater(() -> {
                        renderAssignees(assignees);
                        renderTasks(tasks, stats, myOpen);
                    });
                } catch (Exception ignored) {}
            }, "tasks-refresh").start();
        }

        private void renderAssignees(List<TaskService.Assignee> assignees) {
            TaskService.Assignee none = new TaskService.Assignee(0, "Non assignée");

            cachedAssignees = (assignees == null) ? List.of() : new ArrayList<>(assignees);

            cbAssign.getItems().clear();
            cbAssign.getItems().add(none);
            if (assignees != null) cbAssign.getItems().addAll(assignees);
            cbAssign.getSelectionModel().selectFirst();
        }

        private void renderTasks(List<TaskSnapshot> tasks, TaskService.TaskStats stats, long myOpen) {
            todoList.getChildren().clear();
            doingList.getChildren().clear();
            doneList.getChildren().clear();

            int todo = 0, doing = 0, done = 0;

            if (tasks != null) {
                for (TaskSnapshot t : tasks) {
                    String st = safe(t.getStatus()).toUpperCase();
                    Node card = buildTaskCard(t);
                    switch (st) {
                        case "DOING" -> { doingList.getChildren().add(card); doing++; }
                        case "DONE"  -> { doneList.getChildren().add(card);  done++; }
                        default      -> { todoList.getChildren().add(card);  todo++; }
                    }
                }
            }

            setBadgeCount("TODO", todo);
            setBadgeCount("DOING", doing);
            setBadgeCount("DONE", done);

            int total = stats == null ? (todo + doing + done) : stats.total;
            int doneCount = stats == null ? done : stats.done;
            double ratio = (total <= 0) ? 0 : (double) doneCount / (double) total;
            progress.setProgress(ratio);

            String mineStr = (myOpen > 0) ? (" · Mes tâches ouvertes: " + myOpen) : "";
            sub.setText(doneCount + "/" + Math.max(total, 0) + " terminée(s)" + mineStr);

            refreshTaskBadgeAsync();
        }

        private void setBadgeCount(String status, int count) {
            if (getChildren().size() < 2) return;
            Node cols = getChildren().get(1);
            if (!(cols instanceof HBox h)) return;

            for (Node c : h.getChildren()) {
                if (c instanceof VBox col && status.equals(col.getUserData())) {
                    Node head = col.getChildren().get(0);
                    if (head instanceof HBox hb) {
                        for (Node x : hb.getChildren()) {
                            if (x instanceof Label lab && lab.getStyleClass().contains("taskCountBadge")) {
                                lab.setText(String.valueOf(count));
                            }
                        }
                    }
                }
            }
        }

        private Node buildTaskCard(TaskSnapshot t) {
            VBox card = new VBox(8);
            card.getStyleClass().add("taskCard");
            card.setUserData(t.getId());

            Label id = new Label("#" + t.getId());
            id.getStyleClass().add("taskIdBadge");

            Label title = new Label(safe(t.getTitle()));
            title.getStyleClass().add("taskCardTitle");
            title.setWrapText(true);

            Label who = new Label(buildAssigneeLine(t));
            who.getStyleClass().add("taskCardMeta");
            who.setWrapText(true);

            String st = safe(t.getStatus()).toUpperCase();
            Label pill = new Label(statusLabel(st));
            pill.getStyleClass().add("taskStatusPill");
            pill.getStyleClass().add("taskStatusPill_" + st);

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            HBox top = new HBox(8, id, title, spacer, pill);
            top.setAlignment(Pos.TOP_LEFT);

            boolean canManage = isAdmin() || isAnnonceOwner() || (currentUser != null && t.getCreatedBy() == currentUser.getId());
            boolean statusAllowed = currentUser != null && (t.getAssignedTo() == null || t.getAssignedTo() == currentUser.getId());
            boolean assignmentAllowed = canManage || (currentUser != null && (t.getAssignedTo() == null || t.getAssignedTo() == currentUser.getId()));

            ComboBox<TaskService.Assignee> cb = new ComboBox<>();
            cb.getStyleClass().add("taskAssignCombo");
            cb.setPrefWidth(160);

            TaskService.Assignee none = new TaskService.Assignee(0, "Non assignée");
            cb.getItems().add(none);
            if (canManage || !assignmentAllowed) {
                // admin/organisateur : liste complète
                // non autorisé : liste complète mais combo désactivé (pour afficher le bon nom)
                cb.getItems().addAll(cachedAssignees);
            } else {
                // utilisateur normal : peut seulement prendre/relâcher sa propre tâche
                TaskService.Assignee me = null;
                for (TaskService.Assignee a : cachedAssignees) {
                    if (currentUser != null && a.userId == currentUser.getId()) {
                        me = a;
                        break;
                    }
                }
                if (me == null && currentUser != null) me = new TaskService.Assignee(currentUser.getId(), "Moi");
                if (me != null) cb.getItems().add(me);
            }

            int current = t.getAssignedTo() == null ? 0 : t.getAssignedTo();
            for (TaskService.Assignee a : cb.getItems()) {
                if (a.userId == current) {
                    cb.getSelectionModel().select(a);
                    break;
                }
            }
            if (cb.getSelectionModel().getSelectedItem() == null) cb.getSelectionModel().selectFirst();

            cb.setDisable(!assignmentAllowed);

            cb.setOnAction(e -> {
                if (!assignmentAllowed) return;
                TaskService.Assignee a = cb.getValue();
                Integer uid = (a == null || a.userId <= 0) ? null : a.userId;
                new Thread(() -> {
                    try {
                        taskService.assignTask(t.getId(), uid, currentUser.getId(), canManage);
                        pushSystemMessageAsync("👤 Tâche #" + t.getId() + " assignée à " + (a == null ? "" : a.name));
                        refreshAsync();
                    } catch (Exception ex) {
                        Platform.runLater(() -> showError("Assignation impossible: " + safe(ex.getMessage())));
                    }
                }, "task-assign").start();
            });

            Button b1 = new Button();
            Button b2 = new Button();
            b1.getStyleClass().add("pollActionBtnSecondary");
            b2.getStyleClass().add("pollActionBtnSecondary");

            if ("DONE".equals(st)) {
                b1.setText("↺ Réouvrir");
                b2.setText("—");
                b2.setDisable(true);
                b2.setVisible(false);
                b2.setManaged(false);

                b1.setOnAction(e -> changeStatus(t, "TODO"));
            } else if ("DOING".equals(st)) {
                b1.setText("✓ Terminer");
                b2.setText("↺ TODO");
                b1.setOnAction(e -> changeStatus(t, "DONE"));
                b2.setOnAction(e -> changeStatus(t, "TODO"));
            } else {
                b1.setText("▶ En cours");
                b2.setText("✓ Terminer");
                b1.setOnAction(e -> changeStatus(t, "DOING"));
                b2.setOnAction(e -> changeStatus(t, "DONE"));
            }

            if (!statusAllowed) {
                b1.setDisable(true);
                b2.setDisable(true);
            }

            Button del = new Button("🗑");
            del.getStyleClass().add("pollActionBtnSecondary");
            del.setVisible(canManage);
            del.setManaged(canManage);
            del.setOnAction(e -> {
                Alert a = new Alert(Alert.AlertType.CONFIRMATION);
                a.setHeaderText("Supprimer la tâche ?");
                a.setContentText("Cette action est définitive.");
                a.showAndWait().ifPresent(bt -> {
                    if (bt == ButtonType.OK) {
                        new Thread(() -> {
                            try {
                                taskService.deleteTask(t.getId());
                                pushSystemMessageAsync("🗑 Tâche #" + t.getId() + " supprimée");
                                refreshAsync();
                            } catch (Exception ex) {
                                Platform.runLater(() -> showError("Suppression impossible: " + safe(ex.getMessage())));
                            }
                        }, "task-del").start();
                    }
                });
            });

            HBox actions = new HBox(8, cb, b1, b2, new Region(), del);
            actions.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(actions.getChildren().get(4), Priority.ALWAYS);

            card.getChildren().addAll(top, who);
            if (!safe(t.getDescription()).trim().isEmpty()) {
                Label d = new Label(safe(t.getDescription()));
                d.getStyleClass().add("taskCardDesc");
                d.setWrapText(true);
                card.getChildren().add(d);
            }
            card.getChildren().add(actions);

            card.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2) editTask(t);
            });

            // Drag start (move between columns)
            card.setOnDragDetected(e -> {
                if (currentUser == null) return;
                Dragboard db = card.startDragAndDrop(TransferMode.MOVE);
                ClipboardContent cc = new ClipboardContent();
                cc.put(TASK_ID_FORMAT, String.valueOf(t.getId()));
                db.setContent(cc);
                e.consume();
            });

            return card;
        }

        private void changeStatusById(int taskId, String newStatus) {
            new Thread(() -> {
                try {
                    if (currentUser == null) {
                        Platform.runLater(() -> showError("Vous devez être connecté."));
                        return;
                    }
                    taskService.setStatus(taskId, newStatus, currentUser.getId());
                    refreshAsync();
                } catch (Exception ex) {
                    Platform.runLater(() -> showError("Changement impossible: " + safe(ex.getMessage())));
                }
            }, "task-status-dnd").start();
        }

        private void editTask(TaskSnapshot t) {
            boolean canEdit = isAdmin() || isAnnonceOwner() || (currentUser != null && t.getCreatedBy() == currentUser.getId());
            if (!canEdit) return;

            Dialog<ButtonType> dlg = new Dialog<>();
            dlg.setTitle("Modifier tâche #" + t.getId());
            dlg.getDialogPane().getStyleClass().add("taskDialog");
            dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

            TextField tt = new TextField(safe(t.getTitle()));
            tt.getStyleClass().add("taskField");
            TextArea dd = new TextArea(safe(t.getDescription()));
            dd.setPrefRowCount(3);
            dd.setWrapText(true);
            dd.getStyleClass().add("taskField");

            VBox root = new VBox(10, new Label("Titre"), tt, new Label("Détails"), dd);
            root.setPadding(new Insets(10));
            dlg.getDialogPane().setContent(root);

            try {
                var url = getClass().getResource("/styles/sorties/chat.css");
                if (url != null) dlg.getDialogPane().getStylesheets().add(url.toExternalForm());
            } catch (Exception ignored) {}

            // ✅ ICI : bt est ButtonType (pas Void)
            dlg.showAndWait().ifPresent(bt -> {
                if (bt != ButtonType.OK) return;
                new Thread(() -> {
                    try {
                        taskService.updateTask(t.getId(), tt.getText(), dd.getText());
                        pushSystemMessageAsync("✏ Tâche #" + t.getId() + " modifiée");
                        refreshAsync();
                    } catch (Exception ex) {
                        Platform.runLater(() -> showError("Modification impossible: " + safe(ex.getMessage())));
                    }
                }, "task-edit").start();
            });
        }

        private void changeStatus(TaskSnapshot t, String newStatus) {
            new Thread(() -> {
                try {
                    if (currentUser == null) {
                        Platform.runLater(() -> showError("Vous devez être connecté."));
                        return;
                    }
                    taskService.setStatus(t.getId(), newStatus, currentUser.getId());
                    if ("DONE".equalsIgnoreCase(newStatus)) {
                        pushSystemMessageAsync("✅ Tâche #" + t.getId() + " terminée : " + safe(t.getTitle()));
                    } else if ("DOING".equalsIgnoreCase(newStatus)) {
                        pushSystemMessageAsync("▶ Tâche #" + t.getId() + " en cours : " + safe(t.getTitle()));
                    } else {
                        pushSystemMessageAsync("↺ Tâche #" + t.getId() + " repassée en TODO : " + safe(t.getTitle()));
                    }
                    refreshAsync();
                } catch (Exception ex) {
                    Platform.runLater(() -> showError("Changement impossible: " + safe(ex.getMessage())));
                }
            }, "task-status").start();
        }

        private void createTask() {
            String title = safe(tfTitle.getText()).trim();
            if (title.isEmpty()) return;

            TaskService.Assignee a = cbAssign.getValue();
            Integer uid = (a == null || a.userId <= 0) ? null : a.userId;
            String desc = safe(taDesc.getText()).trim();

            btnAdd.setDisable(true);
            new Thread(() -> {
                try {
                    int id = taskService.createTask(annonce.getId(), currentUser.getId(), title, desc, uid);
                    pushSystemMessageAsync("🧩 Nouvelle tâche #" + id + " : " + title);

                    Platform.runLater(() -> {
                        tfTitle.clear();
                        taDesc.clear();
                        cbAssign.getSelectionModel().selectFirst();
                        btnAdd.setDisable(false);
                    });

                    refreshAsync();
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        btnAdd.setDisable(false);
                        showError("Création impossible: " + safe(ex.getMessage()));
                    });
                }
            }, "task-create").start();
        }

        private void autoAssign() {
            boolean canManage = isAdmin() || isAnnonceOwner();
            if (!canManage) {
                showError("Seul l'organisateur (ou admin) peut auto-répartir.");
                return;
            }

            btnAuto.setDisable(true);
            new Thread(() -> {
                try {
                    int changed = taskService.autoAssignBalanced(annonce.getId());
                    if (changed > 0) pushSystemMessageAsync("⚙ Auto-répartition effectuée (" + changed + " tâche(s) assignée(s))");
                    Platform.runLater(() -> btnAuto.setDisable(false));
                    refreshAsync();
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        btnAuto.setDisable(false);
                        showError("Auto-répartition impossible: " + safe(ex.getMessage()));
                    });
                }
            }, "task-auto").start();
        }

        private String buildAssigneeLine(TaskSnapshot t) {
            String assigned = safe(t.getAssignedToName()).trim();
            if (assigned.isEmpty()) assigned = "Non assignée";
            String by = safe(t.getCreatedByName()).trim();
            if (by.isEmpty()) by = "Utilisateur #" + t.getCreatedBy();
            return "Assignée à: " + assigned + " · Créée par: " + by;
        }

        private String statusLabel(String status) {
            return switch (safe(status).toUpperCase()) {
                case "DOING" -> "EN COURS";
                case "DONE" -> "TERMINÉ";
                default -> "À FAIRE";
            };
        }
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

        private boolean selectionDirty = false;
        private boolean applyingSnapshot = false;

        private boolean firstPaint = true;
        private final Map<Integer, Double> lastPercent = new HashMap<>();

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

            setOnMouseEntered(e -> {
                if (!getStyleClass().contains("pollHover")) getStyleClass().add("pollHover");
            });
            setOnMouseExited(e -> getStyleClass().remove("pollHover"));
        }

        void playAppear() {
            // Only once per instance.
            if (!firstPaint) return;
            microAppear(this);
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

            if (pinned) {
                if (!getStyleClass().contains("pollCardPinned")) getStyleClass().add("pollCardPinned");
            } else {
                getStyleClass().remove("pollCardPinned");
            }
            if (!open) {
                if (!getStyleClass().contains("pollCardClosed")) getStyleClass().add("pollCardClosed");
            } else {
                getStyleClass().remove("pollCardClosed");
            }

            if (selectionDirty) {
                if (!getStyleClass().contains("pollCardDirty")) getStyleClass().add("pollCardDirty");
            } else {
                getStyleClass().remove("pollCardDirty");
            }

            btnPin.setText(pinned ? "📌 Désépingler" : "📌 Épingler");

            canManage = isAdmin() || isAnnonceOwner() || (currentUser != null && s.getCreatedBy() == currentUser.getId());
            btnClose.setVisible(canManage);
            btnClose.setManaged(canManage);
            btnPin.setVisible(canManage);
            btnPin.setManaged(canManage);

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

                    double oldPct = lastPercent.getOrDefault(opt.getId(), firstPaint ? 0.0 : opt.getPercent());
                    optionsBox.getChildren().add(buildOptionRow(opt.getId(), opt.getText(), opt.getVotes(), opt.getPercent(), oldPct, selected));
                    lastPercent.put(opt.getId(), opt.getPercent());
                }
            } finally {
                applyingSnapshot = false;
            }

            boolean canAdd = open && canWrite && (allowAddOptions || canManage);
            tfAdd.setVisible(canAdd);
            tfAdd.setManaged(canAdd);
            btnAdd.setVisible(canAdd);
            btnAdd.setManaged(canAdd);

            btnVote.setDisable(!open || !canWrite);
            btnClear.setDisable(!open || !canWrite);
            btnClear.setVisible(!s.getMyOptionIds().isEmpty());
            btnClear.setManaged(!s.getMyOptionIds().isEmpty());
            btnVote.setText(s.getMyOptionIds().isEmpty() ? "Voter" : "Mettre à jour");

            // Disable vote when nothing selected.
            updateVoteDisabled();

            firstPaint = false;
        }

        private Node buildOptionRow(int optionId, String text, int votes, double pct, double oldPct, boolean selected) {
            Label txt = new Label(safe(text));
            txt.getStyleClass().add("pollOptionText");
            txt.setWrapText(true);

            double boundedNew = Math.max(0, Math.min(1, pct));
            double boundedOld = Math.max(0, Math.min(1, oldPct));

            ProgressBar bar = new ProgressBar(boundedOld);
            bar.setPrefWidth(260);
            bar.getStyleClass().add("pollOptionBar");

            // animate bar to new value (only if meaningful delta)
            double delta = Math.abs(boundedNew - boundedOld);
            if (delta >= 0.03) {
                Timeline tl = new Timeline(
                        new KeyFrame(Duration.millis(220), new KeyValue(bar.progressProperty(), boundedNew))
                );
                tl.play();
            } else {
                bar.setProgress(boundedNew);
            }

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

            row.setOnMouseClicked(e -> {
                if (!open || !canWrite) return;
                if (isTargetInside(selector, e.getTarget())) return;
                if (selector instanceof CheckBox cb) cb.setSelected(!cb.isSelected());
                else if (selector instanceof RadioButton rb) rb.setSelected(true);
            });

            if (selector instanceof CheckBox cb) {
                cb.selectedProperty().addListener((obs, was, is) -> {
                    if (!applyingSnapshot && was != is) selectionDirty = true;
                    if (is) {
                        if (!row.getStyleClass().contains("selected")) row.getStyleClass().add("selected");
                        microPulse(row);
                    } else {
                        row.getStyleClass().remove("selected");
                    }
                    updateVoteDisabled();
                });
            } else if (selector instanceof RadioButton rb) {
                rb.selectedProperty().addListener((obs, was, is) -> {
                    if (!applyingSnapshot && was != is) selectionDirty = true;
                    if (is) {
                        if (!row.getStyleClass().contains("selected")) row.getStyleClass().add("selected");
                        microPulse(row);
                    } else {
                        row.getStyleClass().remove("selected");
                    }
                    updateVoteDisabled();
                });
            }

            return row;
        }

        private void updateVoteDisabled() {
            if (!open || !canWrite) {
                btnVote.setDisable(true);
                return;
            }

            boolean any = false;
            if (allowMulti) {
                for (var e : multiBoxes.entrySet()) {
                    if (e.getValue().isSelected()) { any = true; break; }
                }
            } else {
                for (Node n : optionsBox.getChildren()) {
                    if (n instanceof HBox h) {
                        Node first = h.getChildren().isEmpty() ? null : h.getChildren().get(0);
                        if (first instanceof RadioButton rb && rb.isSelected()) { any = true; break; }
                    }
                }
            }

            btnVote.setDisable(!any);
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
                        if (first instanceof RadioButton rb && rb.isSelected()) selected.add(oid);
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
            } else {
                for (Node n : optionsBox.getChildren()) {
                    if (n instanceof HBox h) {
                        Object ud = h.getUserData();
                        if (ud instanceof Integer oid) {
                            Node first = h.getChildren().get(0);
                            if (first instanceof RadioButton rb && rb.isSelected()) selected.add(oid);
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
                        microPulse(this);
                        refreshPinnedAsync();
                        refreshTaskBadgeAsync();
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
                        refreshTaskBadgeAsync();
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
                        refreshTaskBadgeAsync();
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> showError("Clôture impossible : " + safe(ex.getMessage())));
                }
            }).start();
        }
    }
}
