package it.touchinformatica.xmleditor.view;

import it.touchinformatica.xmleditor.util.I18n;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.StringReader;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

/**
 * Pannello ad albero che visualizza la struttura del documento XML.
 * Popola il TreeView tramite SAX parsing con numeri di riga accurati.
 */
public class TreePane extends VBox {

    private static final int MAX_CHILDREN = 200;
    private static final int MAX_DEPTH    = 12;

    private final Label title;
    private final TreeView<XmlNode> treeView;
    private Consumer<XmlNode> onNodeSelected;

    public TreePane() {
        title = new Label(I18n.t("panel.tree"));
        title.getStyleClass().add("panel-title");

        treeView = new TreeView<>();
        treeView.setShowRoot(true);
        treeView.getStyleClass().add("xml-tree");
        treeView.setCellFactory(tv -> new XmlTreeCell());

        treeView.getSelectionModel().selectedItemProperty().addListener(
            (obs, old, newItem) -> {
                if (newItem != null && onNodeSelected != null) {
                    onNodeSelected.accept(newItem.getValue());
                }
            }
        );

        VBox.setVgrow(treeView, javafx.scene.layout.Priority.ALWAYS);
        getChildren().addAll(title, treeView);
        getStyleClass().add("tree-pane");
        setPrefWidth(280);
    }

    // ──────────────────────────────────────────────
    // REBUILD VIA SAX (con numeri di riga)
    // ──────────────────────────────────────────────

    public void rebuildFrom(String xmlText, Runnable onError) {
        Thread.ofVirtual().start(() -> {
            try {
                SAXParserFactory factory = SAXParserFactory.newInstance();
                factory.setNamespaceAware(true);
                factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
                SAXParser parser = factory.newSAXParser();
                TreeBuilderHandler handler = new TreeBuilderHandler();
                // StringReader e non byte UTF-8: così la dichiarazione
                // <?xml encoding="…"?> non fa re-interpretare gli accentati
                parser.parse(new InputSource(new StringReader(xmlText)), handler);
                TreeItem<XmlNode> root = handler.getRoot();
                Platform.runLater(() -> {
                    treeView.setRoot(root);
                    if (root != null) root.setExpanded(true);
                });
            } catch (Exception e) {
                if (onError != null) Platform.runLater(onError);
            }
        });
    }

    public void clear() {
        Platform.runLater(() -> treeView.setRoot(null));
    }

    /** Ricarica i testi dopo un cambio di lingua. */
    public void applyLanguage() {
        title.setText(I18n.t("panel.tree"));
    }

    public void setOnNodeSelected(Consumer<XmlNode> callback) {
        this.onNodeSelected = callback;
    }

    // ──────────────────────────────────────────────
    // RECORD MODELLO NODO
    // ──────────────────────────────────────────────

    /**
     * Nodo dell'albero.
     *
     * @param line   riga in cui termina il tag di apertura
     * @param column colonna subito dopo il {@code >} del tag di apertura:
     *               insieme alla riga individua il punto esatto nel documento
     */
    public record XmlNode(String tag, String attrs, int line, int column) {
        @Override public String toString() { return tag; }
    }

    // ──────────────────────────────────────────────
    // SAX HANDLER
    // ──────────────────────────────────────────────

    private static class TreeBuilderHandler extends DefaultHandler {

        private Locator locator;
        private final Deque<TreeItem<XmlNode>> stack = new ArrayDeque<>();
        private TreeItem<XmlNode> root;
        private int depth = 0;

        @Override
        public void setDocumentLocator(Locator locator) {
            this.locator = locator;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes atts) {
            depth++;
            if (depth > MAX_DEPTH) return;

            int line   = locator != null ? locator.getLineNumber()   : 0;
            int column = locator != null ? locator.getColumnNumber() : 0;
            String tag = (localName != null && !localName.isEmpty()) ? localName : qName;
            String attrsPreview = buildAttrsPreview(atts);

            TreeItem<XmlNode> item = new TreeItem<>(new XmlNode(tag, attrsPreview, line, column));

            if (stack.isEmpty()) {
                root = item;
            } else {
                TreeItem<XmlNode> parent = stack.peek();
                if (parent.getChildren().size() < MAX_CHILDREN) {
                    parent.getChildren().add(item);
                }
            }
            stack.push(item);
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            if (depth <= MAX_DEPTH && !stack.isEmpty()) {
                stack.pop();
            }
            depth--;
        }

        public TreeItem<XmlNode> getRoot() { return root; }

        private String buildAttrsPreview(Attributes atts) {
            if (atts == null || atts.getLength() == 0) return "";
            StringBuilder sb = new StringBuilder();
            int max = Math.min(atts.getLength(), 3);
            for (int i = 0; i < max; i++) {
                if (i > 0) sb.append("  ");
                sb.append(atts.getQName(i)).append("=\"").append(atts.getValue(i)).append("\"");
            }
            if (atts.getLength() > 3) sb.append("  …");
            return sb.toString();
        }
    }

    // ──────────────────────────────────────────────
    // CELL RENDERER
    // ──────────────────────────────────────────────

    private static class XmlTreeCell extends TreeCell<XmlNode> {
        @Override
        protected void updateItem(XmlNode item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
            } else {
                javafx.scene.text.TextFlow flow = new javafx.scene.text.TextFlow();

                javafx.scene.text.Text tagText = new javafx.scene.text.Text(item.tag());
                tagText.getStyleClass().add("tree-tag");
                flow.getChildren().add(tagText);

                if (!item.attrs().isEmpty()) {
                    javafx.scene.text.Text attrText = new javafx.scene.text.Text("  " + item.attrs());
                    attrText.getStyleClass().add("tree-attr");
                    flow.getChildren().add(attrText);
                }

                setGraphic(flow);
                setText(null);
            }
        }
    }
}
