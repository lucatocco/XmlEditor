package it.touchinformatica.xmleditor.view;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import org.fxmisc.richtext.CodeArea;

/**
 * Barra di ricerca con navigazione avanti/indietro nel CodeArea.
 */
public class SearchBar extends HBox {

    private final TextField searchField;
    private final Label countLabel;
    private final EditorPane editorPane;

    // Stato ricerca
    private String lastQuery = "";
    private int currentIndex = -1;
    private java.util.List<Integer> occurrences = new java.util.ArrayList<>();

    public SearchBar(EditorPane editorPane) {
        this.editorPane = editorPane;

        searchField = new TextField();
        searchField.setPromptText("Search in XML…");
        searchField.setPrefWidth(280);
        HBox.setHgrow(searchField, Priority.ALWAYS);

        countLabel = new Label("");
        countLabel.setMinWidth(90);

        Button btnPrev = new Button("◀");
        Button btnNext = new Button("▶");
        Button btnClose = new Button("✕");

        btnPrev.setOnAction(e -> movePrev());
        btnNext.setOnAction(e -> moveNext());
        btnClose.setOnAction(e -> hide());

        searchField.textProperty().addListener((obs, old, val) -> performSearch(val));
        searchField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                if (e.isShiftDown()) movePrev();
                else moveNext();
            }
        });

        getChildren().addAll(
            new Label("🔍  "),
            searchField,
            btnPrev, btnNext,
            countLabel,
            btnClose
        );

        setSpacing(6);
        setPadding(new Insets(4, 8, 4, 8));
        getStyleClass().add("search-bar");

        setVisible(false);
        setManaged(false);
    }

    // ──────────────────────────────────────────────
    // VISIBILITÀ
    // ──────────────────────────────────────────────

    public void toggleVisible() {
        if (isVisible()) hide();
        else {
            show();
            searchField.requestFocus();
        }
    }

    public void show() {
        setVisible(true);
        setManaged(true);
        searchField.requestFocus();
    }

    public void hide() {
        setVisible(false);
        setManaged(false);
    }

    // ──────────────────────────────────────────────
    // RICERCA
    // ──────────────────────────────────────────────

    private void performSearch(String query) {
        occurrences.clear();
        currentIndex = -1;
        lastQuery = query;

        if (query == null || query.isBlank()) {
            countLabel.setText("");
            clearHighlight();
            return;
        }

        String text = editorPane.getText().toLowerCase();
        String q = query.toLowerCase();
        int idx = 0;
        while ((idx = text.indexOf(q, idx)) != -1) {
            occurrences.add(idx);
            idx += q.length();
        }

        countLabel.setText(occurrences.isEmpty()
            ? "No results"
            : "0/" + occurrences.size()
        );

        if (!occurrences.isEmpty()) {
            currentIndex = 0;
            highlight(currentIndex);
        }
    }

    private void moveNext() {
        if (occurrences.isEmpty()) return;
        currentIndex = (currentIndex + 1) % occurrences.size();
        highlight(currentIndex);
    }

    private void movePrev() {
        if (occurrences.isEmpty()) return;
        currentIndex = (currentIndex - 1 + occurrences.size()) % occurrences.size();
        highlight(currentIndex);
    }

    private void highlight(int idx) {
        CodeArea area = editorPane.getCodeArea();
        int pos = occurrences.get(idx);
        int len = lastQuery.length();
        area.selectRange(pos, pos + len);
        area.requestFollowCaret();
        countLabel.setText((idx + 1) + "/" + occurrences.size());
    }

    private void clearHighlight() {
        editorPane.getCodeArea().deselect();
    }
}
