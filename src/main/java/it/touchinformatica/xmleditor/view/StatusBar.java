package it.touchinformatica.xmleditor.view;

import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

public class StatusBar extends HBox {

    private final Label label;

    public StatusBar() {
        label = new Label("Pronto");
        label.getStyleClass().add("status-label");
        HBox.setHgrow(label, Priority.ALWAYS);
        getChildren().add(label);
        getStyleClass().add("status-bar");
    }

    public void setStatus(String msg) {
        Platform.runLater(() -> label.setText(msg));
    }
}
