package controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import models.User;

public class UserCardController {
    @FXML private ImageView userImageView;
    @FXML private Label nameLabel;
    @FXML private Label roleLabel;
    @FXML private Label emailLabel;
    @FXML private Label phoneLabel;

    public void setUser(User user) {
        if (user == null) {
            userImageView.setImage(new Image(getClass().getResource("/images/bg.png").toExternalForm()));
            nameLabel.setText("");
            roleLabel.setText("");
            emailLabel.setText("");
            phoneLabel.setText("");
            return;
        }
        
        // Charger l'image du utilisateur
        if (user.getImageUrl() != null && !user.getImageUrl().isEmpty()) {
            try {
                String imageUrl = user.getImageUrl();
                // Convertir les chemins locaux Windows en URLs file://
                if (imageUrl.matches("^[A-Za-z]:.*")) {
                    // C'est un chemin Windows, convertir en file:// URL
                    imageUrl = "file:///" + imageUrl.replace("\\", "/");
                }
                userImageView.setImage(new Image(imageUrl));
            } catch (Exception e) {
                userImageView.setImage(new Image(getClass().getResource("/images/bg.png").toExternalForm()));
            }
        } else {
            userImageView.setImage(new Image(getClass().getResource("/images/bg.png").toExternalForm()));
        }
        
        nameLabel.setText(user.getPrenom() + " " + user.getNom());
        roleLabel.setText(user.getRole() != null ? user.getRole().toUpperCase() : "");
        emailLabel.setText(user.getEmail());
        String tel = user.getTelephone() != null && !user.getTelephone().isEmpty() ? user.getTelephone() : "-";
        phoneLabel.setText("Tel: " + tel);
    }
}
