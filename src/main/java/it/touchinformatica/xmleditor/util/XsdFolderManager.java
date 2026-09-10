package it.touchinformatica.xmleditor.util;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

    private static final XMLInputFactory STAX = createStaxFactory();

    private final Preferences prefs;
    private Path xsdFolder;

    // Indice degli schemi, ricostruito solo quando la cartella cambia
    private Path             indexedFolder;
    private List<Path>       indexedFiles = List.of();
    private List<SchemaInfo> cachedIndex  = List.of();

    private static XMLInputFactory createStaxFactory() {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        // Gli schemi sono file locali, ma nessun motivo per risolvere entità esterne
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        return factory;
    }

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
     * Descrive uno schema della cartella: come si chiama, che namespace definisce
     * e quali elementi può avere come radice.
     */
    public record SchemaInfo(Path path, String targetNamespace, Set<String> rootElements) {}

    /** Elemento radice di un documento XML: nome e namespace. */
    public record DocumentRoot(String localName, String namespace) {}

    /** Come si è arrivati allo schema: serve a spiegarlo nel log. */
    public enum MatchKind {
        SCHEMA_LOCATION, NAMESPACE, ROOT_ELEMENT, FILE_NAME
    }

    /** Schema individuato per un documento, con il motivo della scelta. */
    public record SchemaMatch(Path path, MatchKind kind) {}

    /**
     * Cerca lo schema XSD più adatto al documento, dal criterio più affidabile
     * al più debole:
     *
     * <ol>
     *   <li>il file indicato da {@code xsi:schemaLocation} nel documento</li>
     *   <li>lo schema il cui {@code targetNamespace} è il namespace della radice:
     *       per i tracciati CBI è una corrispondenza esatta, versione compresa</li>
     *   <li>lo schema che dichiara come elemento globale la radice del documento</li>
     *   <li>lo schema con lo stesso nome del file XML</li>
     * </ol>
     *
     * @param root              radice del documento, o null se non ricavabile
     * @param xmlFileName       nome del file XML, o null se non ancora salvato
     * @param schemaLocationHint nome file estratto da schemaLocation, o null
     * @return schema trovato e criterio usato, oppure empty
     */
    public Optional<SchemaMatch> findSchemaFor(DocumentRoot root,
                                               String xmlFileName,
                                               String schemaLocationHint) {
        if (xsdFolder == null) return Optional.empty();

        if (schemaLocationHint != null) {
            Optional<Path> byHint = findMatchingSchema(schemaLocationHint);
            if (byHint.isPresent()) return Optional.of(new SchemaMatch(byHint.get(), MatchKind.SCHEMA_LOCATION));
        }

        if (root != null) {
            List<SchemaInfo> schemas = index();

            if (root.namespace() != null && !root.namespace().isBlank()) {
                for (SchemaInfo info : schemas) {
                    if (root.namespace().equals(info.targetNamespace())) {
                        return Optional.of(new SchemaMatch(info.path(), MatchKind.NAMESPACE));
                    }
                }
            }

            // Nessun namespace o nessuno schema con quel namespace: si ripiega
            // sull'elemento radice. Se più schemi lo dichiarano (tipicamente più
            // versioni dello stesso tracciato) si prende l'ultimo in ordine di
            // nome, che è la versione più recente.
            List<SchemaInfo> byRoot = new ArrayList<>();
            for (SchemaInfo info : schemas) {
                if (info.rootElements().contains(root.localName())) byRoot.add(info);
            }
            if (!byRoot.isEmpty()) {
                return Optional.of(new SchemaMatch(byRoot.get(byRoot.size() - 1).path(), MatchKind.ROOT_ELEMENT));
            }
        }

        if (xmlFileName != null) {
            Optional<Path> byName = findMatchingSchema(xmlFileName);
            if (byName.isPresent()) return Optional.of(new SchemaMatch(byName.get(), MatchKind.FILE_NAME));
        }

        return Optional.empty();
    }

    /**
     * Schemi della cartella con namespace ed elementi radice, in ordine di nome.
     *
     * <p>L'indice è tenuto in memoria e ricostruito solo quando la cartella cambia:
     * rileggere decine di schemi a ogni apertura di file sarebbe uno spreco.</p>
     */
    public synchronized List<SchemaInfo> index() {
        List<Path> schemas = listAvailableSchemas();
        if (indexedFolder != null && indexedFolder.equals(xsdFolder)
            && indexedFiles.equals(schemas)) {
            return cachedIndex;
        }

        List<SchemaInfo> built = new ArrayList<>(schemas.size());
        for (Path schema : schemas) {
            built.add(readSchemaInfo(schema));
        }
        indexedFolder = xsdFolder;
        indexedFiles  = schemas;
        cachedIndex   = List.copyOf(built);
        return cachedIndex;
    }

    /**
     * Legge targetNamespace ed elementi globali di uno schema.
     * Si ferma appena esce dagli elementi di primo livello: non serve l'intero file.
     */
    private static SchemaInfo readSchemaInfo(Path schema) {
        String namespace = null;
        Set<String> roots = new LinkedHashSet<>();
        try (InputStream in = Files.newInputStream(schema)) {
            XMLStreamReader reader = STAX.createXMLStreamReader(in);
            int depth = 0;
            while (reader.hasNext()) {
                if (reader.next() == XMLStreamConstants.START_ELEMENT) {
                    depth++;
                    if (depth == 1) {
                        namespace = reader.getAttributeValue(null, "targetNamespace");
                    } else if (depth == 2 && "element".equals(reader.getLocalName())) {
                        String name = reader.getAttributeValue(null, "name");
                        if (name != null) roots.add(name);
                    }
                } else if (reader.getEventType() == XMLStreamConstants.END_ELEMENT) {
                    depth--;
                }
            }
            reader.close();
        } catch (IOException | XMLStreamException e) {
            // Schema illeggibile: resta nell'indice senza informazioni, così può
            // comunque essere scelto a mano dal selettore
        }
        return new SchemaInfo(schema, namespace, roots);
    }

    /**
     * Cerca uno schema con lo stesso nome del file indicato.
     * {@code fattura.xml} cerca {@code fattura.xsd}.
     */
    public Optional<Path> findMatchingSchema(String xmlFileName) {
        if (xsdFolder == null) return Optional.empty();

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
