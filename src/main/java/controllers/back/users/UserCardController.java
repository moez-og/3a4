package controllers.back.users;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import models.users.User;

import java.net.URL;
import java.util.ResourceBundle;

public class UserCardController implements Initializable {

    private static final String DEFAULT_AVATAR = "/images/logo/logo.png";

    @FXML private StackPane imageWrapper;
    @FXML private ImageView userImageView;
    @FXML private Label nameLabel;
    @FXML private Label roleLabel;
    @FXML private Label emailLabel;
    @FXML private Label phoneLabel;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Image responsive (prend la largeur de la card)
        if (imageWrapper != null && userImageView != null) {
            userImageView.fitWidthProperty().bind(imageWrapper.widthProperty());

            // Clip sur le wrapper -> coins supérieurs arrondis
            Rectangle clip = new Rectangle();
            clip.widthProperty().bind(imageWrapper.widthProperty());
            clip.heightProperty().bind(imageWrapper.heightProperty());
            clip.setArcWidth(32);
            clip.setArcHeight(32);
            imageWrapper.setClip(clip);
        }
    }

    public void setUser(User user) {
        if (user == null) {
            setDefaultAvatar();
            nameLabel.setText("");
            roleLabel.setText("");
            emailLabel.setText("");
            phoneLabel.setText("");
            return;
        }

        // Image utilisateur (URL web, file:// ou chemin Windows)
        String img = user.getImageUrl();
        if (img != null && !img.trim().isEmpty()) {
            img = img.trim();
            try {
                // Convertir chemin Windows -> file:///
                if (img.matches("^[A-Za-z]:\\\\.*")) {
                    img = "file:///" + img.replace("\\", "/");
                }
                userImageView.setImage(new Image(img, true));
            } catch (Exception e) {
                setDefaultAvatar();
            }
        } else {
            setDefaultAvatar();
        }

        nameLabel.setText(safe(user.getPrenom()) + " " + safe(user.getNom()));
        String role = user.getRole() != null ? user.getRole().trim() : "";
        roleLabel.setText(role.toUpperCase());

        // Badge couleur selon le rôle (optionnel)
        // On nettoie d'abord les classes précédentes
        roleLabel.getStyleClass().removeAll("badge-admin", "badge-partenaire", "badge-abonne");
        if ("admin".equalsIgnoreCase(role)) {
            roleLabel.getStyleClass().add("badge-admin");
        } else if ("partenaire".equalsIgnoreCase(role)) {
            roleLabel.getStyleClass().add("badge-partenaire");
        } else {
            roleLabel.getStyleClass().add("badge-abonne");
        }
        emailLabel.setText(safe(user.getEmail()));

        String tel = (user.getTelephone() != null && !user.getTelephone().trim().isEmpty())
                ? user.getTelephone().trim()
                : "-";
        phoneLabel.setText("Tel: " + tel);
    }

    private void setDefaultAvatar() {
        try {
            URL url = getClass().getResource(DEFAULT_AVATAR);
            if (url != null) {
                userImageView.setImage(new Image(url.toExternalForm(), true));
            } else {
                // si jamais l’image est absente du classpath
                userImageView.setImage(null);
            }
        } catch (Exception e) {
            userImageView.setImage(null);
        }
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}