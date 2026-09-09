package it.touchinformatica.xmleditor.util;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.Optional;
import java.util.prefs.Preferences;
import java.util.stream.Stream;

/**
 * Gestisce la cartella XSD globale dell'applicazione.
 *
 * <p>La cartella viene persistita tramite Java Preferences API, quindi
 * sopravvive ai riavvii senza bisogno di file di configurazione esterni.</p>
 *
 * <p>Logica di ricerca dello schema per un dato file XML:</p>
 * <ol>
 *   <li>Cerca {@code <nomeFile>.xsd} nella cartella XSD (stesso nome del file XML)</li>
 *   <li>Se non trovato, restituisce l'elenco completo degli XSD disponibili
 *       così il chiamante può mostrare un selettore</li>
 * </ol>
 */
public class XsdFolderManager {


    private static final String PREF_KEY = "xsd_folder";

    private final Preferences prefs;
    private Path xsdFolder;

    public XsdFolderManager() {
        this.prefs = Preferences.userNodeForPackage(XsdFolderManager.class);
        load();
    }

    // ──────────────────────────────────────────────
    // GETTER / SETTER
    // ──────────────────────────────────────────────

    public Optional<Path> getXsdFolder() {
        return Optional.ofNullable(xsdFolder);
    }

    /**
     * Imposta e persiste la cartella XSD.
     *
     * @param folder directory che contiene i file .xsd
     * @throws IllegalArgumentException se il path non è una directory
     */
    public void setXsdFolder(Path folder) {
        if (folder != null && !Files.isDirectory(folder)) {
            throw new IllegalArgumentException(I18n.t("error.notADirectory", folder));
        }
        this.xsdFolder = folder;
        save();
    }

    public void clearXsdFolder() {
        this.xsdFolder = null;
        prefs.remove(PREF_KEY);
    }

    // ──────────────────────────────────────────────
    // LOOKUP SCHEMA
    // ──────────────────────────────────────────────

    /**
     * Cerca lo schema XSD più adatto per il file XML indicato.
     *
     * <p>Strategia:</p>
     * <ol>
     *   <li>Corrispondenza per nome: {@code fattura.xml} → cerca {@code fattura.xsd}</li>
     *   <li>Se non trovato, restituisce {@code Optional.empty()} — il chiamante
     *       può invocare {@link #listAvailableSchemas()} per mostrare un selettore</li>
     * </ol>
     *
     * @param xmlFileName nome del file XML (solo il nome, non il path completo)
     * @return XSD trovato per corrispondenza, o empty se non trovato
     */
    public Optional<Path> findMatchingSchema(String xmlFileName) {
        if (xsdFolder == null) return Optional.empty();

        // Rimuovi estensione e cerca <nome>.xsd
        String baseName = stripExtension(xmlFileName);
        Path candidate = xsdFolder.resolve(baseName + ".xsd");
        if (Files.isRegularFile(candidate)) return Optional.of(candidate);

        // Prova anche case-insensitive (utile su Linux)
        try (Stream<Path> stream = Files.list(xsdFolder)) {
            return stream
                .filter(p -> p.getFileName().toString().toLowerCase()
                    .equals((baseName + ".xsd").toLowerCase()))
                .findFirst();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /**
     * Elenca tutti i file .xsd presenti nella cartella configurata.
     *
     * @return lista ordinata di path XSD, vuota se la cartella non è impostata
     *         o non contiene XSD
     */
    public List<Path> listAvailableSchemas() {
        if (xsdFolder == null || !Files.isDirectory(xsdFolder)) return List.of();
        try (Stream<Path> stream = Files.list(xsdFolder)) {
            return stream
                .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".xsd"))
                .sorted()
                .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    /**
     * Indica se la funzionalità è attiva (cartella impostata e accessibile).
     */
    public boolean isConfigured() {
        return xsdFolder != null && Files.isDirectory(xsdFolder);
    }

    // ──────────────────────────────────────────────
    // PERSISTENCE
    // ──────────────────────────────────────────────

    private void save() {
        if (xsdFolder != null) {
            prefs.put(PREF_KEY, xsdFolder.toAbsolutePath().toString());
        } else {
            prefs.remove(PREF_KEY);
        }
    }

    private void load() {
        String stored = prefs.get(PREF_KEY, null);
        if (stored != null) {
            Path p = Path.of(stored);
            if (Files.isDirectory(p)) xsdFolder = p;
            // Se la cartella non esiste più la ignoriamo silenziosamente
        }
    }

    // ──────────────────────────────────────────────
    // HELPERS
    // ──────────────────────────────────────────────

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}
