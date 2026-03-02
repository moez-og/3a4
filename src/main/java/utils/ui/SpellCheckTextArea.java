package utils.ui;

import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Side;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.StackPane;
import org.fxmisc.richtext.StyleClassedTextArea;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;
import services.language.LanguageToolService;
import services.language.LanguageToolService.GrammarMatch;

import java.util.*;

/**
 * Zone de texte avec correction orthographique / grammaticale en temps réel.
 * <p>
 * Fonctionne comme Google Docs / Word :
 * <ul>
 *   <li>Mots incorrects soulignés en rouge (wavy underline)</li>
 *   <li>Clic droit sur un mot souligné → menu contextuel avec suggestions</li>
 *   <li>Détection en temps réel (debounce 900ms)</li>
 *   <li>Non bloquant (appels API en arrière-plan)</li>
 * </ul>
 * <p>
 * Usage :
 * <pre>
 *   SpellCheckTextArea sc = new SpellCheckTextArea();
 *   sc.setText("Cafee Panoramaa");
 *   someVBox.getChildren().add(sc.getNode());
 *   String text = sc.getText();
 * </pre>
 */
public class SpellCheckTextArea {

    private final StyleClassedTextArea textArea;
    private final StackPane wrapper;
    private final ContextMenu contextMenu;

    // Timer debounce
    private Timer spellTimer;
    private static final long DEBOUNCE_MS = 900;

    // Dernières corrections détectées
    private volatile List<GrammarMatch> lastMatches = new ArrayList<>();

    // État : éviter les boucles infinies quand on applique une correction
    private boolean isApplyingCorrection = false;

