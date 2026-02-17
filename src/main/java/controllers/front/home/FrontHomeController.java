package controllers.front.home;

import javafx.animation.ScaleTransition;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

public class FrontHomeController {

    @FXML private VBox homeRoot;

    @FXML
    private void initialize() {
        if (homeRoot != null) {
            applyHoverScaleToClass(homeRoot, "hoverable");
        }
    }

    private void applyHoverScaleToClass(Parent root, String styleClass) {
        for (Node n : root.lookupAll("." + styleClass)) {
            ScaleTransition in = new ScaleTransition(Duration.millis(140), n);
            in.setToX(1.02);
            in.setToY(1.02);

            ScaleTransition out = new ScaleTransition(Duration.millis(140), n);
            out.setToX(1.0);
            out.setToY(1.0);

            n.setOnMouseEntered(e -> in.playFromStart());
            n.setOnMouseExited(e -> out.playFromStart());
        }
    }
}