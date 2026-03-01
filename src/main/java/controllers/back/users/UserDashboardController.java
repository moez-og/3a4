package controllers.back.users;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import models.users.User;
import services.users.UserService;
import utils.PasswordUtil;

import java.net.URL;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public class UserDashboardController {

    private static final int MIN_PASSWORD_LENGTH = 6;
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    private static final String DIALOG_INVALID_STYLE = " -fx-border-color: #e74c3c; -fx-border-width: 2; -fx-background-color: #fff5f5;";

    @FXML private TextField searchField;
    @FXML private Label totalUsersLabel;
    @FXML private Label activeUsersLabel; // ici = nb admins
    @FXML private Label adminsLabel;      // ici = nb visiteurs
    @FXML private HBox usersRow;
    @FXML private Pagination usersPagination;

    @FXML private Button refreshBtn;
    @FXML private Button addBtn;
    @FXML private Button editBtn;
    @FXML private Button deleteBtn;

    private UserService userService;
    private ObservableList<User> usersList = FXCollections.observableArrayList();
    private ObservableList<User> filteredUsersList = FXCollections.observableArrayList();

    private final int pageSize = 3;

    private User selectedUser;
    private Parent selectedCard;

    @FXML
    public void initialize() {
        userService = new UserService();

        usersPagination.currentPageIndexProperty().addListener((obs, oldVal, newVal) -> updateListViewPage(newVal.intValue()));
        searchField.textProperty().addListener((obs, o, n) -> filterUsers(n));

        // Tant qu’aucun user n’est sélectionné
        if (editBtn != null) editBtn.setDisable(true);
        if (deleteBtn != null) deleteBtn.setDisable(true);

        loadUsers();
    }

    @FXML
    public void loadUsers() {
        new Thread(() -> {
            try {
                List<User> users = userService.obtenirTous();
                ObservableList<User> all = FXCollections.observableArrayList(users);

                long nbAdmins = users.stream().filter(u -> "admin".equalsIgnoreCase(u.getRole())).count();
                long nbVisiteurs = users.stream().filter(u -> "visiteur".equalsIgnoreCase(u.getRole())).count();

                Platform.runLater(() -> {
                    usersList.setAll(all);
                    filteredUsersList.setAll(all);

                    totalUsersLabel.setText(String.valueOf(users.size()));
                    activeUsersLabel.setText(String.valueOf(nbAdmins));
                    adminsLabel.setText(String.valueOf(nbVisiteurs));

                    selectedUser = null;
                    selectedCard = null;
                    if (editBtn != null) editBtn.setDisable(true);
                    if (deleteBtn != null) deleteBtn.setDisable(true);

                    updatePagination();
                    updateListViewPage(0);
                });
            } catch (SQLException e) {
                showError("Erreur", "Impossible de charger les utilisateurs:\n" + e.getMessage());
            }
        }).start();
    }

    private void filterUsers(String query) {
        if (query == null || query.trim().isEmpty()) {
            filteredUsersList.setAll(usersList);
            updatePagination();
            updateListViewPage(0);
            return;
        }

        String q = query.toLowerCase().trim();
        ObservableList<User> out = FXCollections.observableArrayList();

        for (User u : usersList) {
            boolean match =
                    (u.getNom() != null && u.getNom().toLowerCase().contains(q)) ||
                            (u.getPrenom() != null && u.getPrenom().toLowerCase().contains(q)) ||
                            (u.getEmail() != null && u.getEmail().toLowerCase().contains(q)) ||
                            (u.getRole() != null && u.getRole().toLowerCase().contains(q)) ||
                            (u.getTelephone() != null && u.getTelephone().toLowerCase().contains(q));

            if (match) out.add(u);
        }

        filteredUsersList.setAll(out);
        updatePagination();
        updateListViewPage(0);
    }

    private void updatePagination() {
        int totalItems = filteredUsersList.size();
        int pageCount = (int) Math.ceil((double) totalItems / pageSize);
        usersPagination.setPageCount(Math.max(pageCount, 1));
        usersPagination.setCurrentPageIndex(0);
    }

    private void updateListViewPage(int pageIndex) {
        usersRow.getChildren().clear();

        if (filteredUsersList.isEmpty()) {
            return;
        }

        int fromIndex = pageIndex * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, filteredUsersList.size());
        if (fromIndex >= filteredUsersList.size()) return;

        selectedUser = null;
        selectedCard = null;
        if (editBtn != null) editBtn.setDisable(true);
        if (deleteBtn != null) deleteBtn.setDisable(true);

        for (User user : filteredUsersList.subList(fromIndex, toIndex)) {
            Parent card = buildUserCard(user);
            usersRow.getChildren().add(card);
        }
    }

    private Parent buildUserCard(User user) {
        try {
            URL fxmlUrl = getClass().getResource("/fxml/back/users/UserCard.fxml");
            if (fxmlUrl == null) {
                throw new IllegalStateException("FXML introuvable: /fxml/back/users/UserCard.fxml");
            }

            FXMLLoader loader = new FXMLLoader(fxmlUrl);
            Parent root = loader.load();

            UserCardController controller = loader.getController();
            controller.setUser(user);

            root.setOnMouseClicked(e -> selectUserCard(user, root));
            return root;

        } catch (Exception e) {
            Label fallback = new Label((user.getPrenom() != null ? user.getPrenom() : "") + " " + (user.getNom() != null ? user.getNom() : ""));
            fallback.setStyle("-fx-padding: 12; -fx-background-color: white; -fx-background-radius: 10; -fx-border-color: #e1e6ef; -fx-border-radius: 10;");
            return fallback;
        }
    }

    private void selectUserCard(User user, Parent card) {
        if (selectedCard != null) {
            selectedCard.setStyle("-fx-background-color: #ffffff; -fx-background-radius: 16; -fx-border-color: #e1e6ef; -fx-border-radius: 16;");
        }
        selectedUser = user;
        selectedCard = card;

        card.setStyle("-fx-background-color: #f8fbff; -fx-background-radius: 16; -fx-border-color: #4a6fa5; -fx-border-radius: 16; -fx-border-width: 2;");

        if (editBtn != null) editBtn.setDisable(false);
        if (deleteBtn != null) deleteBtn.setDisable(false);
    }

    // =======================
    //  Boutons (FXML)
    // =======================

    @FXML
    private void showAddUserDialog() {
        showUserDialog(null);
    }

    @FXML
    private void showEditUserDialog() {
        if (selectedUser == null) {
            showInfo("Info", "Sélectionner un utilisateur à modifier.");
            return;
        }
        showUserDialog(selectedUser);
    }

    @FXML
    private void deleteUser() {
        if (selectedUser == null) {
            showInfo("Info", "Sélectionner un utilisateur à supprimer.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Suppression");
        confirm.setHeaderText(null);
        confirm.setContentText("Supprimer " + safe(selectedUser.getPrenom()) + " " + safe(selectedUser.getNom()) + " ?");

        Optional<ButtonType> res = confirm.showAndWait();
        if (res.isEmpty() || res.get() != ButtonType.OK) return;

        try {
            userService.supprimer(selectedUser.getId());
            showInfo("Succès", "Utilisateur supprimé.");
            loadUsers();
        } catch (SQLException e) {
            showError("Erreur", "Suppression impossible:\n" + e.getMessage());
        }
    }

    // =======================
    //  Dialog Add / Edit
    // =======================

    private void showUserDialog(User toEdit) {
        boolean isEdit = (toEdit != null);

        Stage dialog = new Stage();
        dialog.setTitle(isEdit ? "Modifier un utilisateur" : "Ajouter un utilisateur");
        dialog.setWidth(520);
        dialog.setHeight(640);

        VBox root = new VBox(14);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: #f5f5f5;");

        Label title = new Label(isEdit ? "Modifier l’utilisateur" : "Créer un nouvel utilisateur");
        title.setStyle("-fx-font-size: 16; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        TextField nomField = createStyledTextField("Nom");
        TextField prenomField = createStyledTextField("Prénom");
        TextField emailField = createStyledTextField("Email");
        PasswordField passwordField = createStyledPasswordField(isEdit ? "Nouveau mot de passe (optionnel)" : "Mot de passe");
        ComboBox<String> roleCombo = createRoleCombo();
        TextField telField = createStyledTextField("Téléphone (optionnel)");
        TextField imageUrlField = createStyledTextField("Image URL (optionnel)");

        if (isEdit) {
            nomField.setText(safe(toEdit.getNom()));
            prenomField.setText(safe(toEdit.getPrenom()));
            emailField.setText(safe(toEdit.getEmail()));
            roleCombo.setValue(toEdit.getRole() != null ? toEdit.getRole() : "abonne");
            telField.setText(toEdit.getTelephone() != null ? toEdit.getTelephone() : "");
            imageUrlField.setText(toEdit.getImageUrl() != null ? toEdit.getImageUrl() : "");
        }

        attachClearOnChange(nomField);
        attachClearOnChange(prenomField);
        attachClearOnChange(emailField);
        attachClearOnChange(passwordField);
        attachClearOnChange(telField);
        attachClearOnChange(imageUrlField);

        Button save = createActionButton(isEdit ? "Enregistrer" : "Ajouter", "#1e3a5f");
        Button cancel = createActionButton("Annuler", "#7f8c8d");
        cancel.setOnAction(e -> dialog.close());

        save.setOnAction(e -> {
            clearInvalid(nomField); clearInvalid(prenomField); clearInvalid(emailField);
            clearInvalid(passwordField); clearInvalid(telField); clearInvalid(imageUrlField);

            String nom = nomField.getText().trim();
            String prenom = prenomField.getText().trim();
            String email = emailField.getText().trim();
            String pwd = passwordField.getText();
            String role = roleCombo.getValue();
            String tel = telField.getText().trim();
            String img = imageUrlField.getText().trim();

            boolean hasError = false;
            if (nom.isEmpty()) { markInvalid(nomField, "Nom obligatoire"); hasError = true; }
            if (prenom.isEmpty()) { markInvalid(prenomField, "Prénom obligatoire"); hasError = true; }
            if (email.isEmpty()) { markInvalid(emailField, "Email obligatoire"); hasError = true; }
            if (!isEdit && pwd.isEmpty()) { markInvalid(passwordField, "Mot de passe obligatoire"); hasError = true; }
            if (role == null || role.isEmpty()) { showError("Validation", "Sélectionner un rôle."); hasError = true; }

            if (hasError) return;

            if (!EMAIL_PATTERN.matcher(email).matches()) {
                markInvalid(emailField, "Email invalide");
                showError("Validation", "Email invalide.");
                return;
            }

            if (!pwd.isEmpty() && pwd.length() < MIN_PASSWORD_LENGTH) {
                markInvalid(passwordField, "Au moins " + MIN_PASSWORD_LENGTH + " caractères");
                showError("Validation", "Mot de passe trop court.");
                return;
            }

            try {
                // Unicité email (seulement si changement en edit)
                if (isEdit) {
                    String oldEmail = toEdit.getEmail() != null ? toEdit.getEmail().trim() : "";
                    if (!email.equalsIgnoreCase(oldEmail) && userService.emailExiste(email)) {
                        markInvalid(emailField, "Cet email est déjà utilisé");
                        showError("Validation", "Cet email est déjà utilisé.");
                        return;
                    }
                } else {
                    if (userService.emailExiste(email)) {
                        markInvalid(emailField, "Cet email est déjà utilisé");
                        showError("Validation", "Cet email est déjà utilisé.");
                        return;
                    }
                }

                String telValue = tel.isEmpty() ? null : tel;
                String imgValue = img.isEmpty() ? null : img;

                if (isEdit) {
                    String finalHash = toEdit.getPasswordHash();
                    if (!pwd.isEmpty()) finalHash = PasswordUtil.hashPassword(pwd);

                    User updated = new User(
                            toEdit.getId(),
                            nom, prenom, email,
                            finalHash,
                            role,
                            telValue,
                            imgValue
                    );
                    userService.modifier(updated);
                    showInfo("Succès", "Utilisateur modifié.");
                } else {
                    String hash = PasswordUtil.hashPassword(pwd);
                    User created = new User(nom, prenom, email, hash, role, telValue, imgValue);
                    userService.ajouter(created);
                    showInfo("Succès", "Utilisateur ajouté.");
                }

                dialog.close();
                loadUsers();

            } catch (SQLException ex) {
                showError("Erreur", "Opération impossible:\n" + ex.getMessage());
            } catch (Exception ex) {
                showError("Erreur", "Erreur:\n" + ex.getMessage());
            }
        });

        HBox actions = new HBox(10, save, cancel);
        actions.setPadding(new Insets(10, 0, 0, 0));

        root.getChildren().addAll(title, nomField, prenomField, emailField, passwordField, roleCombo, telField, imageUrlField, actions);
        dialog.setScene(new Scene(root));
        dialog.showAndWait();
    }

    // =======================
    //  UI helpers
    // =======================

    private TextField createStyledTextField(String prompt) {
        TextField tf = new TextField();
        tf.setPromptText(prompt);
        tf.setStyle("-fx-font-size: 12; -fx-padding: 10 12; -fx-background-color: white; -fx-background-radius: 10; -fx-border-color: #e1e6ef; -fx-border-radius: 10;");
        return tf;
    }

    private PasswordField createStyledPasswordField(String prompt) {
        PasswordField pf = new PasswordField();
        pf.setPromptText(prompt);
        pf.setStyle("-fx-font-size: 12; -fx-padding: 10 12; -fx-background-color: white; -fx-background-radius: 10; -fx-border-color: #e1e6ef; -fx-border-radius: 10;");
        return pf;
    }

    private ComboBox<String> createRoleCombo() {
        ComboBox<String> cb = new ComboBox<>();
        cb.getItems().addAll("admin", "visiteur", "abonne");
        cb.setPromptText("Rôle");
        cb.setValue("abonne");
        cb.setStyle("-fx-font-size: 12; -fx-padding: 8 10; -fx-background-color: white; -fx-background-radius: 10; -fx-border-color: #e1e6ef; -fx-border-radius: 10;");
        return cb;
    }

    private Button createActionButton(String text, String color) {
        Button btn = new Button(text);
        btn.setStyle("-fx-background-color: " + color + "; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 10; -fx-padding: 10 16;");
        btn.setOnMouseEntered(e -> btn.setOpacity(0.9));
        btn.setOnMouseExited(e -> btn.setOpacity(1.0));
        return btn;
    }

    private void markInvalid(Control field, String message) {
        field.setStyle(field.getStyle() + DIALOG_INVALID_STYLE);
        Tooltip.install(field, new Tooltip(message));
    }

    private void clearInvalid(Control field) {
        String style = field.getStyle();
        field.setStyle(style.replace(DIALOG_INVALID_STYLE, ""));
        Tooltip.uninstall(field, null);
    }

    private void attachClearOnChange(TextInputControl field) {
        field.textProperty().addListener((obs, o, n) -> clearInvalid(field));
    }

    private void showError(String title, String content) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(content);
            alert.showAndWait();
        });
    }

    private void showInfo(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}