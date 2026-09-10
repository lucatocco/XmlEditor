package it.touchinformatica.xmleditor.view;

import it.touchinformatica.xmleditor.service.XmlService;
import it.touchinformatica.xmleditor.util.AppInfo;
import it.touchinformatica.xmleditor.util.I18n;
import it.touchinformatica.xmleditor.util.UpdateChecker;
import it.touchinformatica.xmleditor.util.RecentFilesManager;
import it.touchinformatica.xmleditor.util.XsdFolderManager;
import javafx.application.Platform;
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
    private final javafx.application.HostServices hostServices;

    private Menu menuRecenti;
    private CheckMenuItem miWrap;   // stato globale, va riallineato al tab attivo
    private BorderPane root;        // serve per rifare menu e toolbar al cambio lingua

    public MainStage(Stage stage) {
        this(stage, List.of(), null);
    }

    public MainStage(Stage stage, List<Path> initialFiles) {
        this(stage, initialFiles, null);
    }

    public MainStage(Stage stage, List<Path> initialFiles, javafx.application.HostServices hostServices) {
        this.stage         = stage;
        this.hostServices  = hostServices;
        this.xmlService    = new XmlService();
        this.recentMgr     = new RecentFilesManager();
        this.xsdFolderMgr  = new XsdFolderManager();
        this.statusBar     = new StatusBar();
        this.tabPane       = new TabPane();

        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);

        // Un unico listener per la finestra: registrarne uno per ogni tab creato
        // ne lasciava in giro anche dopo la chiusura del tab.
        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            if (sel == null) return;
            updateWindowTitle(sel.getText());
            if (sel.getUserData() instanceof EditorSession s) {
                if (miWrap != null) miWrap.setSelected(s.getEditorPane().isWordWrap());
                s.getController().refreshStatus();
            }
        });

        // L'applicazione deve avere sempre almeno un tab: chiudendo l'ultimo con la
        // "X" la barra restava vuota e Ctrl+Tab divideva per zero.
        tabPane.getTabs().addListener((javafx.collections.ListChangeListener<Tab>) change -> {
            while (change.next()) {
                for (Tab removed : change.getRemoved()) {
                    if (removed.getUserData() instanceof EditorSession s) s.dispose();
                }
            }
            if (tabPane.getTabs().isEmpty()) Platform.runLater(this::newTab);
        });

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

        // Il titolo del tab cambia con l'asterisco delle modifiche: questo listener
        // vive quanto il tab, quindi non serve rimuoverlo.
        session.getTab().textProperty().addListener((obs, old, text) -> {
            if (tabPane.getSelectionModel().getSelectedItem() == session.getTab())
                updateWindowTitle(text);
        });

        // Il nuovo tab eredita l'impostazione di a capo automatico attualmente scelta
        if (miWrap != null) session.getEditorPane().setWordWrap(miWrap.isSelected());

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
        fc.setTitle(I18n.t("filechooser.open"));
        fc.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter(I18n.t("filter.xmlXsdTxt"), "*.xml", "*.xsd", "*.txt"),
            new FileChooser.ExtensionFilter(I18n.t("filter.allFiles"), "*.*")
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
            alert.setTitle(I18n.t("dialog.open.title"));
            alert.setHeaderText(I18n.t("dialog.open.header"));
            alert.setContentText(I18n.t("dialog.open.content"));
            ButtonType btnNuovo     = new ButtonType(I18n.t("dialog.open.newTab"));
            ButtonType btnSostituisci = new ButtonType(I18n.t("dialog.open.replace"));
            ButtonType btnAnnulla   = new ButtonType(I18n.t("dialog.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
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
        tabPane.getTabs().remove(selected);   // il listener ricrea un tab se resta vuoto
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

        root = new BorderPane();
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
        MenuItem miNuovoTab = menuItem(I18n.t("menu.file.newTab"), "Ctrl+T",           () -> newTab());
        MenuItem miNuovo    = menuItem(I18n.t("menu.file.newDocument"), "Ctrl+N",           () -> withActive(s -> s.getController().newFile()));
        MenuItem miApri     = menuItem(I18n.t("menu.file.open"), "Ctrl+O",           this::openFile);
        MenuItem miSalva    = menuItem(I18n.t("menu.file.save"), "Ctrl+S",           () -> withActive(s -> s.getController().saveFile()));
        MenuItem miSalvaAs  = menuItem(I18n.t("menu.file.saveAs"), "Ctrl+Shift+S",     () -> withActive(s -> s.getController().saveFileAs()));
        menuRecenti         = new Menu(I18n.t("menu.file.recent"));
        MenuItem miChiudiTab= menuItem(I18n.t("menu.file.closeTab"), "Ctrl+W",           this::closeCurrentTab);
        MenuItem miEsci     = menuItem(I18n.t("menu.file.exit"), "Alt+F4",           () -> stage.fireEvent(new javafx.stage.WindowEvent(stage, javafx.stage.WindowEvent.WINDOW_CLOSE_REQUEST)));

        Menu menuFile = new Menu(I18n.t("menu.file"));
        menuFile.getItems().addAll(
            miNuovoTab, miNuovo, miApri, new SeparatorMenuItem(),
            miSalva, miSalvaAs, new SeparatorMenuItem(),
            menuRecenti, new SeparatorMenuItem(),
            miChiudiTab, miEsci
        );

        // ── Modifica ──
        MenuItem miUndo     = menuItem(I18n.t("menu.edit.undo"), "Ctrl+Z",           () -> withActive(s -> s.getEditorPane().undo()));
        MenuItem miRedo     = menuItem(I18n.t("menu.edit.redo"), "Ctrl+Shift+Z",     () -> withActive(s -> s.getEditorPane().redo()));
        MenuItem miTaglia   = menuItem(I18n.t("menu.edit.cut"), "Ctrl+X",           () -> withActive(s -> s.getEditorPane().cut()));
        MenuItem miCopia    = menuItem(I18n.t("menu.edit.copy"), "Ctrl+C",           () -> withActive(s -> s.getEditorPane().copy()));
        MenuItem miIncolla  = menuItem(I18n.t("menu.edit.paste"), "Ctrl+V",           () -> withActive(s -> s.getEditorPane().paste()));
        MenuItem miSelTutto = menuItem(I18n.t("menu.edit.selectAll"), "Ctrl+A",           () -> withActive(s -> s.getEditorPane().selectAll()));
        MenuItem miVaiRiga  = menuItem(I18n.t("menu.edit.goToLine"), "Ctrl+G",           () -> withActive(s -> s.getController().showGoToLine()));
        MenuItem miCerca    = menuItem(I18n.t("menu.edit.find"), "Ctrl+F",           () -> withActive(s -> s.getController().toggleSearch()));

        Menu menuModifica = new Menu(I18n.t("menu.edit"));
        menuModifica.getItems().addAll(
            miUndo, miRedo, new SeparatorMenuItem(),
            miTaglia, miCopia, miIncolla, new SeparatorMenuItem(),
            miSelTutto, new SeparatorMenuItem(),
            miCerca, miVaiRiga
        );

        // ── Visualizza ──
        MenuItem miTabPrev  = menuItem(I18n.t("menu.view.prevTab"), "Ctrl+Shift+Tab",   this::selectPrevTab);
        MenuItem miTabNext  = menuItem(I18n.t("menu.view.nextTab"), "Ctrl+Tab",         this::selectNextTab);
        miWrap = new CheckMenuItem(I18n.t("menu.view.wordWrap"));
        EditorSession activeForWrap = activeSession();
        if (activeForWrap != null) miWrap.setSelected(activeForWrap.getEditorPane().isWordWrap());
        miWrap.setOnAction(e -> withActive(s -> s.getEditorPane().setWordWrap(miWrap.isSelected())));

        Menu menuEncoding = new Menu(I18n.t("menu.view.encoding"));
        for (String enc : List.of("UTF-8", "ISO-8859-1", "UTF-16", "Windows-1252")) {
            MenuItem mi = new MenuItem(enc);
            mi.setOnAction(e -> withActive(s -> s.getController().reloadWithEncoding(enc)));
            menuEncoding.getItems().add(mi);
        }

        Menu menuVisualizza = new Menu(I18n.t("menu.view"));
        menuVisualizza.getItems().addAll(miTabPrev, miTabNext, new SeparatorMenuItem(), miWrap, new SeparatorMenuItem(), menuEncoding);

        // ── XML ──
        MenuItem miPretty     = menuItem(I18n.t("menu.xml.prettyPrint"), "Ctrl+P",  () -> withActive(s -> s.getController().prettyPrint()));
        MenuItem miXsdFolder  = menuItem(I18n.t("menu.xml.setXsdFolder"), null, () -> withActive(s -> s.getController().setXsdFolder()));
        MenuItem miXsd        = menuItem(I18n.t("menu.xml.loadXsd"), null, () -> withActive(s -> s.getController().loadXsd()));
        MenuItem miValida     = menuItem(I18n.t("menu.xml.validate"), "Ctrl+E",  () -> withActive(s -> s.getController().validate()));
        MenuItem miXsdInfo    = menuItem(I18n.t("menu.xml.xsdFolderInfo"), null, this::showXsdFolderInfo);

        Menu menuXml = new Menu(I18n.t("menu.xml"));
        menuXml.getItems().addAll(
            miPretty,
            new SeparatorMenuItem(),
            miXsdFolder, miXsdInfo,
            new SeparatorMenuItem(),
            miXsd, miValida
        );

        MenuItem miUpdate = menuItem(I18n.t("menu.help.checkUpdates"), null, this::checkForUpdatesManually);
        MenuItem miAbout  = menuItem(I18n.t("menu.help.about"), null, this::showAbout);
        Menu menuAiuto = new Menu(I18n.t("menu.help"));
        menuAiuto.getItems().addAll(miUpdate, new SeparatorMenuItem(), miAbout);

        refreshRecentMenu(recentMgr.getRecentFiles());
        updateXsdFolderStatus();
        return new MenuBar(menuFile, menuModifica, menuVisualizza, menuXml, buildSettingsMenu(), menuAiuto);
    }

    // ──────────────────────────────────────────────
    // AGGIORNAMENTI
    // ──────────────────────────────────────────────

    /**
     * Controllo silenzioso all'avvio: parla solo se c'è davvero una versione nuova.
     * Al più una volta al giorno, e solo se l'utente non l'ha disattivato.
     */
    private void checkForUpdatesAtStartup() {
        if (!UpdateChecker.isAutoCheckEnabled() || !UpdateChecker.shouldCheckToday()) return;
        Thread.ofVirtual().start(() ->
            UpdateChecker.findNewerRelease().ifPresent(release ->
                Platform.runLater(() -> showUpdateDialog(release))));
    }

    /**
     * Controllo richiesto dal menu: qui l'esito va mostrato sempre, anche quando
     * non c'è niente di nuovo o la rete manca.
     */
    private void checkForUpdatesManually() {
        statusBar.setStatus(I18n.t("update.checking"));
        Thread.ofVirtual().start(() -> {
            var latest = UpdateChecker.fetchLatest();
            Platform.runLater(() -> {
                statusBar.setStatus(I18n.t("status.ready"));
                if (latest.isEmpty()) {
                    Alert a = new Alert(Alert.AlertType.WARNING);
                    a.setTitle(I18n.t("update.failed.title"));
                    a.setHeaderText(I18n.t("update.failed.header"));
                    a.setContentText(I18n.t("update.failed.content"));
                    a.showAndWait();
                } else if (UpdateChecker.compareVersions(latest.get().version(), AppInfo.version()) > 0) {
                    showUpdateDialog(latest.get());
                } else {
                    Alert a = new Alert(Alert.AlertType.INFORMATION);
                    a.setTitle(I18n.t("update.upToDate.title"));
                    a.setHeaderText(I18n.t("update.upToDate.header"));
                    a.setContentText(I18n.t("update.upToDate.content", AppInfo.version()));
                    a.showAndWait();
                }
            });
        });
    }

    /** Mostra la nuova versione con le sue note e il collegamento al download. */
    private void showUpdateDialog(UpdateChecker.Release release) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.initOwner(stage);
        alert.setTitle(I18n.t("update.available.title"));
        alert.setHeaderText(I18n.t("update.available.header", release.name(), AppInfo.version()));

        if (!release.notes().isBlank()) {
            TextArea notes = new TextArea(release.notes());
            notes.setEditable(false);
            notes.setWrapText(true);
            notes.setPrefRowCount(10);
            alert.getDialogPane().setContent(notes);
            alert.getDialogPane().setPrefWidth(620);
        }

        ButtonType btnDownload = new ButtonType(I18n.t("update.available.download"));
        ButtonType btnLater    = new ButtonType(I18n.t("update.available.later"), ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(btnDownload, btnLater);

        alert.showAndWait().ifPresent(choice -> {
            if (choice == btnDownload && hostServices != null) {
                hostServices.showDocument(release.pageUrl());
            }
        });
    }

    /** Menu Impostazioni: lingua e controllo aggiornamenti. */
    private Menu buildSettingsMenu() {
        Menu menuLingua = new Menu(I18n.t("menu.settings.language"));
        ToggleGroup gruppo = new ToggleGroup();
        for (I18n.Language lang : I18n.AVAILABLE) {
            RadioMenuItem item = new RadioMenuItem(lang.label());
            item.setToggleGroup(gruppo);
            item.setSelected(lang.code().equals(I18n.getLanguage()));
            item.setOnAction(e -> changeLanguage(lang.code()));
            menuLingua.getItems().add(item);
        }
        CheckMenuItem miAutoUpdate = new CheckMenuItem(I18n.t("menu.settings.autoUpdate"));
        miAutoUpdate.setSelected(UpdateChecker.isAutoCheckEnabled());
        miAutoUpdate.setOnAction(e -> UpdateChecker.setAutoCheckEnabled(miAutoUpdate.isSelected()));

        Menu menuSettings = new Menu(I18n.t("menu.settings"));
        menuSettings.getItems().addAll(menuLingua, new SeparatorMenuItem(), miAutoUpdate);
        return menuSettings;
    }

    /** Cambia lingua e aggiorna subito l'interfaccia, senza riavviare. */
    private void changeLanguage(String code) {
        if (!I18n.setLanguage(code)) return;
        applyLanguage();
    }

    private void applyLanguage() {
        // Menu e toolbar si ricostruiscono da zero: è più semplice e sicuro che
        // rincorrere ogni etichetta, e la scena resta la stessa.
        root.setTop(new VBox(buildMenuBar(), buildToolBar()));
        setupAccelerators();

        for (Tab tab : tabPane.getTabs()) {
            if (tab.getUserData() instanceof EditorSession s) s.applyLanguage();
        }
        Tab selected = tabPane.getSelectionModel().getSelectedItem();
        if (selected != null) updateWindowTitle(selected.getText());
    }

    // ──────────────────────────────────────────────
    // TOOLBAR
    // ──────────────────────────────────────────────

    private ToolBar buildToolBar() {
        ToolBar bar = new ToolBar(
            toolBtn(I18n.t("toolbar.newTab"), this::newTab, I18n.t("tooltip.newTab")),
            toolBtn(I18n.t("toolbar.open"), this::openFile, I18n.t("tooltip.open")),
            toolBtn(I18n.t("toolbar.save"), () -> withActive(s -> s.getController().saveFile()), I18n.t("tooltip.save")),
            new Separator(Orientation.VERTICAL),
            toolBtn(I18n.t("toolbar.undo"), () -> withActive(s -> s.getEditorPane().undo()), I18n.t("tooltip.undo")),
            toolBtn(I18n.t("toolbar.redo"), () -> withActive(s -> s.getEditorPane().redo()), I18n.t("tooltip.redo")),
            new Separator(Orientation.VERTICAL),
            toolBtn(I18n.t("toolbar.prettyPrint"), () -> withActive(s -> s.getController().prettyPrint()), I18n.t("tooltip.prettyPrint")),
            toolBtn(I18n.t("toolbar.loadXsd"), () -> withActive(s -> s.getController().loadXsd()), I18n.t("tooltip.loadXsd")),
            toolBtn(I18n.t("toolbar.validate"), () -> withActive(s -> s.getController().validate()), I18n.t("tooltip.validate")),
            new Separator(Orientation.VERTICAL),
            toolBtn(I18n.t("toolbar.find"), () -> withActive(s -> s.getController().toggleSearch()), I18n.t("tooltip.find")),
            toolBtn(I18n.t("toolbar.line"), () -> withActive(s -> s.getController().showGoToLine()), I18n.t("tooltip.goToLine"))
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
        int count = tabPane.getTabs().size();
        if (count == 0) return;
        int idx = tabPane.getSelectionModel().getSelectedIndex();
        tabPane.getSelectionModel().select((idx + 1) % count);
    }

    private void selectPrevTab() {
        int count = tabPane.getTabs().size();
        if (count == 0) return;
        int idx = tabPane.getSelectionModel().getSelectedIndex();
        tabPane.getSelectionModel().select((idx - 1 + count) % count);
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
            a.setTitle(I18n.t("dialog.xsdFolder.title"));
            a.setHeaderText(I18n.t("dialog.xsdFolder.noneHeader"));
            a.setContentText(I18n.t("dialog.xsdFolder.noneContent"));
            a.showAndWait();
            return;
        }
        var schemas = xsdFolderMgr.listAvailableSchemas();
        String list = schemas.isEmpty()
            ? I18n.t("dialog.xsdFolder.empty")
            : schemas.stream()
                .map(p -> "  • " + p.getFileName())
                .reduce("", (a, b) -> a + "\n" + b);

        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(I18n.t("dialog.xsdFolder.title"));
        a.setHeaderText(I18n.t("dialog.xsdFolder.header", xsdFolderMgr.getXsdFolder().orElseThrow()));
        a.setContentText(I18n.t("dialog.xsdFolder.content", schemas.size()) + list);
        a.getDialogPane().setPrefWidth(520);
        a.showAndWait();
    }

    private void showAbout() {
        javafx.scene.control.Label lDev     = new javafx.scene.control.Label(I18n.t("dialog.about.developer"));
        javafx.scene.control.Label lAzienda = new javafx.scene.control.Label(I18n.t("dialog.about.company"));
        javafx.scene.control.Label lSito    = new javafx.scene.control.Label(I18n.t("dialog.about.website"));
        javafx.scene.control.Hyperlink link = new javafx.scene.control.Hyperlink("www.touchinformatica.it");
        link.setOnAction(e -> {
            if (hostServices != null)
                hostServices.showDocument("https://www.touchinformatica.it");
        });
        javafx.scene.layout.HBox siteRow = new javafx.scene.layout.HBox(lSito, link);
        siteRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        javafx.scene.layout.VBox content = new javafx.scene.layout.VBox(6, lDev, lAzienda, siteRow);
        content.setPadding(new javafx.geometry.Insets(10, 0, 0, 0));

        Dialog<Void> dlg = new Dialog<>();
        dlg.setTitle(I18n.t("dialog.about.title"));
        dlg.setHeaderText(AppInfo.nameAndVersion());
        dlg.getDialogPane().setContent(content);
        dlg.getDialogPane().setPrefWidth(380);
        dlg.getDialogPane().getButtonTypes().add(ButtonType.OK);
        dlg.showAndWait();
    }

    private void updateXsdFolderStatus() {
        xsdFolderMgr.getXsdFolder().ifPresent(f -> {
            int n = xsdFolderMgr.listAvailableSchemas().size();
            statusBar.setStatus(I18n.t("status.xsdFolder", f.getFileName(), n));
        });
    }

    // ──────────────────────────────────────────────
    // FILE RECENTI
    // ──────────────────────────────────────────────

    public void refreshRecentMenu(List<Path> recents) {
        if (menuRecenti == null) return;
        menuRecenti.getItems().clear();
        if (recents.isEmpty()) {
            menuRecenti.getItems().add(new MenuItem(I18n.t("menu.file.recent.none")));
            return;
        }
        for (Path p : recents) {
            MenuItem mi = new MenuItem(p.getFileName() + "   " + p.getParent());
            mi.setOnAction(e -> openPathFromRecent(p));
            menuRecenti.getItems().add(mi);
        }
        menuRecenti.getItems().add(new SeparatorMenuItem());
        MenuItem clear = new MenuItem(I18n.t("menu.file.recent.clear"));
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

    public void show() {
        stage.show();
        checkForUpdatesAtStartup();
    }
}