    public SpellCheckTextArea() {
        textArea = new StyleClassedTextArea();
        textArea.setWrapText(true);
        textArea.getStyleClass().add("spell-check-area");

        // Wrapper pour dimensionner correctement dans un layout
        wrapper = new StackPane(textArea);
        wrapper.getStyleClass().add("spell-check-wrapper");
        wrapper.setPrefHeight(120);
        wrapper.setMinHeight(80);

        contextMenu = new ContextMenu();
        contextMenu.getStyleClass().add("spell-context-menu");

        // ── Détection en temps réel (debounced) ──────────────────
        textArea.textProperty().addListener((obs, oldVal, newVal) -> {
            if (isApplyingCorrection) return;
            scheduleSpellCheck();
        });

        // ── Clic droit → menu contextuel ─────────────────────────
        textArea.setOnContextMenuRequested(event -> {
            event.consume();
            int charIdx = textArea.hit(event.getX(), event.getY()).getCharacterIndex().orElse(-1);
            showContextMenu(charIdx, event.getScreenX(), event.getScreenY());
        });

        // Fermer le menu quand on clique ailleurs
        textArea.setOnMousePressed(event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                contextMenu.hide();
            }
        });
    }

    // ══════════════════════════════════════════════════════════════
    //  API publique
    // ══════════════════════════════════════════════════════════════

    /** Retourne le nœud à ajouter à la scène (StackPane contenant le text area). */
    public StackPane getNode() {
        return wrapper;
    }

    /** Retourne le texte brut. */
    public String getText() {
        return textArea.getText();
    }

    /** Définit le texte (remplace tout). */
    public void setText(String text) {
        textArea.replaceText(text != null ? text : "");
    }

    /** Définit le texte indicatif (placeholder simulé). */
    public void setPromptText(String prompt) {
        // StyleClassedTextArea n'a pas de promptText natif.
        // On simule avec un overlay label.
        javafx.scene.control.Label lbl = new javafx.scene.control.Label(prompt);
        lbl.getStyleClass().add("spell-check-prompt");
        lbl.setMouseTransparent(true);

        // Afficher/masquer selon le contenu
        lbl.visibleProperty().bind(
            javafx.beans.binding.Bindings.createBooleanBinding(
                () -> textArea.getText().isEmpty(),
                textArea.textProperty()
            )
        );

        wrapper.getChildren().add(lbl);
        StackPane.setAlignment(lbl, javafx.geometry.Pos.TOP_LEFT);
    }

    /** Donne le focus au text area. */
    public void requestFocus() {
        textArea.requestFocus();
    }

    /** Positionne le curseur. */
    public void positionCaret(int pos) {
        textArea.moveTo(Math.min(pos, textArea.getLength()));
    }

    /** Retourne le StyleClassedTextArea interne (pour binding avancé). */
    public StyleClassedTextArea getInternalArea() {
        return textArea;
    }

    /** Observe les changements de texte. */
    public void addTextListener(javafx.beans.value.ChangeListener<String> listener) {
        textArea.textProperty().addListener(listener);
    }

    /** Définit la hauteur préférée. */
    public void setPrefHeight(double h) {
        wrapper.setPrefHeight(h);
    }

    /** Ajoute une feuille de style CSS. */
    public void addStylesheet(String url) {
        textArea.getStylesheets().add(url);
    }

    // ══════════════════════════════════════════════════════════════
    //  Spell check logic
    // ══════════════════════════════════════════════════════════════

    private void scheduleSpellCheck() {
        if (spellTimer != null) {
            spellTimer.cancel();
            spellTimer = null;
        }

        String text = textArea.getText();
        if (text == null || text.trim().length() < 3) {
            clearHighlights();
            lastMatches = new ArrayList<>();
            return;
        }

        spellTimer = new Timer(true);
        spellTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    String currentText = textArea.getText();
                    if (currentText == null || currentText.trim().length() < 3) {
                        Platform.runLater(() -> {
                            clearHighlights();
                            lastMatches = new ArrayList<>();
                        });
                        return;
                    }

                    List<GrammarMatch> matches = LanguageToolService.check(currentText);
                    lastMatches = matches;

                    Platform.runLater(() -> {
                        try {
                            applyHighlights(currentText, matches);
                        } catch (Exception ignored) {
                            // Le texte a changé entre-temps
                        }
                    });
                } catch (Exception e) {
                    System.err.println("[SpellCheck] Erreur: " + e.getMessage());
                }
            }
        }, DEBOUNCE_MS);
    }

    private void applyHighlights(String text, List<GrammarMatch> matches) {
        if (text == null || text.isEmpty()) return;
        // Vérifier que le texte n'a pas changé
        if (!text.equals(textArea.getText())) return;

        StyleSpansBuilder<Collection<String>> builder = new StyleSpansBuilder<>();

        int lastEnd = 0;
        // Trier par offset
        List<GrammarMatch> sorted = new ArrayList<>(matches);
        sorted.sort(Comparator.comparingInt(m -> m.offset));

        for (GrammarMatch m : sorted) {
            if (m.offset < lastEnd) continue; // chevauchement
            if (m.offset + m.length > text.length()) continue; // dépassement

            // Texte normal avant l'erreur
            if (m.offset > lastEnd) {
                builder.add(Collections.emptyList(), m.offset - lastEnd);
            }

            // Texte erroné
            builder.add(Collections.singleton("spell-error"), m.length);
            lastEnd = m.offset + m.length;
        }

        // Texte restant
        if (lastEnd < text.length()) {
            builder.add(Collections.emptyList(), text.length() - lastEnd);
        }

        try {
            StyleSpans<Collection<String>> spans = builder.create();
            textArea.setStyleSpans(0, spans);
        } catch (Exception ignored) {
            // Peut échouer si le texte a changé entre-temps
        }
    }

    private void clearHighlights() {
        try {
            if (textArea.getLength() > 0) {
                textArea.clearStyle(0, textArea.getLength());
            }
        } catch (Exception ignored) {}
    }

    // ══════════════════════════════════════════════════════════════
    //  Context menu (clic droit)
    // ══════════════════════════════════════════════════════════════

    private void showContextMenu(int charIndex, double screenX, double screenY) {
        contextMenu.getItems().clear();

        if (charIndex < 0) {
            showDefaultMenu(screenX, screenY);
            return;
        }

        // Trouver le match qui contient ce charIndex
        GrammarMatch foundMatch = null;
        for (GrammarMatch m : lastMatches) {
            if (charIndex >= m.offset && charIndex < m.offset + m.length) {
                foundMatch = m;
                break;
            }
        }

        if (foundMatch == null) {
            showDefaultMenu(screenX, screenY);
            return;
        }

        final GrammarMatch match = foundMatch;
        String errWord = "";
        String text = textArea.getText();
        if (text != null && match.offset + match.length <= text.length()) {
            errWord = text.substring(match.offset, match.offset + match.length);
        }

        // ── En-tête : mot erroné ──
        MenuItem header = new MenuItem("✏️  \"" + errWord + "\"");
        header.setDisable(true);
        header.getStyleClass().add("spell-menu-header");
        contextMenu.getItems().add(header);

        // ── Message d'explication ──
        if (match.message != null && !match.message.isEmpty()) {
            String msgShort = match.message.length() > 60
                    ? match.message.substring(0, 57) + "…"
                    : match.message;
            MenuItem msgItem = new MenuItem("ℹ️  " + msgShort);
            msgItem.setDisable(true);
            msgItem.getStyleClass().add("spell-menu-info");
            contextMenu.getItems().add(msgItem);
        }

        contextMenu.getItems().add(new SeparatorMenuItem());

        // ── Suggestions ──
        if (match.replacements.isEmpty()) {
            MenuItem noSugg = new MenuItem("  Aucune suggestion disponible");
            noSugg.setDisable(true);
            noSugg.getStyleClass().add("spell-menu-empty");
            contextMenu.getItems().add(noSugg);
        } else {
            int maxSuggestions = Math.min(match.replacements.size(), 5);
            for (int i = 0; i < maxSuggestions; i++) {
                String replacement = match.replacements.get(i);
                MenuItem item = new MenuItem("  ✓  " + replacement);
                item.getStyleClass().add("spell-menu-suggestion");

                final int offset = match.offset;
                final int length = match.length;
                item.setOnAction(e -> applyCorrection(offset, length, replacement));

                contextMenu.getItems().add(item);
            }
        }

        contextMenu.getItems().add(new SeparatorMenuItem());

        // ── Ignorer ──
        MenuItem ignoreItem = new MenuItem("  Ignorer");
        ignoreItem.getStyleClass().add("spell-menu-ignore");
        ignoreItem.setOnAction(e -> {
            lastMatches.remove(match);
            Platform.runLater(() -> applyHighlights(textArea.getText(), lastMatches));
        });
        contextMenu.getItems().add(ignoreItem);

        // ── Tout corriger ──
        MenuItem fixAllItem = new MenuItem("  ✨ Tout corriger");
        fixAllItem.getStyleClass().add("spell-menu-fixall");
        fixAllItem.setOnAction(e -> applyAllCorrections());
        contextMenu.getItems().add(fixAllItem);

        contextMenu.show(textArea, screenX, screenY);
    }

    private void showDefaultMenu(double screenX, double screenY) {
        MenuItem noErr = new MenuItem("✅  Aucune faute ici");
        noErr.setDisable(true);
        contextMenu.getItems().add(noErr);
        contextMenu.show(textArea, screenX, screenY);
    }

    private void applyCorrection(int offset, int length, String replacement) {
        isApplyingCorrection = true;
        try {
            String text = textArea.getText();
            if (text != null && offset + length <= text.length()) {
                textArea.replaceText(offset, offset + length, replacement);

                // Recalculer les offsets des matches restants
                int delta = replacement.length() - length;
                List<GrammarMatch> updated = new ArrayList<>();
                for (GrammarMatch m : lastMatches) {
                    if (m.offset == offset && m.length == length) continue; // retirer le corrigé
                    if (m.offset > offset) {
                        updated.add(new GrammarMatch(
                                m.offset + delta, m.length,
                                m.message, m.shortMessage, m.ruleId, m.replacements
                        ));
                    } else {
                        updated.add(m);
                    }
                }
                lastMatches = updated;
                Platform.runLater(() -> applyHighlights(textArea.getText(), lastMatches));
            }
        } finally {
            isApplyingCorrection = false;
            // Re-déclencher un spell check complet
            scheduleSpellCheck();
        }
    }

    private void applyAllCorrections() {
        if (lastMatches.isEmpty()) return;

        isApplyingCorrection = true;
        try {
            String text = textArea.getText();
            String corrected = LanguageToolService.autoCorrect(text, lastMatches);
            textArea.replaceText(corrected);
            lastMatches = new ArrayList<>();
            clearHighlights();
        } finally {
            isApplyingCorrection = false;
            scheduleSpellCheck();
        }
    }
}
