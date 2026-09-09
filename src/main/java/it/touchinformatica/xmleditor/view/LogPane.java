package it.touchinformatica.xmleditor.view;

import it.touchinformatica.xmleditor.util.I18n;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Priority;

/**
 * LogPane — pannello log inferiore.
 */
public class LogPane extends VBox {

    private final Label title;
    private final TextArea area;

    public LogPane() {
        title = new Label(I18n.t("panel.log"));
        title.getStyleClass().add("panel-title");

        area = new TextArea();
        area.setEditable(false);
        area.setWrapText(false);
        area.getStyleClass().add("log-area");
        area.setPrefHeight(130);
        VBox.setVgrow(area, Priority.ALWAYS);

        getChildren().addAll(title, area);
        getStyleClass().add("log-pane");
    }

    public void log(String msg, String level) {
        String prefix = switch (level) {
            case "ok"    -> "✔ ";
            case "warn"  -> "⚠ ";
            case "error" -> "✖ ";
            default      -> "  ";
        };
        Platform.runLater(() -> {
            area.appendText(prefix + msg + "\n");
            area.setScrollTop(Double.MAX_VALUE);
        });
    }

    /** Ricarica i testi dopo un cambio di lingua. */
    public void applyLanguage() {
        title.setText(I18n.t("panel.log"));
    }

    public void clear() { Platform.runLater(() -> area.clear()); }
}


