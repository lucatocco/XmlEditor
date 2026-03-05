package it.touchinformatica.xmleditor.controller;

import it.touchinformatica.xmleditor.model.XmlDocument;
import it.touchinformatica.xmleditor.service.XmlService;
import it.touchinformatica.xmleditor.util.LastPositionManager;
import it.touchinformatica.xmleditor.util.RecentFilesManager;
import it.touchinformatica.xmleditor.util.XsdFolderManager;
import it.touchinformatica.xmleditor.view.*;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Controller per una singola EditorSession (un tab).
 * Non ha più riferimento allo Stage — gestisce solo la propria sessione.
 */
public class MainController {

    private final XmlService           xmlService;
    private final RecentFilesManager   recentMgr;
    private final LastPositionManager  lastPosMgr;
    private final EditorPane           editorPane;
    private final TreePane             treePane;
    private final LogPane              logPane;
    private final StatusBar            statusBar;
    private final Consumer<List<Path>> refreshRecentMenu;
    private final Consumer<String>     updateTabTitle;
    private final XsdFolderManager     xsdFolderMgr;

    private XmlDocument currentDoc;
    private Path        currentXsdPath;
    private SearchBar   searchBar;   // creata lazily e iniettata nell'EditorPane parent

    public MainController(XmlService xmlService,
                          RecentFilesManager recentMgr,
                          XsdFolderManager xsdFolderMgr,
                          EditorPane editorPane,
                          TreePane treePane,
                          LogPane logPane,
                          StatusBar statusBar,
                          Consumer<List<Path>> refreshRecentMenu,
                          Consumer<String> updateTabTitle) {
        this.xmlService        = xmlService;
        this.recentMgr         = recentMgr;
        this.lastPosMgr        = new LastPositionManager();
        this.xsdFolderMgr      = xsdFolderMgr;
        this.editorPane        = editorPane;
        this.treePane          = treePane;
        this.logPane           = logPane;
        this.statusBar         = statusBar;
        this.refreshRecentMenu = refreshRecentMenu;
        this.updateTabTitle    = updateTabTitle;

        treePane.setOnNodeSelected(editorPane::goToLine);

        // Statusbar: riga:colonna in tempo reale
        editorPane.getCodeArea().caretPositionProperty().addListener((obs, old, pos) -> {
            int para = editorPane.getCodeArea().getCurrentParagraph();
            int col  = editorPane.getCodeArea().getCaretColumn();
            int tot  = editorPane.getLineCount();
            statusBar.setStatus("Riga " + (para + 1) + ":" + (col + 1)
                + "  |  Righe: " + tot
                + "  |  " + editorPane.getEncoding()
                + "  |  " + editorPane.getLineEnding());
        });
    }

    // ──────────────────────────────────────────────
    // SEARCH BAR (lazy, integrata nel layout del tab)
    // ──────────────────────────────────────────────

    public void setSearchBar(SearchBar sb) { this.searchBar = sb; }

    public void toggleSearch() {
        if (searchBar != null) searchBar.toggleVisible();
    }

    // ──────────────────────────────────────────────
    // NUOVO
    // ──────────────────────────────────────────────

    public void newFile() {
        if (!confirmDiscardChanges()) return;
        editorPane.newDocument();
        treePane.clear();
        currentDoc = null;
        updateTabTitle.accept("Nuovo documento");
        logPane.log("Nuovo documento", "info");
        statusBar.setStatus("Pronto");
    }

    // ──────────────────────────────────────────────
    // APRI
    // ──────────────────────────────────────────────

