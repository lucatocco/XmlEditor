package it.touchinformatica.xmleditor.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Test della ricerca dello schema XSD per un documento. */
class XsdFolderManagerTest {

    private XsdFolderManager withFolder(Path dir) {
        XsdFolderManager mgr = new XsdFolderManager();
        mgr.setXsdFolder(dir);
        return mgr;
    }

    @Test
    @DisplayName("trova lo schema con lo stesso nome del file XML")
    void findsSchemaByName(@TempDir Path dir) throws Exception {
        Files.createFile(dir.resolve("fattura.xsd"));
        Files.createFile(dir.resolve("altro.xsd"));

        var found = withFolder(dir).findMatchingSchema("fattura.xml");

        assertTrue(found.isPresent());
        assertEquals("fattura.xsd", found.get().getFileName().toString());
    }

    @Test
    @DisplayName("la corrispondenza per nome ignora maiuscole e minuscole")
    void matchesCaseInsensitively(@TempDir Path dir) throws Exception {
        Files.createFile(dir.resolve("Fattura.xsd"));

        assertTrue(withFolder(dir).findMatchingSchema("fattura.xml").isPresent());
    }

    @Test
    @DisplayName("senza corrispondenza non restituisce nulla")
    void returnsEmptyWhenNoMatch(@TempDir Path dir) throws Exception {
        Files.createFile(dir.resolve("altro.xsd"));

        assertTrue(withFolder(dir).findMatchingSchema("fattura.xml").isEmpty());
    }

    @Test
    @DisplayName("elenca solo i file .xsd, in ordine")
    void listsOnlySchemasSorted(@TempDir Path dir) throws Exception {
        Files.createFile(dir.resolve("b.xsd"));
        Files.createFile(dir.resolve("a.xsd"));
        Files.createFile(dir.resolve("lettera.txt"));

        var schemas = withFolder(dir).listAvailableSchemas();

        assertEquals(2, schemas.size());
        assertEquals("a.xsd", schemas.get(0).getFileName().toString());
        assertEquals("b.xsd", schemas.get(1).getFileName().toString());
    }

    @Test
    @DisplayName("una cartella vuota risulta configurata ma senza schemi")
    void emptyFolderIsConfigured(@TempDir Path dir) {
        XsdFolderManager mgr = withFolder(dir);

        assertTrue(mgr.isConfigured());
        assertTrue(mgr.listAvailableSchemas().isEmpty());
    }

    @Test
    @DisplayName("un path che non è una directory viene rifiutato")
    void rejectsNonDirectory(@TempDir Path dir) throws Exception {
        Path file = Files.createFile(dir.resolve("non-una-cartella.xsd"));

        assertThrows(IllegalArgumentException.class, () -> new XsdFolderManager().setXsdFolder(file));
    }

    // ──────────────────────────────────────────────
    // SELEZIONE AUTOMATICA DELLO SCHEMA
    // ──────────────────────────────────────────────

    /** Scrive uno schema con il namespace e l'elemento radice indicati. */
    private void writeSchema(Path dir, String fileName, String targetNamespace, String rootElement)
            throws Exception {
        Files.writeString(dir.resolve(fileName), """
            <?xml version="1.0"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                       targetNamespace="%s" xmlns="%s">
              <xs:element name="%s"/>
            </xs:schema>
            """.formatted(targetNamespace, targetNamespace, rootElement));
    }

    @Test
    @DisplayName("indicizza namespace ed elemento radice di ogni schema")
    void indexesNamespaceAndRoot(@TempDir Path dir) throws Exception {
        writeSchema(dir, "Pagamenti.00.04.00.xsd", "urn:test:Pagamenti.00.04.00", "Pagamenti");

        var index = withFolder(dir).index();

        assertEquals(1, index.size());
        assertEquals("urn:test:Pagamenti.00.04.00", index.get(0).targetNamespace());
        assertTrue(index.get(0).rootElements().contains("Pagamenti"));
    }

    @Test
    @DisplayName("trova lo schema dal namespace del documento, anche se il file XML ha tutt'altro nome")
    void findsSchemaByNamespace(@TempDir Path dir) throws Exception {
        writeSchema(dir, "Pagamenti.00.04.00.xsd", "urn:test:Pagamenti.00.04.00", "Pagamenti");
        writeSchema(dir, "Incassi.00.04.00.xsd",   "urn:test:Incassi.00.04.00",   "Incassi");

        var root  = new XsdFolderManager.DocumentRoot("Pagamenti", "urn:test:Pagamenti.00.04.00");
        var match = withFolder(dir).findSchemaFor(root, "disposizione_20260910.xml", null);

        assertTrue(match.isPresent());
        assertEquals("Pagamenti.00.04.00.xsd", match.get().path().getFileName().toString());
        assertEquals(XsdFolderManager.MatchKind.NAMESPACE, match.get().kind());
    }

