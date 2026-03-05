package it.touchinformatica.xmleditor.view;

import it.touchinformatica.xmleditor.service.XmlService;
import it.touchinformatica.xmleditor.util.RecentFilesManager;
import it.touchinformatica.xmleditor.util.XsdFolderManager;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.event.Event;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * MainStage — finestra principale con TabPane.
 * Gestisce creazione/chiusura tab e delega le operazioni alla sessione attiva.
 */
public class MainStage {

    private final Stage            stage;
    private final XmlService       xmlService;
    private final RecentFilesManager recentMgr;
    private final XsdFolderManager xsdFolderMgr;
    private final TabPane          tabPane;
    private final StatusBar        statusBar;

    private Menu menuRecenti;

    public MainStage(Stage stage) {
        this(stage, List.of());
    }

    public MainStage(Stage stage, List<Path> initialFiles) {
        this.stage         = stage;
        this.xmlService    = new XmlService();
        this.recentMgr     = new RecentFilesManager();
        this.xsdFolderMgr  = new XsdFolderManager();
        this.statusBar     = new StatusBar();
        this.tabPane       = new TabPane();

        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);

        buildScene();
        setupAccelerators();

        if (initialFiles.isEmpty()) {
            newTab();
        } else {
            for (Path p : initialFiles) {
                EditorSession s = newTab();
                s.openPath(p);
            }
        }
    }

    // ──────────────────────────────────────────────
    // GESTIONE TAB
    // ──────────────────────────────────────────────

    /** Crea un nuovo tab vuoto e lo seleziona */
    public EditorSession newTab() {
        EditorSession session = new EditorSession(
            xmlService, recentMgr, xsdFolderMgr, this::refreshRecentMenu, statusBar
        );
        tabPane.getTabs().add(session.getTab());
        tabPane.getSelectionModel().select(session.getTab());

        // Aggiorna titolo finestra al cambio tab
        session.getTab().textProperty().addListener((obs, old, text) -> {
            if (tabPane.getSelectionModel().getSelectedItem() == session.getTab())
                updateWindowTitle(text);
        });
        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            if (sel == session.getTab()) updateWindowTitle(session.getTab().getText());
        });

        updateWindowTitle(session.getTab().getText());
        return session;
    }

    /** Ritorna la sessione del tab attivo, o null se non c'è nessun tab */
    public EditorSession activeSession() {
        Tab selected = tabPane.getSelectionModel().getSelectedItem();
        if (selected == null) return null;
        Object ud = selected.getUserData();
        return ud instanceof EditorSession s ? s : null;
    }

    /**
     * Apre un file:
     * - Se il tab corrente è vuoto e non modificato → apre lì
     * - Se il tab corrente ha modifiche → chiede nuovo tab o sostituire
     * - Altrimenti apre nel tab corrente
     */
    public void openFile() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Apri file XML");
        fc.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("File XML/XSD", "*.xml", "*.xsd"),
            new FileChooser.ExtensionFilter("Tutti i file", "*.*")
        );
        List<File> files = fc.showOpenMultipleDialog(stage);
        if (files == null || files.isEmpty()) return;

        for (int i = 0; i < files.size(); i++) {
            Path path = files.get(i).toPath();
            if (i == 0) {
                openPathIntelligent(path);
            } else {
                // File aggiuntivi sempre in nuovo tab
                EditorSession s = newTab();
                s.openPath(path);
            }
        }
    }

    private void openPathIntelligent(Path path) {
        EditorSession current = getOrCreateActiveSession();

        boolean isEmpty    = current.getEditorPane().getText().isBlank();
        boolean isModified = current.isModified();

        if (!isEmpty && isModified) {
            // Chiedi: nuovo tab o sostituire?
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Apri file");
            alert.setHeaderText("Il tab corrente ha modifiche non salvate.");
            alert.setContentText("Vuoi aprire il file in un nuovo tab o sostituire quello corrente?");
            ButtonType btnNuovo     = new ButtonType("Nuovo tab");
            ButtonType btnSostituisci = new ButtonType("Sostituisci");
            ButtonType btnAnnulla   = new ButtonType("Annulla", ButtonBar.ButtonData.CANCEL_CLOSE);
            alert.getButtonTypes().setAll(btnNuovo, btnSostituisci, btnAnnulla);

            Optional<ButtonType> result = alert.showAndWait();
            if (result.isEmpty() || result.get() == btnAnnulla) return;

            if (result.get() == btnNuovo) {
                EditorSession s = newTab();
                s.openPath(path);
            } else {
                current.openPath(path);
            }
        } else if (!isEmpty && !isModified) {
            // Tab occupato ma salvato → nuovo tab
            EditorSession s = newTab();
            s.openPath(path);
        } else {
            // Tab vuoto → apri qui
            current.openPath(path);
        }
    }

    public void openPathFromRecent(Path path) {
        openPathIntelligent(path);
    }

    private EditorSession getOrCreateActiveSession() {
        EditorSession s = activeSession();
        return s != null ? s : newTab();
    }

    /** Chiude il tab attivo (con conferma se modificato) */
    public void closeCurrentTab() {
        Tab selected = tabPane.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        Object ud = selected.getUserData();
        if (ud instanceof EditorSession s) {
            s.saveCurrentPosition();
            if (s.isModified() && !s.getController().confirmDiscardChanges()) return;
        }
        tabPane.getTabs().remove(selected);
        if (tabPane.getTabs().isEmpty()) newTab();
    }

    // ──────────────────────────────────────────────
    // DELEGA ALL'ACTIVE SESSION
    // ──────────────────────────────────────────────

    private void withActive(java.util.function.Consumer<EditorSession> action) {
        EditorSession s = getOrCreateActiveSession();
        action.accept(s);
    }

    // ──────────────────────────────────────────────
    // SCENE
    // ──────────────────────────────────────────────

    private void buildScene() {
        MenuBar menuBar = buildMenuBar();
        ToolBar toolBar = buildToolBar();

        BorderPane root = new BorderPane();
        root.setTop(new VBox(menuBar, toolBar));
        root.setCenter(tabPane);
        root.setBottom(statusBar);

        Scene scene = new Scene(root, 1400, 900);
        scene.getStylesheets().add(
            getClass().getResource("/it/touchinformatica/xmleditor/css/editor.css").toExternalForm()
        );

        stage.setScene(scene);
        stage.setTitle("XmlEditor — Touch Informatica");

        stage.setOnCloseRequest(e -> {
            for (Tab tab : tabPane.getTabs()) {
                Object ud = tab.getUserData();
                if (ud instanceof EditorSession s) {
                    s.saveCurrentPosition();
                    if (s.isModified()) {
                        tabPane.getSelectionModel().select(tab);
                        if (!s.getController().confirmDiscardChanges()) {
                            e.consume();
                            return;
                        }
                    }
                }
            }
        });
    }

    // ──────────────────────────────────────────────
    // MENU BAR
    // ──────────────────────────────────────────────

    private MenuBar buildMenuBar() {
        // ── File ──
        MenuItem miNuovoTab = menuItem("Nuovo tab",       "Ctrl+T",           () -> newTab());
        MenuItem miNuovo    = menuItem("Nuovo documento", "Ctrl+N",           () -> withActive(s -> s.getController().newFile()));
        MenuItem miApri     = menuItem("Apri…",           "Ctrl+O",           this::openFile);
        MenuItem miSalva    = menuItem("Salva",           "Ctrl+S",           () -> withActive(s -> s.getController().saveFile()));
        MenuItem miSalvaAs  = menuItem("Salva come…",     "Ctrl+Shift+S",     () -> withActive(s -> s.getController().saveFileAs()));
        menuRecenti         = new Menu("File recenti");
        MenuItem miChiudiTab= menuItem("Chiudi tab",      "Ctrl+W",           this::closeCurrentTab);
        MenuItem miEsci     = menuItem("Esci",            "Alt+F4",           () -> stage.fireEvent(new javafx.stage.WindowEvent(stage, javafx.stage.WindowEvent.WINDOW_CLOSE_REQUEST)));

        Menu menuFile = new Menu("File");
        menuFile.getItems().addAll(
            miNuovoTab, miNuovo, miApri, new SeparatorMenuItem(),
            miSalva, miSalvaAs, new SeparatorMenuItem(),
            menuRecenti, new SeparatorMenuItem(),
            miChiudiTab, miEsci
        );

        // ── Modifica ──
        MenuItem miUndo     = menuItem("Annulla",          "Ctrl+Z",           () -> withActive(s -> s.getEditorPane().undo()));
        MenuItem miRedo     = menuItem("Ripeti",           "Ctrl+Shift+Z",     () -> withActive(s -> s.getEditorPane().redo()));
        MenuItem miTaglia   = menuItem("Taglia",           "Ctrl+X",           () -> withActive(s -> s.getEditorPane().cut()));
        MenuItem miCopia    = menuItem("Copia",            "Ctrl+C",           () -> withActive(s -> s.getEditorPane().copy()));
        MenuItem miIncolla  = menuItem("Incolla",          "Ctrl+V",           () -> withActive(s -> s.getEditorPane().paste()));
        MenuItem miSelTutto = menuItem("Seleziona tutto",  "Ctrl+A",           () -> withActive(s -> s.getEditorPane().selectAll()));
        MenuItem miVaiRiga  = menuItem("Vai a riga…",      "Ctrl+G",           () -> withActive(s -> s.getController().showGoToLine()));
        MenuItem miCerca    = menuItem("Cerca…",           "Ctrl+F",           () -> withActive(s -> s.getController().toggleSearch()));

        Menu menuModifica = new Menu("Modifica");
        menuModifica.getItems().addAll(
            miUndo, miRedo, new SeparatorMenuItem(),
            miTaglia, miCopia, miIncolla, new SeparatorMenuItem(),
            miSelTutto, new SeparatorMenuItem(),
            miCerca, miVaiRiga
        );

        // ── Visualizza ──
        MenuItem miTabPrev  = menuItem("Tab precedente",   "Ctrl+Shift+Tab",   this::selectPrevTab);
        MenuItem miTabNext  = menuItem("Tab successivo",   "Ctrl+Tab",         this::selectNextTab);
        CheckMenuItem miWrap = new CheckMenuItem("A capo automatico");
        miWrap.setOnAction(e -> withActive(s -> s.getEditorPane().setWordWrap(miWrap.isSelected())));

        Menu menuEncoding = new Menu("Encoding");
        for (String enc : List.of("UTF-8", "ISO-8859-1", "UTF-16", "Windows-1252")) {
            MenuItem mi = new MenuItem(enc);
            mi.setOnAction(e -> withActive(s -> s.getController().reloadWithEncoding(enc)));
            menuEncoding.getItems().add(mi);
        }

        Menu menuVisualizza = new Menu("Visualizza");
        menuVisualizza.getItems().addAll(miTabPrev, miTabNext, new SeparatorMenuItem(), miWrap, new SeparatorMenuItem(), menuEncoding);

        // ── XML ──
        MenuItem miPretty     = menuItem("Pretty Print",        "Ctrl+P",  () -> withActive(s -> s.getController().prettyPrint()));
        MenuItem miXsdFolder  = menuItem("Imposta cartella XSD…", null,    () -> withActive(s -> s.getController().setXsdFolder()));
        MenuItem miXsd        = menuItem("Carica XSD singolo…",  null,      () -> withActive(s -> s.getController().loadXsd()));
        MenuItem miValida     = menuItem("Valida XSD",           "Ctrl+E",  () -> withActive(s -> s.getController().validate()));
        MenuItem miXsdInfo    = menuItem("Info cartella XSD",    null,      this::showXsdFolderInfo);

        Menu menuXml = new Menu("XML");
        menuXml.getItems().addAll(
            miPretty,
            new SeparatorMenuItem(),
            miXsdFolder, miXsdInfo,
            new SeparatorMenuItem(),
            miXsd, miValida
        );

        refreshRecentMenu(recentMgr.getRecentFiles());
        updateXsdFolderStatus();
        return new MenuBar(menuFile, menuModifica, menuVisualizza, menuXml);
    }

    // ──────────────────────────────────────────────
    // TOOLBAR
    // ──────────────────────────────────────────────

    private ToolBar buildToolBar() {
        ToolBar bar = new ToolBar(
            toolBtn("📄+ Tab",         this::newTab,                                    "Nuovo tab (Ctrl+T)"),
            toolBtn("📂 Apri",         this::openFile,                                  "Apri (Ctrl+O)"),
            toolBtn("💾 Salva",        () -> withActive(s -> s.getController().saveFile()), "Salva (Ctrl+S)"),
            new Separator(Orientation.VERTICAL),
            toolBtn("↩ Annulla",       () -> withActive(s -> s.getEditorPane().undo()),  "Annulla (Ctrl+Z)"),
            toolBtn("↪ Ripeti",        () -> withActive(s -> s.getEditorPane().redo()),  "Ripeti (Ctrl+Shift+Z)"),
            new Separator(Orientation.VERTICAL),
            toolBtn("⬡ Pretty Print",  () -> withActive(s -> s.getController().prettyPrint()), "Pretty Print (Ctrl+P)"),
            toolBtn("📋 Carica XSD",   () -> withActive(s -> s.getController().loadXsd()),      "Carica schema XSD"),
            toolBtn("✔ Valida",        () -> withActive(s -> s.getController().validate()),      "Valida XSD (Ctrl+E)"),
            new Separator(Orientation.VERTICAL),
            toolBtn("🔍 Cerca",        () -> withActive(s -> s.getController().toggleSearch()),  "Cerca (Ctrl+F)"),
            toolBtn("# Riga",          () -> withActive(s -> s.getController().showGoToLine()),   "Vai a riga (Ctrl+G)")
        );
        bar.getStyleClass().add("main-toolbar");
        return bar;
    }

    private Button toolBtn(String text, Runnable action, String tooltip) {
        Button b = new Button(text);
        b.setTooltip(new Tooltip(tooltip));
        b.setOnAction(e -> action.run());
        b.getStyleClass().add("toolbar-btn");
        return b;
    }

    // ──────────────────────────────────────────────
    // NAVIGAZIONE TAB
    // ──────────────────────────────────────────────

    private void selectNextTab() {
        int idx = tabPane.getSelectionModel().getSelectedIndex();
        int next = (idx + 1) % tabPane.getTabs().size();
        tabPane.getSelectionModel().select(next);
    }

    private void selectPrevTab() {
        int idx = tabPane.getSelectionModel().getSelectedIndex();
        int prev = (idx - 1 + tabPane.getTabs().size()) % tabPane.getTabs().size();
        tabPane.getSelectionModel().select(prev);
    }

    // ──────────────────────────────────────────────
    // ACCELERATORI
    // ──────────────────────────────────────────────

    private void setupAccelerators() {
        var acc = stage.getScene().getAccelerators();
        acc.put(new KeyCodeCombination(KeyCode.T, KeyCombination.CONTROL_DOWN),                             () -> newTab());
        acc.put(new KeyCodeCombination(KeyCode.W, KeyCombination.CONTROL_DOWN),                             this::closeCurrentTab);
        acc.put(new KeyCodeCombination(KeyCode.N, KeyCombination.CONTROL_DOWN),                             () -> withActive(s -> s.getController().newFile()));
        acc.put(new KeyCodeCombination(KeyCode.O, KeyCombination.CONTROL_DOWN),                             this::openFile);
        acc.put(new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN),                             () -> withActive(s -> s.getController().saveFile()));
        acc.put(new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN),  () -> withActive(s -> s.getController().saveFileAs()));
        acc.put(new KeyCodeCombination(KeyCode.P, KeyCombination.CONTROL_DOWN),                             () -> withActive(s -> s.getController().prettyPrint()));
        acc.put(new KeyCodeCombination(KeyCode.E, KeyCombination.CONTROL_DOWN),                             () -> withActive(s -> s.getController().validate()));
        acc.put(new KeyCodeCombination(KeyCode.F, KeyCombination.CONTROL_DOWN),                             () -> withActive(s -> s.getController().toggleSearch()));
        acc.put(new KeyCodeCombination(KeyCode.G, KeyCombination.CONTROL_DOWN),                             () -> withActive(s -> s.getController().showGoToLine()));
        acc.put(new KeyCodeCombination(KeyCode.A, KeyCombination.CONTROL_DOWN),                             () -> withActive(s -> s.getEditorPane().selectAll()));
        acc.put(new KeyCodeCombination(KeyCode.TAB, KeyCombination.CONTROL_DOWN),                           this::selectNextTab);
        acc.put(new KeyCodeCombination(KeyCode.TAB, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN),this::selectPrevTab);
    }

    // ──────────────────────────────────────────────
    // XSD FOLDER INFO
    // ──────────────────────────────────────────────

    private void showXsdFolderInfo() {
        if (!xsdFolderMgr.isConfigured()) {
            Alert a = new Alert(Alert.AlertType.INFORMATION);
            a.setTitle("Cartella XSD");
            a.setHeaderText("Nessuna cartella XSD configurata");
            a.setContentText("Usa 'Imposta cartella XSD…' dal menu XML per configurarla.");
            a.showAndWait();
            return;
        }
        var schemas = xsdFolderMgr.listAvailableSchemas();
        String list = schemas.isEmpty()
            ? "(nessun file .xsd trovato)"
            : schemas.stream()
                .map(p -> "  • " + p.getFileName())
                .reduce("", (a, b) -> a + "\n" + b);

        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("Cartella XSD");
        a.setHeaderText("Cartella: " + xsdFolderMgr.getXsdFolder().orElseThrow());
        a.setContentText(schemas.size() + " schema/i disponibili:" + list);
        a.getDialogPane().setPrefWidth(520);
        a.showAndWait();
    }

    private void updateXsdFolderStatus() {
        xsdFolderMgr.getXsdFolder().ifPresent(f -> {
            int n = xsdFolderMgr.listAvailableSchemas().size();
            statusBar.setStatus("Cartella XSD: " + f.getFileName() + " (" + n + " XSD)");
        });
    }

    // ──────────────────────────────────────────────
    // FILE RECENTI
    // ──────────────────────────────────────────────

    public void refreshRecentMenu(List<Path> recents) {
        if (menuRecenti == null) return;
        menuRecenti.getItems().clear();
        if (recents.isEmpty()) {
            menuRecenti.getItems().add(new MenuItem("(nessuno)"));
            return;
        }
        for (Path p : recents) {
            MenuItem mi = new MenuItem(p.getFileName() + "   " + p.getParent());
            mi.setOnAction(e -> openPathFromRecent(p));
            menuRecenti.getItems().add(mi);
        }
        menuRecenti.getItems().add(new SeparatorMenuItem());
        MenuItem clear = new MenuItem("Cancella lista");
        clear.setOnAction(e -> { recentMgr.clear(); refreshRecentMenu(recentMgr.getRecentFiles()); });
        menuRecenti.getItems().add(clear);
    }

    // ──────────────────────────────────────────────
    // HELPERS
    // ──────────────────────────────────────────────

    private void updateWindowTitle(String tabTitle) {
        String clean = tabTitle.startsWith("* ") ? tabTitle.substring(2) : tabTitle;
        stage.setTitle("XmlEditor — " + clean);
    }

    private MenuItem menuItem(String label, String accel, Runnable action) {
        MenuItem mi = new MenuItem(label);
        if (accel != null) mi.setAccelerator(KeyCombination.keyCombination(accel));
        mi.setOnAction(e -> action.run());
        return mi;
    }

    public void show() { stage.show(); }
}