    public void openFile() {
        if (!confirmDiscardChanges()) return;
        FileChooser fc = new FileChooser();
        fc.setTitle("Apri file XML");
        fc.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("File XML/XSD", "*.xml", "*.xsd"),
            new FileChooser.ExtensionFilter("Tutti i file", "*.*")
        );
        // Nota: openFile singolo nel controller, multi-file gestito da MainStage
        File file = fc.showOpenDialog(null);
        if (file != null) openPath(file.toPath());
    }

    public void openPath(Path path) {
        openPath(path, editorPane.getCharset());
    }

    public void openPath(Path path, Charset charset) {
        if (currentDoc != null && currentDoc.getFilePath() != null) {
            lastPosMgr.savePosition(currentDoc.getFilePath(),
                editorPane.getCodeArea().getCurrentParagraph() + 1);
        }
        statusBar.setStatus("Caricamento " + path.getFileName() + "…");
        Thread.ofVirtual().start(() -> {
            try {
                long sizeMb = Files.size(path) / 1024 / 1024;
                String raw = Files.readString(path, charset);
                String le = detectLineEnding(raw);
                String content = normalizeLineEndings(raw);
                Platform.runLater(() -> {
                    currentDoc = new XmlDocument(path, content);
                    editorPane.setText(content);
                    editorPane.setEncoding(charset.name());
                    editorPane.setLineEnding(le);
                    updateTabTitle.accept(path.getFileName().toString());
                    statusBar.setStatus("Aperto: " + path.getFileName()
                        + " (" + sizeMb + " MB)  |  " + charset.name() + "  |  " + le);
                    logPane.log("Caricato: " + path + " (" + sizeMb + " MB)  [" + le + "]", "ok");
                    recentMgr.add(path);
                    refreshRecentMenu.accept(recentMgr.getRecentFiles());
                    rebuildTree(content);
                    int savedLine = lastPosMgr.loadPosition(path);
                    if (savedLine > 1) editorPane.goToLine(savedLine);
                });
            } catch (IOException e) {
                Platform.runLater(() -> logPane.log("Errore apertura: " + e.getMessage(), "error"));
            }
        });
    }

    public void saveCurrentPosition() {
        if (currentDoc != null && currentDoc.getFilePath() != null) {
            lastPosMgr.savePosition(currentDoc.getFilePath(),
                editorPane.getCodeArea().getCurrentParagraph() + 1);
        }
    }

    // ──────────────────────────────────────────────
    // SALVA
    // ──────────────────────────────────────────────

    public void saveFile() {
        if (currentDoc == null || currentDoc.getFilePath() == null) { saveFileAs(); return; }
        writeToDisk(currentDoc.getFilePath());
    }

    public void saveFileAs() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Salva file XML");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("File XML", "*.xml"));
        File file = fc.showSaveDialog(null);
        if (file != null) writeToDisk(file.toPath());
    }

    private void writeToDisk(Path path) {
        String content  = restoreLineEndings(editorPane.getText(), editorPane.getLineEnding());
        Charset charset = editorPane.getCharset();
        Thread.ofVirtual().start(() -> {
            try {
                Files.writeString(path, content, charset);
                Platform.runLater(() -> {
                    if (currentDoc == null) currentDoc = new XmlDocument(path, content);
                    else { currentDoc.setFilePath(path); currentDoc.markSaved(); }
                    editorPane.markSaved();
                    updateTabTitle.accept(path.getFileName().toString());
                    statusBar.setStatus("Salvato: " + path);
                    logPane.log("Salvato: " + path, "ok");
                    recentMgr.add(path);
                    refreshRecentMenu.accept(recentMgr.getRecentFiles());
                });
            } catch (IOException e) {
                Platform.runLater(() -> logPane.log("Errore salvataggio: " + e.getMessage(), "error"));
            }
        });
    }

    // ──────────────────────────────────────────────
    // PRETTY PRINT
    // ──────────────────────────────────────────────

    public void prettyPrint() {
        String content = editorPane.getText();
        statusBar.setStatus("Pretty print in corso…");
        Thread.ofVirtual().start(() -> {
            try {
                String formatted = xmlService.prettyPrint(content);
                Platform.runLater(() -> {
                    editorPane.setText(formatted);
                    statusBar.setStatus("Pretty print completato");
                    logPane.log("Pretty print completato", "ok");
                    rebuildTree(formatted);
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    logPane.log("Errore XML: " + e.getMessage(), "error");
                    statusBar.setStatus("Errore nel pretty print");
                });
            }
        });
    }

    // ──────────────────────────────────────────────
    // XSD
    // ──────────────────────────────────────────────

    /**
     * Imposta la cartella globale degli XSD (persiste tra sessioni).
     * Mostra un DirectoryChooser; se la cartella era già impostata la usa come punto di partenza.
     */
    public void setXsdFolder() {
        DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle("Seleziona cartella XSD");
        xsdFolderMgr.getXsdFolder().ifPresent(f -> dc.setInitialDirectory(f.toFile()));
        File dir = dc.showDialog(null);
        if (dir == null) return;
        xsdFolderMgr.setXsdFolder(dir.toPath());
        int count = xsdFolderMgr.listAvailableSchemas().size();
        logPane.log("Cartella XSD impostata: " + dir.getAbsolutePath()
            + "  (" + count + " schema/i trovati)", "ok");
        statusBar.setStatus("Cartella XSD: " + dir.getName() + " (" + count + " XSD)");
    }

    /**
     * Carica un singolo XSD manualmente (override puntuale rispetto alla cartella).
     * Il file selezionato ha priorità sulla cartella per la sessione corrente.
     */
    public void loadXsd() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Carica schema XSD");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Schema XSD", "*.xsd"));
        xsdFolderMgr.getXsdFolder().ifPresent(f -> fc.setInitialDirectory(f.toFile()));
        File file = fc.showOpenDialog(null);
        if (file == null) return;
        currentXsdPath = file.toPath();
        logPane.log("Schema XSD caricato manualmente: " + file.getName(), "ok");
        statusBar.setStatus("XSD: " + file.getName());
    }

    public void validate() {
        // 1. Priorità allo schema caricato manualmente in questa sessione
        Path xsdToUse = currentXsdPath;

        // 2. Se non c'è, cerca nella cartella XSD configurata
        if (xsdToUse == null && xsdFolderMgr.isConfigured()) {
            String xmlFileName = (currentDoc != null && currentDoc.getFilePath() != null)
                ? currentDoc.getFileName() : null;

            if (xmlFileName != null) {
                // Cerca <nomeFile>.xsd
                xsdToUse = xsdFolderMgr.findMatchingSchema(xmlFileName).orElse(null);

                if (xsdToUse != null) {
                    logPane.log("Schema trovato automaticamente: " + xsdToUse.getFileName(), "ok");
                } else {
                    // Nessuna corrispondenza diretta → mostra selettore tra gli XSD disponibili
                    List<Path> available = xsdFolderMgr.listAvailableSchemas();
                    if (available.isEmpty()) {
                        logPane.log("Cartella XSD configurata ma non contiene file .xsd", "warn");
                        return;
                    }
                    xsdToUse = showXsdPickerDialog(available);
                    if (xsdToUse == null) return; // annullato dall'utente
                }
            } else {
                // File senza nome (documento nuovo) → mostra selettore
                List<Path> available = xsdFolderMgr.listAvailableSchemas();
                if (!available.isEmpty()) {
                    xsdToUse = showXsdPickerDialog(available);
                    if (xsdToUse == null) return;
                }
            }
        }

        if (xsdToUse == null) {
            logPane.log("Nessuno schema XSD disponibile. "
                + "Usa 'Imposta cartella XSD' o 'Carica XSD' dal menu XML.", "warn");
            return;
        }

        final Path finalXsd = xsdToUse;
        String content = editorPane.getText();
        statusBar.setStatus("Validazione in corso con " + finalXsd.getFileName() + "…");
        Thread.ofVirtual().start(() -> {
            XmlService.ValidationResult result = xmlService.validate(content, finalXsd);
            Platform.runLater(() -> {
                if (result.valid()) {
                    logPane.log("✔ Documento valido  [" + finalXsd.getFileName() + "]", "ok");
                    statusBar.setStatus("Validazione: OK ✔");
                } else {
                    logPane.log("Validazione fallita con [" + finalXsd.getFileName() + "] — "
                        + result.errors().size() + " errore/i:", "error");
                    result.errors().forEach(err ->
                        logPane.log("  [" + (err.fatal() ? "FATALE" : "ERRORE") + "] "
                            + "Riga " + err.line() + ", Col " + err.column() + ": " + err.message(), "error")
                    );
                    if (!result.errors().isEmpty())
                        editorPane.goToLine(result.errors().get(0).line());
                    statusBar.setStatus("Validazione: " + result.errors().size() + " errori ✖");
                }
            });
        });
    }

    /**
     * Mostra un ChoiceDialog per scegliere tra gli XSD disponibili nella cartella.
     * Chiamato quando non c'è corrispondenza automatica per nome.
     *
     * @return path scelto dall'utente, o null se ha annullato
     */
    private Path showXsdPickerDialog(List<Path> available) {
        // Costruiamo le label mostrate all'utente (solo il nome file)
        List<String> names = available.stream()
            .map(p -> p.getFileName().toString())
            .toList();

        ChoiceDialog<String> dialog = new ChoiceDialog<>(names.get(0), names);
        dialog.setTitle("Scegli schema XSD");
        dialog.setHeaderText("Nessuno schema corrisponde automaticamente al file corrente.");
        dialog.setContentText("Schema XSD da usare:");

        return dialog.showAndWait()
            .map(chosen -> available.get(names.indexOf(chosen)))
            .orElse(null);
    }

    /** Ritorna la cartella XSD correntemente configurata (per la statusbar in MainStage). */
    public XsdFolderManager getXsdFolderManager() { return xsdFolderMgr; }

    // ──────────────────────────────────────────────
    // VAI A RIGA
    // ──────────────────────────────────────────────

    public void showGoToLine() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Vai a riga");
        dialog.setHeaderText("Riga (1 - " + editorPane.getLineCount() + "):");
        dialog.setContentText("Riga:");
        dialog.showAndWait().ifPresent(val -> {
            try { editorPane.goToLine(Integer.parseInt(val.trim())); }
            catch (NumberFormatException e) { logPane.log("Numero riga non valido: " + val, "warn"); }
        });
    }

    // ──────────────────────────────────────────────
    // ENCODING
    // ──────────────────────────────────────────────

    public void reloadWithEncoding(String enc) {
        if (currentDoc == null || currentDoc.getFilePath() == null) {
            editorPane.setEncoding(enc);
            statusBar.setStatus("Encoding: " + enc);
            return;
        }
        if (!confirmDiscardChanges()) return;
        try { openPath(currentDoc.getFilePath(), Charset.forName(enc)); }
        catch (Exception e) { logPane.log("Encoding non valido: " + enc, "error"); }
    }

    // ──────────────────────────────────────────────
    // CONFERMA ABBANDONO MODIFICHE
    // ──────────────────────────────────────────────

    public boolean confirmDiscardChanges() {
        if (!editorPane.isModified()) return true;

        String fileName = (currentDoc != null && currentDoc.getFilePath() != null)
            ? currentDoc.getFileName() : "Nuovo documento";

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Modifiche non salvate");
        alert.setHeaderText("\"" + fileName + "\" ha modifiche non salvate.");
        alert.setContentText("Vuoi salvare prima di continuare?");

        ButtonType btnSalva   = new ButtonType("Salva");
        ButtonType btnScarta  = new ButtonType("Non salvare");
        ButtonType btnAnnulla = new ButtonType("Annulla", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(btnSalva, btnScarta, btnAnnulla);

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isEmpty() || result.get() == btnAnnulla) return false;
        if (result.get() == btnSalva) saveFile();
        return true;
    }

    // ──────────────────────────────────────────────
    // ALBERO
    // ──────────────────────────────────────────────

    private void rebuildTree(String xmlText) {
        treePane.rebuildFrom(xmlText,
            () -> logPane.log("Albero non disponibile (XML non ben formato)", "warn"));
    }

    // ──────────────────────────────────────────────
    // LINE ENDINGS
    // ──────────────────────────────────────────────

    private static String detectLineEnding(String text) {
        if (text.contains("\r\n")) return "CRLF";
        if (text.contains("\r"))   return "CR";
        return "LF";
    }

    private static String normalizeLineEndings(String text) {
        return text.replace("\r\n", "\n").replace("\r", "\n");
    }

    private static String restoreLineEndings(String text, String lineEnding) {
        return switch (lineEnding) {
            case "CRLF" -> text.replace("\n", "\r\n");
            case "CR"   -> text.replace("\n", "\r");
            default     -> text;
        };
    }
}