    @Test
    @DisplayName("il namespace distingue due versioni dello stesso tracciato")
    void namespaceDiscriminatesVersions(@TempDir Path dir) throws Exception {
        writeSchema(dir, "Pagamenti.00.04.00.xsd", "urn:test:Pagamenti.00.04.00", "Pagamenti");
        writeSchema(dir, "Pagamenti.00.04.01.xsd", "urn:test:Pagamenti.00.04.01", "Pagamenti");

        var root  = new XsdFolderManager.DocumentRoot("Pagamenti", "urn:test:Pagamenti.00.04.00");
        var match = withFolder(dir).findSchemaFor(root, null, null);

        assertEquals("Pagamenti.00.04.00.xsd", match.orElseThrow().path().getFileName().toString());
    }

    @Test
    @DisplayName("senza namespace ripiega sull'elemento radice")
    void fallsBackToRootElement(@TempDir Path dir) throws Exception {
        writeSchema(dir, "Pagamenti.00.04.00.xsd", "urn:test:Pagamenti.00.04.00", "Pagamenti");

        var root  = new XsdFolderManager.DocumentRoot("Pagamenti", null);
        var match = withFolder(dir).findSchemaFor(root, null, null);

        assertTrue(match.isPresent());
        assertEquals(XsdFolderManager.MatchKind.ROOT_ELEMENT, match.get().kind());
    }

    @Test
    @DisplayName("schemaLocation ha la precedenza sul namespace")
    void schemaLocationWins(@TempDir Path dir) throws Exception {
        writeSchema(dir, "Pagamenti.00.04.00.xsd", "urn:test:Pagamenti.00.04.00", "Pagamenti");
        writeSchema(dir, "Esplicito.xsd",          "urn:test:Altro",              "Pagamenti");

        var root  = new XsdFolderManager.DocumentRoot("Pagamenti", "urn:test:Pagamenti.00.04.00");
        var match = withFolder(dir).findSchemaFor(root, null, "Esplicito.xsd");

        assertEquals("Esplicito.xsd", match.orElseThrow().path().getFileName().toString());
        assertEquals(XsdFolderManager.MatchKind.SCHEMA_LOCATION, match.get().kind());
    }

    @Test
    @DisplayName("senza corrispondenze non sceglie nulla, invece di indovinare")
    void noMatchReturnsEmpty(@TempDir Path dir) throws Exception {
        writeSchema(dir, "Pagamenti.00.04.00.xsd", "urn:test:Pagamenti.00.04.00", "Pagamenti");

        var root = new XsdFolderManager.DocumentRoot("Sconosciuto", "urn:test:Sconosciuto");

        assertTrue(withFolder(dir).findSchemaFor(root, "ignoto.xml", null).isEmpty());
    }

    @Test
    @DisplayName("uno schema illeggibile non fa fallire l'indicizzazione degli altri")
    void brokenSchemaDoesNotBreakIndex(@TempDir Path dir) throws Exception {
        writeSchema(dir, "Buono.xsd", "urn:test:Buono", "Buono");
        Files.writeString(dir.resolve("Rotto.xsd"), "<xs:schema><non chiuso>");

        var mgr = withFolder(dir);

        assertEquals(2, mgr.index().size());
        var root = new XsdFolderManager.DocumentRoot("Buono", "urn:test:Buono");
        assertTrue(mgr.findSchemaFor(root, null, null).isPresent());
    }

    @Test
    @DisplayName("senza cartella impostata non cerca nulla")
    void unconfiguredFindsNothing() {
        XsdFolderManager mgr = new XsdFolderManager();
        mgr.clearXsdFolder();

        assertFalse(mgr.isConfigured());
        assertTrue(mgr.findMatchingSchema("fattura.xml").isEmpty());
        assertTrue(mgr.listAvailableSchemas().isEmpty());
        assertTrue(mgr.findSchemaFor(
            new XsdFolderManager.DocumentRoot("qualsiasi", "urn:x"), "a.xml", null).isEmpty());
    }
}
