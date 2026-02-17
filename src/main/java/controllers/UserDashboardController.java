package controllers;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.Parent;
import javafx.stage.Stage;
import models.User;
import services.UserService;
import utils.PasswordUtil;
import gui.LoginApp;

import java.sql.SQLException;
import java.util.regex.Pattern;
import java.util.List;
import java.util.Optional;

public class UserDashboardController {
    private static final int MIN_PASSWORD_LENGTH = 6;
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    private static final String DIALOG_INVALID_STYLE = " -fx-border-color: #e74c3c; -fx-border-width: 2; -fx-background-color: #fff5f5;";

    @FXML private TextField searchField;
    @FXML private Label totalUsersLabel;
    @FXML private Label activeUsersLabel;
    @FXML private Label adminsLabel;
    @FXML private Label userInfoLabel;
    @FXML private Label userRoleLabel;
    @FXML private HBox usersRow;
    @FXML private Pagination usersPagination;
    @FXML private ImageView logoView;
    @FXML private Button btnDashboard;
    @FXML private Button btnUtilisateurs;
    @FXML private Button btnAjouter;
    @FXML private Button btnDeconnexion;

    private UserService userService;
    private ObservableList<User> usersList;
    private ObservableList<User> filteredUsersList;
    private final int pageSize = 3;
    private User selectedUser;
    private User currentUser;
    private Parent selectedCard;
    private Stage primaryStage;

    public void setPrimaryStage(Stage primaryStage) {
        this.primaryStage = primaryStage;
    }

    public void setCurrentUser(User user) {
        this.currentUser = user;
        updateCurrentUserInfo();
    }

    @FXML
    public void initialize() {
        userService = new UserService();
        setupUsersList();
        setupPagination();
        loadUsers();
        setupSearch();
        updateCurrentUserInfo();
    }

    private void updateCurrentUserInfo() {
        if (currentUser == null || userInfoLabel == null || userRoleLabel == null) {
            return;
        }
        String fullName = (currentUser.getPrenom() != null ? currentUser.getPrenom() : "") +
                " " + (currentUser.getNom() != null ? currentUser.getNom() : "");
        userInfoLabel.setText("👤 " + fullName.trim());
        String role = currentUser.getRole() != null ? currentUser.getRole().toUpperCase() : "";
        userRoleLabel.setText(role);
    }

    private void setupUsersList() {
        usersRow.setSpacing(16);
    }

    private void setupPagination() {
        usersPagination.currentPageIndexProperty().addListener((obs, oldVal, newVal) -> {
            updateListViewPage(newVal.intValue());
        });
    }

