package it.touchinformatica.xmleditor.view;

import it.touchinformatica.xmleditor.controller.MainController;
import it.touchinformatica.xmleditor.service.XmlService;
import it.touchinformatica.xmleditor.util.RecentFilesManager;
import javafx.geometry.Orientation;
import javafx.scene.control.Tab;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/**
 * EditorSession — un singolo tab completamente autonomo.
 * Contiene: EditorPane, SearchBar, TreePane, LogPane, MainController.
 */
public class EditorSession {

    private final Tab            tab;
    private final EditorPane     editorPane;
    private final SearchBar      searchBar;
    private final TreePane       treePane;
    private final LogPane        logPane;
    private final MainController controller;

    public EditorSession(XmlService xmlService,
                         RecentFilesManager recentMgr,
                         Consumer<List<Path>> refreshRecentMenu,
                         StatusBar sharedStatusBar) {

        this.editorPane = new EditorPane();
        this.treePane   = new TreePane();
        this.logPane    = new LogPane();
        this.searchBar  = new SearchBar(editorPane);

        this.controller = new MainController(
            xmlService,
            recentMgr,
            editorPane,
            treePane,
            logPane,
            sharedStatusBar,
            refreshRecentMenu,
            this::updateTabTitle
        );
        this.controller.setSearchBar(searchBar);

        // Layout interno al tab
        //   ┌────────────────────────────────────────┐
        //   │  searchBar (nascosta di default)       │
        //   ├───────────┬────────────────────────────┤
        //   │ treePane  │  editorPane                │
        //   │           ├────────────────────────────┤
        //   │           │  logPane                   │
        //   └───────────┴────────────────────────────┘

        javafx.scene.control.SplitPane vertSplit = new javafx.scene.control.SplitPane();
        vertSplit.setOrientation(Orientation.VERTICAL);
        vertSplit.getItems().addAll(editorPane, logPane);
        vertSplit.setDividerPositions(0.75);

        javafx.scene.control.SplitPane horzSplit = new javafx.scene.control.SplitPane();
        horzSplit.getItems().addAll(treePane, vertSplit);
        horzSplit.setDividerPositions(0.22);

        BorderPane content = new BorderPane();
        content.setTop(searchBar);
        content.setCenter(horzSplit);

        // Tab
        this.tab = new Tab();
        this.tab.setContent(content);
        this.tab.setUserData(this);   // ← fondamentale per activeSession()
        updateTabTitle("Nuovo documento");

        // Asterisco nel titolo quando modificato
        editorPane.modifiedProperty().addListener((obs, old, modified) -> {
            String current = tab.getText();
            if (modified && !current.startsWith("*")) {
                tab.setText("* " + current);
            } else if (!modified && current.startsWith("* ")) {
                tab.setText(current.substring(2));
            }
        });

        // Conferma chiusura tab se modificato
        tab.setOnCloseRequest(e -> {
            controller.saveCurrentPosition();
            if (!controller.confirmDiscardChanges()) e.consume();
        });
    }

    // ──────────────────────────────────────────────
    // HELPERS
    // ──────────────────────────────────────────────

    private void updateTabTitle(String fileName) {
        tab.setText(fileName);
    }

    // ──────────────────────────────────────────────
    // API PUBBLICA
    // ──────────────────────────────────────────────

    public Tab            getTab()        { return tab; }
    public EditorPane     getEditorPane() { return editorPane; }
    public MainController getController() { return controller; }
    public boolean        isModified()    { return editorPane.isModified(); }

    public void saveCurrentPosition()    { controller.saveCurrentPosition(); }

    public void openPath(Path path) { controller.openPath(path); }
}
