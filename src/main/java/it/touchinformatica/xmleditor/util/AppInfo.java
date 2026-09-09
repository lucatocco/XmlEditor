package it.touchinformatica.xmleditor.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Nome e versione dell'applicazione, con un'unica fonte di verità: {@code pom.xml}.
 *
 * <p>Maven filtra {@code app.properties} durante il build sostituendo
 * {@code ${project.version}} con la versione reale, quindi non c'è nessun numero
 * di versione scritto a mano nel codice.</p>
 */
public final class AppInfo {

    private static final String RESOURCE = "/it/touchinformatica/xmleditor/app.properties";

    private static final Properties PROPS = load();

    private AppInfo() {}

    /** Nome esteso dell'applicazione, es. "XML Editor". */
    public static String name() {
        return PROPS.getProperty("name", "XML Editor");
    }

    /** Versione, es. "2.0.0"; "sviluppo" se le risorse non sono state filtrate da Maven. */
    public static String version() {
        String v = PROPS.getProperty("version", "");
        // Se si esegue senza passare da Maven il placeholder resta non sostituito
        return (v.isBlank() || v.startsWith("${")) ? "sviluppo" : v;
    }

    /** Etichetta pronta per titoli e finestre di informazioni, es. "XML Editor v2.0.0". */
    public static String nameAndVersion() {
        return name() + " v" + version();
    }

    private static Properties load() {
        Properties props = new Properties();
        try (InputStream in = AppInfo.class.getResourceAsStream(RESOURCE)) {
            if (in != null) props.load(in);
        } catch (IOException e) {
            // Restano i default: la versione non è un motivo per non partire
        }
        return props;
    }
}