    private void setupSearch() {
        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            filterUsers(newValue);
        });
    }

    @FXML
    private void loadUsers() {
        new Thread(() -> {
            try {
                List<User> users = userService.obtenirTous();
                usersList = FXCollections.observableArrayList(users);
                filteredUsersList = FXCollections.observableArrayList(users);
                
                int total = users.size();
                long admins = users.stream().filter(u -> "admin".equalsIgnoreCase(u.getRole())).count();
                long visitors = users.stream().filter(u -> "visiteur".equalsIgnoreCase(u.getRole())).count();

                javafx.application.Platform.runLater(() -> {
                    updatePagination();
                    updateListViewPage(0);
                    totalUsersLabel.setText(String.valueOf(total));
                    activeUsersLabel.setText(String.valueOf(admins));
                    adminsLabel.setText(String.valueOf(visitors));
                });
            } catch (SQLException e) {
                showError("Erreur", "Impossible de charger les utilisateurs");
            }
        }).start();
    }

    private void filterUsers(String query) {
        if (usersList == null) return;
        if (query == null || query.isEmpty()) {
            filteredUsersList.setAll(usersList);
            updatePagination();
            updateListViewPage(0);
            return;
        }

        String lowerCaseQuery = query.toLowerCase();
        ObservableList<User> filteredList = FXCollections.observableArrayList();

        for (User user : usersList) {
            if (user.getNom().toLowerCase().contains(lowerCaseQuery) ||
                user.getPrenom().toLowerCase().contains(lowerCaseQuery) ||
                user.getEmail().toLowerCase().contains(lowerCaseQuery) ||
                (user.getRole() != null && user.getRole().toLowerCase().contains(lowerCaseQuery)) ||
                (user.getTelephone() != null && user.getTelephone().contains(lowerCaseQuery))) {
                filteredList.add(user);
            }
        }
        filteredUsersList.setAll(filteredList);
        updatePagination();
        updateListViewPage(0);
    }

    @FXML
    private void showDashboard() {
        loadUsers();
    }

    @FXML
    private void showUsersTable() {
        loadUsers();
    }

    @FXML
    private void showAddUserDialog() {
        Stage dialog = new Stage();
        dialog.setTitle("Ajouter un Utilisateur");
        dialog.setWidth(500);
        dialog.setHeight(600);

        VBox dialogRoot = new VBox(15);
        dialogRoot.setPadding(new Insets(20));
        dialogRoot.setStyle("-fx-background-color: #f5f5f5;");

        Label titleLabel = new Label("Créer un Nouvel Utilisateur");
        titleLabel.setStyle("-fx-font-size: 16; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        TextField nomField = createStyledTextField("Nom");
        TextField prenomField = createStyledTextField("Prénom");
        TextField emailField = createStyledTextField("Email");
        PasswordField passwordField = createStyledPasswordField("Mot de passe");
        ComboBox<String> roleCombo = createRoleCombo();
        TextField telField = createStyledTextField("Téléphone");
        TextField imageUrlField = createStyledTextField("Image URL");

        attachClearOnChange(nomField);
        attachClearOnChange(prenomField);
        attachClearOnChange(emailField);
        attachClearOnChange(passwordField);
        attachClearOnChange(telField);
        attachClearOnChange(imageUrlField);

        Button saveButton = createActionButton("Enregistrer", "#1e3a5f");
        saveButton.setPrefWidth(200);
        saveButton.setOnAction(e -> {
            try {
                String nom = nomField.getText().trim();
                String prenom = prenomField.getText().trim();
                String email = emailField.getText().trim();
                String password = passwordField.getText();
                String role = roleCombo.getValue();
                String telephone = telField.getText().trim();
                String imageUrl = imageUrlField.getText().trim();

                clearInvalid(nomField);
                clearInvalid(prenomField);
                clearInvalid(emailField);
                clearInvalid(passwordField);
                clearInvalid(telField);
                clearInvalid(imageUrlField);

                boolean hasError = false;
                if (nom.isEmpty()) {
                    markInvalid(nomField, "Nom obligatoire");
                    hasError = true;
                }
                if (prenom.isEmpty()) {
                    markInvalid(prenomField, "Prenom obligatoire");
                    hasError = true;
                }
                if (email.isEmpty()) {
                    markInvalid(emailField, "Email obligatoire");
                    hasError = true;
                }
                if (password.isEmpty()) {
                    markInvalid(passwordField, "Mot de passe obligatoire");
                    hasError = true;
                }
                if (hasError) {
                    showError("Validation", "Veuillez corriger les champs en rouge");
                    return;
                }

                if (!isValidEmail(email)) {
                    markInvalid(emailField, "Format email invalide");
                    showError("Validation", "Email invalide");
                    return;
                }

                if (password.length() < MIN_PASSWORD_LENGTH) {
                    markInvalid(passwordField, "Au moins " + MIN_PASSWORD_LENGTH + " caracteres");
                    showError("Validation", "Mot de passe trop court");
                    return;
                }

                if (userService.emailExiste(email)) {
                    markInvalid(emailField, "Email existe deja");
                    showError("Erreur", "Email existe deja!");
                    return;
                }

                String hashedPassword = PasswordUtil.hashPassword(password);
                User newUser = new User(nom, prenom, email, hashedPassword, role, telephone, imageUrl);
                userService.ajouter(newUser);

                showSuccess("Succès", "Utilisateur ajouté!");
                dialog.close();
                loadUsers();
            } catch (SQLException ex) {
                showError("Erreur", ex.getMessage());
            }
        });

        dialogRoot.getChildren().addAll(
                titleLabel, new Separator(),
                new Label("Nom:"), nomField,
                new Label("Prénom:"), prenomField,
                new Label("Email:"), emailField,
                new Label("Mot de passe:"), passwordField,
                new Label("Rôle:"), roleCombo,
                new Label("Téléphone:"), telField,
                new Label("Image URL:"), imageUrlField,
                saveButton
        );

        Scene scene = new Scene(new ScrollPane(dialogRoot));
        dialog.setScene(scene);
        dialog.showAndWait();
    }

    @FXML
    private void showEditUserDialog() {
        User selectedUser = getSelectedUser();
        if (selectedUser == null) {
            showError("Sélection", "Sélectionnez un utilisateur!");
            return;
        }

        Stage dialog = new Stage();
        dialog.setTitle("Modifier Utilisateur");
        dialog.setWidth(500);
        dialog.setHeight(500);

        VBox dialogRoot = new VBox(15);
        dialogRoot.setPadding(new Insets(20));
        dialogRoot.setStyle("-fx-background-color: #f5f5f5;");

        TextField nomField = createStyledTextField("Nom");
        nomField.setText(selectedUser.getNom());

        TextField prenomField = createStyledTextField("Prénom");
        prenomField.setText(selectedUser.getPrenom());

        TextField emailField = createStyledTextField("Email");
        emailField.setText(selectedUser.getEmail());
        emailField.setDisable(true);

        ComboBox<String> roleCombo = createRoleCombo();
        roleCombo.setValue(selectedUser.getRole());

        TextField telField = createStyledTextField("Téléphone");
        telField.setText(selectedUser.getTelephone() != null ? selectedUser.getTelephone() : "");

        TextField imageUrlField = createStyledTextField("Image URL");
        imageUrlField.setText(selectedUser.getImageUrl() != null ? selectedUser.getImageUrl() : "");

        attachClearOnChange(nomField);
        attachClearOnChange(prenomField);
        attachClearOnChange(telField);
        attachClearOnChange(imageUrlField);

        Button saveButton = createActionButton("Enregistrer", "#1e3a5f");
        saveButton.setPrefWidth(200);
        saveButton.setOnAction(e -> {
            try {
                clearInvalid(nomField);
                clearInvalid(prenomField);
                clearInvalid(telField);

                String nom = nomField.getText().trim();
                String prenom = prenomField.getText().trim();
                String telephone = telField.getText().trim();
                String imageUrl = imageUrlField.getText().trim();

                boolean hasError = false;
                if (nom.isEmpty()) {
                    markInvalid(nomField, "Nom obligatoire");
                    hasError = true;
                }
                if (prenom.isEmpty()) {
                    markInvalid(prenomField, "Prenom obligatoire");
                    hasError = true;
                }
                if (hasError) {
                    showError("Validation", "Veuillez corriger les champs en rouge");
                    return;
                }

                selectedUser.setNom(nom);
                selectedUser.setPrenom(prenom);
                selectedUser.setRole(roleCombo.getValue());
                selectedUser.setTelephone(telephone);
                selectedUser.setImageUrl(imageUrl);

                userService.modifier(selectedUser);
                if (currentUser != null && selectedUser.getId() == currentUser.getId()) {
                    currentUser.setNom(selectedUser.getNom());
                    currentUser.setPrenom(selectedUser.getPrenom());
                    currentUser.setRole(selectedUser.getRole());
                    currentUser.setTelephone(selectedUser.getTelephone());
                    currentUser.setImageUrl(selectedUser.getImageUrl());
                    updateCurrentUserInfo();
                }
                showSuccess("Succès", "Utilisateur modifié!");
                dialog.close();
                loadUsers();
            } catch (SQLException ex) {
                showError("Erreur", ex.getMessage());
            }
        });

        dialogRoot.getChildren().addAll(
                new Label("Modifier l'Utilisateur"), new Separator(),
                new Label("Nom:"), nomField,
                new Label("Prénom:"), prenomField,
                new Label("Email:"), emailField,
                new Label("Rôle:"), roleCombo,
                new Label("Téléphone:"), telField,
                new Label("Image URL:"), imageUrlField,
                saveButton
        );

        Scene scene = new Scene(new ScrollPane(dialogRoot));
        dialog.setScene(scene);
        dialog.showAndWait();
    }

    @FXML
    private void deleteUser() {
        User selectedUser = getSelectedUser();
        if (selectedUser == null) {
            showError("Sélection", "Sélectionnez un utilisateur!");
            return;
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirmation de suppression");
        alert.setHeaderText(null);
        alert.setContentText("Voulez-vous vraiment supprimer " + selectedUser.getPrenom() + " " + selectedUser.getNom() + " ?");
        
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                userService.supprimer(selectedUser.getId());
                showSuccess("Succès", "Utilisateur supprimé avec succès!");
                loadUsers();
            } catch (SQLException e) {
                showError("Erreur", "Erreur lors de la suppression: " + e.getMessage());
            }
        }
    }

    @FXML
    private void handleLogout() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Déconnexion");
        alert.setHeaderText(null);
        alert.setContentText("Êtes-vous sûr de vouloir vous déconnecter ?");
        
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                LoginApp app = new LoginApp();
                Stage stage = new Stage();
                app.start(stage);
                if (primaryStage != null) {
                    primaryStage.close();
                }
            } catch (Exception e) {
                showError("Erreur", "Erreur lors de la déconnexion: " + e.getMessage());
            }
        }
    }

    private TextField createStyledTextField(String prompt) {
        TextField field = new TextField();
        field.setPromptText(prompt);
        field.setStyle("-fx-padding: 10; -fx-background-radius: 5;");
        field.getProperties().put("baseStyle", field.getStyle());
        return field;
    }

    private PasswordField createStyledPasswordField(String prompt) {
        PasswordField field = new PasswordField();
        field.setPromptText(prompt);
        field.setStyle("-fx-padding: 10; -fx-background-radius: 5;");
        field.getProperties().put("baseStyle", field.getStyle());
        return field;
    }

    private void attachClearOnChange(TextInputControl field) {
        field.textProperty().addListener((obs, oldVal, newVal) -> {
            if (Boolean.TRUE.equals(field.getProperties().get("invalid"))) {
                clearInvalid(field);
            }
        });
    }

    private void markInvalid(Control field, String message) {
        field.getProperties().put("invalid", true);
        Tooltip tooltip = new Tooltip(message);
        field.getProperties().put("errorTooltip", tooltip);
        Tooltip.install(field, tooltip);

        String baseStyle = (String) field.getProperties().getOrDefault("baseStyle", field.getStyle());
        field.setStyle(baseStyle + DIALOG_INVALID_STYLE);
    }

    private void clearInvalid(Control field) {
        field.getProperties().remove("invalid");
        Object tooltip = field.getProperties().remove("errorTooltip");
        if (tooltip instanceof Tooltip t) {
            Tooltip.uninstall(field, t);
        }
        String baseStyle = (String) field.getProperties().getOrDefault("baseStyle", "");
        field.setStyle(baseStyle);
    }

    private boolean isValidEmail(String email) {
        return EMAIL_PATTERN.matcher(email).matches();
    }

    private ComboBox<String> createRoleCombo() {
        ComboBox<String> combo = new ComboBox<>();
        combo.setItems(FXCollections.observableArrayList("admin", "visiteur", "abonne"));
        combo.setValue("visiteur");
        combo.setPrefWidth(460);
        combo.setStyle("-fx-padding: 5;");
        return combo;
    }

    private Button createActionButton(String text, String color) {
        Button btn = new Button(text);
        btn.setStyle("-fx-background-color: " + color + "; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10 20; -fx-background-radius: 5; -fx-cursor: hand;");
        return btn;
    }

    private void showError(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private void showSuccess(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private void updatePagination() {
        if (filteredUsersList == null) {
            usersPagination.setPageCount(1);
            return;
        }
        int totalItems = filteredUsersList.size();
        int pageCount = (int) Math.ceil((double) totalItems / pageSize);
        usersPagination.setPageCount(Math.max(pageCount, 1));
    }

    private void updateListViewPage(int pageIndex) {
        if (filteredUsersList == null) {
            usersRow.getChildren().clear();
            return;
        }
        int fromIndex = pageIndex * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, filteredUsersList.size());
        if (fromIndex >= filteredUsersList.size()) {
            usersRow.getChildren().clear();
            return;
        }

        usersRow.getChildren().clear();
        selectedUser = null;
        selectedCard = null;
        for (User user : filteredUsersList.subList(fromIndex, toIndex)) {
            Parent card = buildUserCard(user);
            usersRow.getChildren().add(card);
        }
    }

    private Parent buildUserCard(User user) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/UserCard.fxml"));
            Parent root = loader.load();
            UserCardController controller = loader.getController();
            controller.setUser(user);
            root.setOnMouseClicked(event -> selectUserCard(user, root));
            return root;
        } catch (Exception e) {
            Label fallback = new Label(user.getPrenom() + " " + user.getNom());
            fallback.setStyle("-fx-padding: 12; -fx-background-color: white; -fx-background-radius: 10; -fx-border-color: #e1e6ef; -fx-border-radius: 10;");
            return fallback;
        }
    }

    private User getSelectedUser() {
        return selectedUser;
    }

    private void selectUserCard(User user, Parent card) {
        if (selectedCard != null) {
            selectedCard.setStyle("-fx-background-color: #ffffff; -fx-background-radius: 16; -fx-border-color: #e1e6ef; -fx-border-radius: 16;");
        }
        selectedUser = user;
        selectedCard = card;
        card.setStyle("-fx-background-color: #f8fbff; -fx-background-radius: 16; -fx-border-color: #4a6fa5; -fx-border-radius: 16; -fx-border-width: 2;");
    }
}
