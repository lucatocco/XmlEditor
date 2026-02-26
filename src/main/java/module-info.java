module it.touchinformatica.xmleditor {
    requires javafx.controls;
    requires javafx.fxml;
    requires org.fxmisc.richtext;
    requires org.fxmisc.flowless;
    requires org.fxmisc.undo;
    requires Saxon.HE;
    requires java.xml;
    requires java.prefs;

    opens it.touchinformatica.xmleditor to javafx.fxml;
    opens it.touchinformatica.xmleditor.controller to javafx.fxml;

    exports it.touchinformatica.xmleditor;
}
