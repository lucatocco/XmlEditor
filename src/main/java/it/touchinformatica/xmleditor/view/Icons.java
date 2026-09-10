package it.touchinformatica.xmleditor.view;

import javafx.scene.Node;
import javafx.scene.layout.Region;

/**
 * Icone della barra degli strumenti, disegnate come forme vettoriali.
 *
 * <p>Prima erano emoji nel testo del pulsante, ma JavaFX non disegna i font a
 * colori: su Linux, dove l'unico font emoji installato è di norma Noto Color
 * Emoji, i pulsanti restavano senza icona. Un tracciato vettoriale non dipende
 * dai font installati, si adatta a qualsiasi dimensione e prende il colore dal
 * CSS come qualunque altro nodo.</p>
 *
 * <p>I tracciati sono disegnati su una griglia 24×24; {@code -fx-shape} li scala
 * alla dimensione richiesta.</p>
 */
final class Icons {

    static final String NEW_TAB  = "M11 4h2v7h7v2h-7v7h-2v-7H4v-2h7z";
    static final String OPEN     = "M2 5h7l2 2h11v13H2zM4 18h15V9H4z";
    static final String SAVE     = "M11 3h2v9.2l3.6-3.6 1.4 1.4-6 6-6-6 1.4-1.4 3.6 3.6V3zM4 19h16v2H4z";
    static final String UNDO     = "M8 8V4L2 10l6 6v-4h6a4 4 0 0 1 0 8h-3v2h3a6 6 0 0 0 0-12H8z";
    static final String REDO     = "M16 8V4l6 6-6 6v-4h-6a4 4 0 0 0 0 8h3v2h-3a6 6 0 0 1 0-12h6z";
    static final String FORMAT   = "M3 4h18v2H3zM7 9h14v2H7zM7 14h14v2H7zM3 19h18v2H3z";
    static final String SCHEMA   = "M6 2h8l6 6v14H6zM8 20h10V10h-6V4H8z";
    static final String VALIDATE = "M9 16.2 4.8 12l-1.4 1.4L9 19 21 7l-1.4-1.4z";
    static final String FIND     = "M10 2a8 8 0 1 0 4.94 14.29l4.88 4.88 1.42-1.42-4.88-4.88A8 8 0 0 0 10 2zm0 2a6 6 0 1 1 0 12 6 6 0 0 1 0-12z";
    static final String LINE     = "M7 3h2v18H7zM15 3h2v18h-2zM3 7h18v2H3zM3 15h18v2H3z";

    private static final double SIZE = 15;

    private Icons() {}

    /** Nodo icona pronto per {@code setGraphic}, colorato dal CSS. */
    static Node of(String svgPath) {
        Region icon = new Region();
        icon.setStyle("-fx-shape: \"" + svgPath + "\";");
        icon.getStyleClass().add("toolbar-icon");
        icon.setMinSize(SIZE, SIZE);
        icon.setPrefSize(SIZE, SIZE);
        icon.setMaxSize(SIZE, SIZE);
        return icon;
    }
}
