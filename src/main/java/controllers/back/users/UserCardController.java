package controllers.back.users;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import models.users.User;

import java.net.URL;

public class UserCardController {

    private static final String DEFAULT_AVATAR = "/images/logo/logo.png";

    @FXML private ImageView userImageView;
    @FXML private Label nameLabel;
    @FXML private Label roleLabel;
    @FXML private Label emailLabel;
    @FXML private Label phoneLabel;

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
        roleLabel.setText(user.getRole() != null ? user.getRole().toUpperCase() : "");
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