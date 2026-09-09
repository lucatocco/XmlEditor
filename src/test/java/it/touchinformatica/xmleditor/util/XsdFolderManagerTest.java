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

    @Test
    @DisplayName("senza cartella impostata non cerca nulla")
    void unconfiguredFindsNothing() {
        XsdFolderManager mgr = new XsdFolderManager();
        mgr.clearXsdFolder();

        assertFalse(mgr.isConfigured());
        assertTrue(mgr.findMatchingSchema("fattura.xml").isEmpty());
        assertTrue(mgr.listAvailableSchemas().isEmpty());
    }
}
