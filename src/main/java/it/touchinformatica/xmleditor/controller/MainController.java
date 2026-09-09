package it.touchinformatica.xmleditor.controller;

import it.touchinformatica.xmleditor.model.XmlDocument;
import it.touchinformatica.xmleditor.service.XmlService;
import it.touchinformatica.xmleditor.util.I18n;
import it.touchinformatica.xmleditor.util.LastPositionManager;
import it.touchinformatica.xmleditor.util.RecentFilesManager;
import it.touchinformatica.xmleditor.util.XsdFolderManager;
import it.touchinformatica.xmleditor.view.*;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

import java.io.*;
import java.nio.charset.CharacterCodingException;
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

    /** Oltre questa dimensione l'albero si aggiorna solo su apertura e pretty print. */
    private static final int MAX_LIVE_TREE_CHARS = 5_000_000;

    private final PauseTransition treeDebounce = new PauseTransition(Duration.millis(700));

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
        editorPane.getCodeArea().caretPositionProperty().addListener((obs, old, pos) -> refreshStatus());

        // L'albero seguiva solo apertura e pretty print, così dopo qualche modifica
        // i nodi puntavano a righe sbagliate. Ora si ricostruisce a pausa di battitura:
        // PauseTransition vive sul thread FX, quindi non aggiunge thread da chiudere.
        treeDebounce.setOnFinished(e -> {
            String text = editorPane.getText();
            if (text.length() <= MAX_LIVE_TREE_CHARS) rebuildTree(text);
        });
        editorPane.getCodeArea().textProperty().addListener((obs, old, val) -> treeDebounce.playFromStart());
    }

    /** Aggiorna la statusbar con la posizione del caret; da richiamare anche al cambio tab. */
    public void refreshStatus() {
        int para = editorPane.getCodeArea().getCurrentParagraph();
        int col  = editorPane.getCodeArea().getCaretColumn();
        int tot  = editorPane.getLineCount();
        statusBar.setStatus(I18n.t("status.caret", para + 1, col + 1, tot,
            editorPane.getEncoding(), editorPane.getLineEnding()));
    }

    /** Ricarica i testi dipendenti dalla lingua dopo un cambio. */
    public void applyLanguage() {
        if (currentDoc == null || currentDoc.getFilePath() == null) {
            updateTabTitle.accept(I18n.t("doc.untitled"));
        }
        refreshStatus();
    }

    /** Finestra a cui agganciare i dialoghi, così non finiscono dietro l'applicazione. */
    private Window owner() {
        return editorPane.getScene() != null ? editorPane.getScene().getWindow() : null;
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
        updateTabTitle.accept(I18n.t("doc.untitled"));
        logPane.log(I18n.t("log.newDocument"), "info");
        statusBar.setStatus(I18n.t("status.ready"));
    }

    // ──────────────────────────────────────────────
    // APRI
    // ──────────────────────────────────────────────

    /** Apre il file rilevandone l'encoding dal BOM o dalla dichiarazione XML. */
    public void openPath(Path path) {
        openPath(path, null);
    }

    /**
     * @param charset encoding da usare, oppure null per rilevarlo dal file.
     *                Un file non leggibile con l'encoding richiesto viene riletto
     *                in ISO-8859-1, che accetta qualunque byte, con un avviso:
     *                meglio aprirlo con gli accenti sbagliati che non aprirlo.
     */
    public void openPath(Path path, Charset charset) {
        if (currentDoc != null && currentDoc.getFilePath() != null) {
            lastPosMgr.savePosition(currentDoc.getFilePath(),
                editorPane.getCodeArea().getCurrentParagraph() + 1);
        }
        statusBar.setStatus(I18n.t("log.loading", path.getFileName()));
        Thread.ofVirtual().start(() -> {
            try {
                String size = humanSize(Files.size(path));
                Charset requested = charset != null ? charset : detectCharset(path);

                String raw;
                String warning = null;
                try {
                    raw = Files.readString(path, requested);
                } catch (CharacterCodingException e) {
                    warning = I18n.t("log.encodingFallback", requested.name());
                    requested = StandardCharsets.ISO_8859_1;
                    raw = Files.readString(path, requested);
                }

                final Charset used = requested;
                final String note = warning;
                String le = detectLineEnding(raw);
                String content = normalizeLineEndings(raw);
                Platform.runLater(() -> {
                    currentDoc = new XmlDocument(path, content);
                    editorPane.setText(content);
                    editorPane.setEncoding(used.name());
                    editorPane.setLineEnding(le);
                    updateTabTitle.accept(path.getFileName().toString());
                    statusBar.setStatus(I18n.t("log.opened", path.getFileName(), size, used.name(), le));
                    logPane.log(I18n.t("log.loaded", path, size, used.name(), le), "ok");
                    if (note != null) logPane.log(note, "warn");
                    recentMgr.add(path);
                    refreshRecentMenu.accept(recentMgr.getRecentFiles());
                    rebuildTree(content);
                    int savedLine = lastPosMgr.loadPosition(path);
                    if (savedLine > 1) editorPane.goToLine(savedLine);
                });
            } catch (IOException e) {
                Platform.runLater(() -> logPane.log(I18n.t("log.openError", e.getMessage()), "error"));
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
    public boolean saveFile() { return saveFile(false); }

    /** @return false se l'utente ha annullato la scelta del file */
    public boolean saveFileAs() { return saveFileAs(false); }

    /**
     * Salva attendendo la fine della scrittura.
     * Da usare quando subito dopo l'applicazione chiude o ricarica il file:
     * la variante asincrona verrebbe interrotta dalla fine della JVM.
     *
     * @return true se il file è stato scritto davvero
     */
    public boolean saveFileBlocking() { return saveFile(true); }

    private boolean saveFile(boolean blocking) {
        if (currentDoc == null || currentDoc.getFilePath() == null) return saveFileAs(blocking);
        return writeToDisk(currentDoc.getFilePath(), blocking);
    }

    private boolean saveFileAs(boolean blocking) {
        FileChooser fc = new FileChooser();
        fc.setTitle(I18n.t("filechooser.save"));
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(I18n.t("filter.xml"), "*.xml"));
        File file = fc.showSaveDialog(owner());
        if (file == null) return false;
        return writeToDisk(file.toPath(), blocking);
    }

    /**
     * @param blocking se true scrive sul thread chiamante e riporta l'esito reale;
     *                 se false scrive in background e riporta solo che è partita
     */
    private boolean writeToDisk(Path path, boolean blocking) {
        String content  = restoreLineEndings(editorPane.getText(), editorPane.getLineEnding());
        Charset charset = editorPane.getCharset();

        if (blocking) {
            try {
                writeAtomically(path, content, charset);
                onSaved(path, content);
                return true;
            } catch (IOException e) {
                logPane.log(I18n.t("log.saveError", e.getMessage()), "error");
                return false;
            }
        }

        Thread.ofVirtual().start(() -> {
            try {
                writeAtomically(path, content, charset);
                Platform.runLater(() -> onSaved(path, content));
            } catch (IOException e) {
                Platform.runLater(() -> logPane.log(I18n.t("log.saveError", e.getMessage()), "error"));
            }
        });
        return true;
    }

    /** Aggiornamenti di stato e interfaccia dopo un salvataggio riuscito. */
    private void onSaved(Path path, String content) {
        if (currentDoc == null) currentDoc = new XmlDocument(path, content);
        else { currentDoc.setFilePath(path); currentDoc.markSaved(); }
        editorPane.markSaved();
        updateTabTitle.accept(path.getFileName().toString());
        statusBar.setStatus(I18n.t("log.saved", path));
        logPane.log(I18n.t("log.saved", path), "ok");
        recentMgr.add(path);
        refreshRecentMenu.accept(recentMgr.getRecentFiles());
    }

    /**
     * Scrive su un file temporaneo nella stessa cartella e poi lo sposta sul posto.
     * Scrivere direttamente sul file di destinazione lo troncherebbe subito: se la
     * scrittura si interrompe a metà, l'originale è perso.
     */
    private static void writeAtomically(Path path, String content, Charset charset) throws IOException {
        Path dir = path.toAbsolutePath().getParent();
        Path tmp;
        try {
            tmp = Files.createTempFile(dir, ".xmleditor-", ".tmp");
        } catch (IOException e) {
            // Cartella non scrivibile: meglio il salvataggio diretto che nessun salvataggio
            Files.writeString(path, content, charset);
            return;
        }
        try {
            Files.writeString(tmp, content, charset);
            try {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    // ──────────────────────────────────────────────
    // PRETTY PRINT
    // ──────────────────────────────────────────────

    public void prettyPrint() {
        String  content = editorPane.getText();
        Charset charset = editorPane.getCharset();
        statusBar.setStatus(I18n.t("log.prettyPrinting"));
        Thread.ofVirtual().start(() -> {
            try {
                String formatted = xmlService.prettyPrint(content, charset);
                Platform.runLater(() -> {
                    // replaceText e non setText: il pretty print resta annullabile
                    // e il documento risulta modificato finché non lo salvi
                    editorPane.replaceText(formatted);
                    statusBar.setStatus(I18n.t("log.prettyPrintDone"));
                    logPane.log(I18n.t("log.prettyPrintDone"), "ok");
                    rebuildTree(formatted);
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    logPane.log(I18n.t("log.xmlError", e.getMessage()), "error");
                    statusBar.setStatus(I18n.t("log.prettyPrintFailed"));
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
        dc.setTitle(I18n.t("filechooser.xsdFolder"));
        xsdFolderMgr.getXsdFolder().ifPresent(f -> dc.setInitialDirectory(f.toFile()));
        File dir = dc.showDialog(owner());
        if (dir == null) return;
        xsdFolderMgr.setXsdFolder(dir.toPath());
        int count = xsdFolderMgr.listAvailableSchemas().size();
        logPane.log(I18n.t("log.xsdFolderSet", dir.getAbsolutePath(), count), "ok");
        statusBar.setStatus(I18n.t("status.xsdFolder", dir.getName(), count));
    }

    /**
     * Carica un singolo XSD manualmente (override puntuale rispetto alla cartella).
     * Il file selezionato ha priorità sulla cartella per la sessione corrente.
     */
    public void loadXsd() {
        FileChooser fc = new FileChooser();
        fc.setTitle(I18n.t("filechooser.loadXsd"));
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(I18n.t("filter.xsd"), "*.xsd"));
        xsdFolderMgr.getXsdFolder().ifPresent(f -> fc.setInitialDirectory(f.toFile()));
        File file = fc.showOpenDialog(owner());
        if (file == null) return;
        currentXsdPath = file.toPath();
        logPane.log(I18n.t("log.xsdLoaded", file.getName()), "ok");
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
                    logPane.log(I18n.t("log.schemaBySchemaLocation", xsdToUse.getFileName()), "ok");
                }
            }

            // 2b. Se non trovato via schemaLocation, cerca per nome file XML
            if (xsdToUse == null) {
                String xmlFileName = (currentDoc != null && currentDoc.getFilePath() != null)
                    ? currentDoc.getFileName() : null;

                if (xmlFileName != null) {
                    xsdToUse = xsdFolderMgr.findMatchingSchema(xmlFileName).orElse(null);
                    if (xsdToUse != null) {
                        logPane.log(I18n.t("log.schemaByFileName", xsdToUse.getFileName()), "ok");
                    }
                }
            }

            // 2c. Nessuna corrispondenza automatica → mostra selettore
            if (xsdToUse == null) {
                List<Path> available = xsdFolderMgr.listAvailableSchemas();
                if (available.isEmpty()) {
                    logPane.log(I18n.t("log.xsdFolderEmpty"), "warn");
                    return;
                }
                xsdToUse = showXsdPickerDialog(available);
                if (xsdToUse == null) return; // annullato dall'utente
            }
        }

        if (xsdToUse == null) {
            logPane.log(I18n.t("log.noSchema"), "warn");
            return;
        }

        final Path finalXsd = xsdToUse;
        String content = editorPane.getText();
        statusBar.setStatus(I18n.t("log.validating", finalXsd.getFileName()));
        Thread.ofVirtual().start(() -> {
            XmlService.ValidationResult result = xmlService.validate(content, finalXsd);
            Platform.runLater(() -> {
                if (result.valid()) {
                    logPane.log(I18n.t("log.valid", finalXsd.getFileName()), "ok");
                    statusBar.setStatus(I18n.t("status.validationOk"));
                } else {
                    logPane.log(I18n.t("log.validationFailed",
                        finalXsd.getFileName(), result.errors().size()), "error");
                    result.errors().forEach(err ->
                        logPane.log(I18n.t("log.validationError",
                            I18n.t(err.fatal() ? "error.fatal" : "error.error"),
                            err.line(), err.column(), err.message()), "error")
                    );
                    if (!result.errors().isEmpty())
                        editorPane.goToLine(result.errors().get(0).line());
                    statusBar.setStatus(I18n.t("status.validationFailed", result.errors().size()));
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
        dialog.setTitle(I18n.t("dialog.xsdPicker.title"));
        dialog.setHeaderText(I18n.t("dialog.xsdPicker.header"));
        dialog.setContentText(I18n.t("dialog.xsdPicker.content"));

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
        dialog.setTitle(I18n.t("dialog.goToLine.title"));
        dialog.setHeaderText(I18n.t("dialog.goToLine.header", editorPane.getLineCount()));
        dialog.setContentText(I18n.t("dialog.goToLine.content"));
        dialog.showAndWait().ifPresent(val -> {
            try { editorPane.goToLine(Integer.parseInt(val.trim())); }
            catch (NumberFormatException e) { logPane.log(I18n.t("log.invalidLine", val), "warn"); }
        });
    }

    // ──────────────────────────────────────────────
    // ENCODING
    // ──────────────────────────────────────────────

    public void reloadWithEncoding(String enc) {
        if (currentDoc == null || currentDoc.getFilePath() == null) {
            editorPane.setEncoding(enc);
            statusBar.setStatus(I18n.t("menu.view.encoding") + ": " + enc);
            return;
        }
        // confirmDiscardChanges() ora attende la fine della scrittura, quindi la
        // rilettura non può più incrociare un salvataggio ancora in corso
        if (!confirmDiscardChanges()) return;
        try { openPath(currentDoc.getFilePath(), Charset.forName(enc)); }
        catch (Exception e) { logPane.log(I18n.t("log.invalidEncoding", enc), "error"); }
    }

    // ──────────────────────────────────────────────
    // CONFERMA ABBANDONO MODIFICHE
    // ──────────────────────────────────────────────

    public boolean confirmDiscardChanges() {
        if (!editorPane.isModified()) return true;

        String fileName = (currentDoc != null && currentDoc.getFilePath() != null)
            ? currentDoc.getFileName() : I18n.t("doc.untitled");

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(I18n.t("dialog.unsaved.title"));
        alert.setHeaderText(I18n.t("dialog.unsaved.header", fileName));
        alert.setContentText(I18n.t("dialog.unsaved.content"));

        ButtonType btnSalva   = new ButtonType(I18n.t("dialog.unsaved.save"));
        ButtonType btnScarta  = new ButtonType(I18n.t("dialog.unsaved.dontSave"));
        ButtonType btnAnnulla = new ButtonType(I18n.t("dialog.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(btnSalva, btnScarta, btnAnnulla);

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isEmpty() || result.get() == btnAnnulla) return false;
        // Se il "Save As…" viene annullato non si procede: le modifiche
        // andrebbero perse senza che l'utente lo abbia mai confermato
        if (result.get() == btnSalva) return saveFileBlocking();
        return true;
    }

    // ──────────────────────────────────────────────
    // ALBERO
    // ──────────────────────────────────────────────

    private void rebuildTree(String xmlText) {
        treePane.rebuildFrom(xmlText,
            () -> logPane.log(I18n.t("log.treeUnavailable"), "warn"));
    }

    // ──────────────────────────────────────────────
    // LINE ENDINGS
    // ──────────────────────────────────────────────

    /**
     * Ricava l'encoding del file da BOM e dichiarazione XML.
     *
     * <p>Un documento XML dichiara da sé come è codificato: leggerlo sempre in UTF-8
     * faceva fallire l'apertura dei file ISO-8859-1 con un {@code MalformedInputException}
     * dal messaggio incomprensibile.</p>
     *
     * @return l'encoding dichiarato, UTF-8 se il file non dice nulla
     */
    private static Charset detectCharset(Path path) throws IOException {
        byte[] head = new byte[1024];
        int read;
        try (InputStream in = Files.newInputStream(path)) {
            read = in.readNBytes(head, 0, head.length);
        }
        if (read <= 0) return StandardCharsets.UTF_8;

        // 1. BOM: ha la precedenza sulla dichiarazione
        if (read >= 3 && (head[0] & 0xFF) == 0xEF && (head[1] & 0xFF) == 0xBB && (head[2] & 0xFF) == 0xBF)
            return StandardCharsets.UTF_8;
        if (read >= 2 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xFE)
            return StandardCharsets.UTF_16LE;
        if (read >= 2 && (head[0] & 0xFF) == 0xFE && (head[1] & 0xFF) == 0xFF)
            return StandardCharsets.UTF_16BE;

        // 2. <?xml … encoding="…"?> — il prologo è ASCII in tutti gli encoding gestiti qui
        String declaration = new String(head, 0, read, StandardCharsets.ISO_8859_1);
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("<\\?xml[^>]*encoding\\s*=\\s*[\"']([^\"']+)[\"']")
            .matcher(declaration);
        if (m.find()) {
            try {
                return Charset.forName(m.group(1).trim());
            } catch (Exception ignored) {
                // encoding dichiarato ma sconosciuto: si prosegue con UTF-8
            }
        }
        return StandardCharsets.UTF_8;
    }

    private static String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

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
