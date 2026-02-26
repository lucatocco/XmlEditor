package it.touchinformatica.xmleditor;

import it.touchinformatica.xmleditor.view.MainStage;
import javafx.application.Application;
import javafx.stage.Stage;

import java.nio.file.Path;
import java.util.List;

/**
 * Entry point dell'applicazione XML Editor.
 * Avvio: mvn javafx:run
 */
public class MainApp extends Application {

    @Override
    public void start(Stage primaryStage) {
        List<String> raw = getParameters().getRaw();
        List<Path> files = raw.stream()
                .map(Path::of)
                .filter(p -> p.toFile().isFile())
                .toList();
        new MainStage(primaryStage, files).show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
