package it.touchinformatica.xmleditor.view;

import it.touchinformatica.xmleditor.util.XmlSyntaxHighlighter;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.layout.StackPane;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Pannello editor basato su RichTextFX CodeArea.
 * Funzionalità notepad: undo/redo, cut/copy/paste, selectAll,
 * word wrap, tracking modifiche, encoding property.
 */
public class EditorPane extends StackPane {

    private final CodeArea codeArea;
    private final BooleanProperty modified    = new SimpleBooleanProperty(false);
    private final StringProperty encoding     = new SimpleStringProperty("UTF-8");
    private final StringProperty lineEnding   = new SimpleStringProperty("LF");
    private boolean suppressModified = false;

    public EditorPane() {
        this.codeArea = new CodeArea();
        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
        codeArea.getStyleClass().add("xml-code-area");
        XmlSyntaxHighlighter.bind(codeArea);

        // Traccia modifiche utente
        codeArea.textProperty().addListener((obs, old, val) -> {
            if (!suppressModified) modified.set(true);
        });

        VirtualizedScrollPane<CodeArea> scrollPane = new VirtualizedScrollPane<>(codeArea);
        getChildren().add(scrollPane);
        getStyleClass().add("editor-pane");
    }

    // ── Testo ──────────────────────────────────────

    public String getText() { return codeArea.getText(); }

    /** Imposta testo senza segnare come modificato, resetta undo history */
    public void setText(String text) {
        Platform.runLater(() -> {
            suppressModified = true;
            codeArea.clear();
            codeArea.appendText(text != null ? text : "");
            codeArea.moveTo(0);
            codeArea.requestFollowCaret();
            codeArea.getUndoManager().forgetHistory();
            modified.set(false);
            suppressModified = false;
        });
    }

    public void newDocument() { setText(""); encoding.set("UTF-8"); lineEnding.set("LF"); }

    // ── Operazioni Modifica ────────────────────────

    public void undo()      { codeArea.undo(); }
    public void redo()      { codeArea.redo(); }
    public void cut()       { codeArea.cut(); }
    public void copy()      { codeArea.copy(); }
    public void paste()     { codeArea.paste(); }
    public void selectAll() { codeArea.selectAll(); }
    public boolean canUndo(){ return codeArea.getUndoManager().isUndoAvailable(); }
    public boolean canRedo(){ return codeArea.getUndoManager().isRedoAvailable(); }

    // ── Navigazione ────────────────────────────────

    public void goToLine(int line) {
        Platform.runLater(() -> {
            int target = Math.max(0, line - 1);
            if (target < getLineCount()) {
                codeArea.moveTo(target, 0);
                codeArea.requestFollowCaret();
            }
        });
    }

    public int getLineCount() {
        return ((java.util.List<?>) codeArea.getParagraphs()).size();
    }

    // ── Word Wrap ──────────────────────────────────

    public void setWordWrap(boolean wrap) { codeArea.setWrapText(wrap); }
    public boolean isWordWrap()           { return codeArea.isWrapText(); }

    // ── Properties ─────────────────────────────────

    public BooleanProperty modifiedProperty() { return modified; }
    public boolean isModified()               { return modified.get(); }
    public void markSaved()                   { modified.set(false); }

    public StringProperty encodingProperty()   { return encoding; }
    public String getEncoding()                { return encoding.get(); }
    public void setEncoding(String enc)        { encoding.set(enc); }

    public StringProperty lineEndingProperty() { return lineEnding; }
    public String getLineEnding()              { return lineEnding.get(); }
    public void setLineEnding(String le)       { lineEnding.set(le); }

    public Charset getCharset() {
        try { return Charset.forName(encoding.get()); }
        catch (Exception e) { return StandardCharsets.UTF_8; }
    }

    public CodeArea getCodeArea() { return codeArea; }
}
