package utils.ui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class AutoCompleteComboBox {

    private AutoCompleteComboBox() {}

    public static void install(ComboBox<String> combo) {
        combo.setEditable(true);
        combo.setVisibleRowCount(10);

        TextField editor = combo.getEditor();
        List<String> original = new ArrayList<>(combo.getItems());

        editor.textProperty().addListener((obs, old, text) -> {
            if (!combo.isFocused()) return;

            String q = (text == null) ? "" : text.trim().toLowerCase();
            if (q.isEmpty()) {
                combo.setItems(FXCollections.observableArrayList(original));
                combo.hide();
                combo.show();
                return;
            }

            List<String> filtered = original.stream()
                    .filter(x -> x != null && x.toLowerCase().contains(q))
                    .collect(Collectors.toList());

            combo.setItems(FXCollections.observableArrayList(filtered));
            combo.hide();
            combo.show();
        });

        combo.focusedProperty().addListener((obs, was, is) -> {
            if (!is) {
                // restore items when leaving
                combo.setItems(FXCollections.observableArrayList(original));
            }
        });

        // Store back original when a selection is done
        combo.valueProperty().addListener((obs, o, n) -> {
            if (n != null) editor.setText(n);
        });

        // Expose a small hook by storing original list as userData
        combo.setUserData(original);
    }

    @SuppressWarnings("unchecked")
    public static void refreshOriginalItems(ComboBox<String> combo) {
        Object ud = combo.getUserData();
        if (ud instanceof List<?>) {
            ((List<String>) ud).clear();
            ((List<String>) ud).addAll(combo.getItems());
        } else {
            combo.setUserData(new ArrayList<>(combo.getItems()));
        }
    }
}