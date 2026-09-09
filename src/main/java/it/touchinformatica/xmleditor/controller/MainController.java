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
            statusBar.setStatus("Line " + (para + 1) + ":" + (col + 1)
                + "  |  Lines: " + tot
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
        updateTabTitle.accept("Untitled");
        logPane.log("New document", "info");
        statusBar.setStatus("Ready");
    }

    // ──────────────────────────────────────────────
    // APRI
    // ──────────────────────────────────────────────

    public void openFile() {
        if (!confirmDiscardChanges()) return;
        FileChooser fc = new FileChooser();
        fc.setTitle("Open XML File");
        fc.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("File XML/XSD/TXT", "*.xml", "*.xsd", "*.txt"),
            new FileChooser.ExtensionFilter("All Files", "*.*")
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
        statusBar.setStatus("Loading " + path.getFileName() + "…");
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
                    statusBar.setStatus("Opened: " + path.getFileName()
                        + " (" + sizeMb + " MB)  |  " + charset.name() + "  |  " + le);
                    logPane.log("Loaded: " + path + " (" + sizeMb + " MB)  [" + le + "]", "ok");
                    recentMgr.add(path);
                    refreshRecentMenu.accept(recentMgr.getRecentFiles());
                    rebuildTree(content);
                    int savedLine = lastPosMgr.loadPosition(path);
                    if (savedLine > 1) editorPane.goToLine(savedLine);
                });
            } catch (IOException e) {
                Platform.runLater(() -> logPane.log("Error opening file: " + e.getMessage(), "error"));
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

    /** @return false se l'utente ha annullato la scelta del file */
    public boolean saveFile() {
        if (currentDoc == null || currentDoc.getFilePath() == null) return saveFileAs();
        writeToDisk(currentDoc.getFilePath());
        return true;
    }

    /** @return false se l'utente ha annullato la scelta del file */
    public boolean saveFileAs() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Save XML File");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("XML Files", "*.xml"));
        File file = fc.showSaveDialog(null);
        if (file == null) return false;
        writeToDisk(file.toPath());
        return true;
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
                    statusBar.setStatus("Saved: " + path);
                    logPane.log("Saved: " + path, "ok");
                    recentMgr.add(path);
                    refreshRecentMenu.accept(recentMgr.getRecentFiles());
                });
            } catch (IOException e) {
                Platform.runLater(() -> logPane.log("Error saving file: " + e.getMessage(), "error"));
            }
        });
    }

    // ──────────────────────────────────────────────
    // PRETTY PRINT
    // ──────────────────────────────────────────────

    public void prettyPrint() {
        String  content = editorPane.getText();
        Charset charset = editorPane.getCharset();
        statusBar.setStatus("Pretty printing…");
        Thread.ofVirtual().start(() -> {
            try {
                String formatted = xmlService.prettyPrint(content, charset);
                Platform.runLater(() -> {
                    // replaceText e non setText: il pretty print resta annullabile
                    // e il documento risulta modificato finché non lo salvi
                    editorPane.replaceText(formatted);
                    statusBar.setStatus("Pretty print complete");
                    logPane.log("Pretty print complete", "ok");
                    rebuildTree(formatted);
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    logPane.log("XML error: " + e.getMessage(), "error");
                    statusBar.setStatus("Pretty print failed");
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
        dc.setTitle("Select XSD Folder");
        xsdFolderMgr.getXsdFolder().ifPresent(f -> dc.setInitialDirectory(f.toFile()));
        File dir = dc.showDialog(null);
        if (dir == null) return;
        xsdFolderMgr.setXsdFolder(dir.toPath());
        int count = xsdFolderMgr.listAvailableSchemas().size();
        logPane.log("XSD folder set: " + dir.getAbsolutePath()
            + "  (" + count + " schema(s) found)", "ok");
        statusBar.setStatus("XSD folder: " + dir.getName() + " (" + count + " XSD)");
    }

    /**
     * Carica un singolo XSD manualmente (override puntuale rispetto alla cartella).
     * Il file selezionato ha priorità sulla cartella per la sessione corrente.
     */
    public void loadXsd() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Load XSD Schema");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("XSD Schema", "*.xsd"));
        xsdFolderMgr.getXsdFolder().ifPresent(f -> fc.setInitialDirectory(f.toFile()));
        File file = fc.showOpenDialog(null);
        if (file == null) return;
        currentXsdPath = file.toPath();
        logPane.log("XSD schema loaded manually: " + file.getName(), "ok");
        statusBar.setStatus("XSD: " + file.getName());
    }

    public void validate() {
        // 1. Priorità allo schema caricato manualmente in questa sessione
        Path xsdToUse = currentXsdPath;

        // 2. Se non c'è, cerca nella cartella XSD configurata
        if (xsdToUse == null && xsdFolderMgr.isConfigured()) {

            // 2a. Prova prima a leggere xsi:schemaLocation dal contenuto XML
            String xmlContent = editorPane.getText();
            String schemaLocationXsd = extractSchemaLocationFilename(xmlContent);
            if (schemaLocationXsd != null) {
                xsdToUse = xsdFolderMgr.findMatchingSchema(schemaLocationXsd).orElse(null);
                if (xsdToUse != null) {
                    logPane.log("Schema found via schemaLocation: " + xsdToUse.getFileName(), "ok");
                }
            }

            // 2b. Se non trovato via schemaLocation, cerca per nome file XML
            if (xsdToUse == null) {
                String xmlFileName = (currentDoc != null && currentDoc.getFilePath() != null)
                    ? currentDoc.getFileName() : null;

                if (xmlFileName != null) {
                    xsdToUse = xsdFolderMgr.findMatchingSchema(xmlFileName).orElse(null);
                    if (xsdToUse != null) {
                        logPane.log("Schema found by file name: " + xsdToUse.getFileName(), "ok");
                    }
                }
            }

            // 2c. Nessuna corrispondenza automatica → mostra selettore
            if (xsdToUse == null) {
                List<Path> available = xsdFolderMgr.listAvailableSchemas();
                if (available.isEmpty()) {
                    logPane.log("XSD folder is configured but contains no .xsd files", "warn");
                    return;
                }
                xsdToUse = showXsdPickerDialog(available);
                if (xsdToUse == null) return; // annullato dall'utente
            }
        }

        if (xsdToUse == null) {
            logPane.log("No XSD schema available. "
                + "Use 'Set XSD Folder' or 'Load Single XSD' from the XML menu.", "warn");
            return;
        }

        final Path finalXsd = xsdToUse;
        String content = editorPane.getText();
        statusBar.setStatus("Validating against " + finalXsd.getFileName() + "…");
        Thread.ofVirtual().start(() -> {
            XmlService.ValidationResult result = xmlService.validate(content, finalXsd);
            Platform.runLater(() -> {
                if (result.valid()) {
                    logPane.log("✔ Document is valid  [" + finalXsd.getFileName() + "]", "ok");
                    statusBar.setStatus("Validation: OK ✔");
                } else {
                    logPane.log("Validation failed against [" + finalXsd.getFileName() + "] — "
                        + result.errors().size() + " error(s):", "error");
                    result.errors().forEach(err ->
                        logPane.log("  [" + (err.fatal() ? "FATAL" : "ERROR") + "] "
                            + "Line " + err.line() + ", Col " + err.column() + ": " + err.message(), "error")
                    );
                    if (!result.errors().isEmpty())
                        editorPane.goToLine(result.errors().get(0).line());
                    statusBar.setStatus("Validation: " + result.errors().size() + " error(s) ✖");
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
        dialog.setTitle("Choose XSD Schema");
        dialog.setHeaderText("No schema automatically matches the current file.");
        dialog.setContentText("XSD schema to use:");

        return dialog.showAndWait()
            .map(chosen -> available.get(names.indexOf(chosen)))
            .orElse(null);
    }

    /** Ritorna la cartella XSD correntemente configurata (per la statusbar in MainStage). */
    public XsdFolderManager getXsdFolderManager() { return xsdFolderMgr; }

    /**
     * Estrae il nome del file XSD dall'attributo xsi:schemaLocation del testo XML.
     *
     * <p>Gestisce entrambe le forme:</p>
     * <ul>
     *   <li>{@code schemaLocation="namespace schema.xsd"} (coppia namespace+location)</li>
     *   <li>{@code noNamespaceSchemaLocation="schema.xsd"}</li>
     * </ul>
     *
     * @param xmlContent testo XML completo
     * @return solo il nome del file .xsd (es. "SEDANsfBlk.xsd"), o null se non trovato
     */
    private static String extractSchemaLocationFilename(String xmlContent) {
        if (xmlContent == null || xmlContent.isBlank()) return null;

        // Cerca solo nei primi 2000 caratteri (l'attributo è sempre nel tag radice)
        String head = xmlContent.length() > 2000 ? xmlContent.substring(0, 2000) : xmlContent;

        // 1. noNamespaceSchemaLocation="schema.xsd"
        java.util.regex.Matcher m1 = java.util.regex.Pattern
            .compile("noNamespaceSchemaLocation\\s*=\\s*[\"']([^\"']+)[\"']")
            .matcher(head);
        if (m1.find()) return filenameOnly(m1.group(1));

        // 2. schemaLocation="ns1 file1.xsd ns2 file2.xsd ..." — prende tutti i token pari (le location)
        java.util.regex.Matcher m2 = java.util.regex.Pattern
            .compile("(?:xsi:)?schemaLocation\\s*=\\s*[\"']([^\"']+)[\"']")
            .matcher(head);
        if (m2.find()) {
            String[] tokens = m2.group(1).trim().split("\\s+");
            // I token sono coppie (namespace, location); le location sono agli indici dispari
            for (int i = 1; i < tokens.length; i += 2) {
                String loc = tokens[i];
                if (loc.toLowerCase().endsWith(".xsd")) return filenameOnly(loc);
            }
            // Fallback: singolo token che finisce con .xsd (schemaLocation senza namespace)
            for (String t : tokens) {
                if (t.toLowerCase().endsWith(".xsd")) return filenameOnly(t);
            }
        }
        return null;
    }

    /** Estrae solo il nome file da un path o URL (es. "path/to/schema.xsd" → "schema.xsd"). */
    private static String filenameOnly(String pathOrUrl) {
        int slash = Math.max(pathOrUrl.lastIndexOf('/'), pathOrUrl.lastIndexOf('\\'));
        return slash >= 0 ? pathOrUrl.substring(slash + 1) : pathOrUrl;
    }

    // ──────────────────────────────────────────────
    // VAI A RIGA
    // ──────────────────────────────────────────────

    public void showGoToLine() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Go to Line");
        dialog.setHeaderText("Line (1 - " + editorPane.getLineCount() + "):");
        dialog.setContentText("Line:");
        dialog.showAndWait().ifPresent(val -> {
            try { editorPane.goToLine(Integer.parseInt(val.trim())); }
            catch (NumberFormatException e) { logPane.log("Invalid line number: " + val, "warn"); }
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
        catch (Exception e) { logPane.log("Invalid encoding: " + enc, "error"); }
    }

    // ──────────────────────────────────────────────
    // CONFERMA ABBANDONO MODIFICHE
    // ──────────────────────────────────────────────

    public boolean confirmDiscardChanges() {
        if (!editorPane.isModified()) return true;

        String fileName = (currentDoc != null && currentDoc.getFilePath() != null)
            ? currentDoc.getFileName() : "Untitled";

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Unsaved Changes");
        alert.setHeaderText("\"" + fileName + "\" has unsaved changes.");
        alert.setContentText("Do you want to save before continuing?");

        ButtonType btnSalva   = new ButtonType("Save");
        ButtonType btnScarta  = new ButtonType("Don't Save");
        ButtonType btnAnnulla = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(btnSalva, btnScarta, btnAnnulla);

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isEmpty() || result.get() == btnAnnulla) return false;
        // Se il "Save As…" viene annullato non si procede: le modifiche
        // andrebbero perse senza che l'utente lo abbia mai confermato
        if (result.get() == btnSalva) return saveFile();
        return true;
    }

    // ──────────────────────────────────────────────
    // ALBERO
    // ──────────────────────────────────────────────

    private void rebuildTree(String xmlText) {
        treePane.rebuildFrom(xmlText,
            () -> logPane.log("Tree unavailable (XML is not well-formed)", "warn"));
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
