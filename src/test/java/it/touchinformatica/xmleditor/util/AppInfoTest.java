package it.touchinformatica.xmleditor.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * La versione dell'applicazione deve arrivare da pom.xml attraverso il filtering
 * Maven di app.properties: se il filtering si rompe, qui si vede subito.
 */
class AppInfoTest {

    @Test
    @DisplayName("la versione è quella di pom.xml, non il placeholder")
    void versionIsResolved() {
        String version = AppInfo.version();

        assertNotEquals("sviluppo", version, "app.properties non è stato filtrato da Maven");
        assertFalse(version.startsWith("${"), "il placeholder non è stato sostituito: " + version);
        assertTrue(version.matches("\\d+\\.\\d+\\.\\d+"), "formato inatteso: " + version);
    }

    @Test
    @DisplayName("il nome arriva dal pom")
    void nameIsResolved() {
        assertEquals("XML Editor", AppInfo.name());
    }

    @Test
    @DisplayName("l'etichetta unisce nome e versione")
    void labelJoinsNameAndVersion() {
        assertEquals(AppInfo.name() + " v" + AppInfo.version(), AppInfo.nameAndVersion());
    }
}
