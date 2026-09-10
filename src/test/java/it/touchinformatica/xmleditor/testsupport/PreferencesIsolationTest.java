package it.touchinformatica.xmleditor.testsupport;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guardia: se l'isolamento delle Preferences salta, i test tornano a scrivere
 * nelle impostazioni reali dell'utente. Meglio accorgersene qui.
 */
class PreferencesIsolationTest {

    @Test
    @DisplayName("i test usano Preferences in memoria, non quelle dell'utente")
    void preferencesAreInMemory() {
        assertEquals(InMemoryPreferencesFactory.class.getName(),
            System.getProperty("java.util.prefs.PreferencesFactory"),
            "la PreferencesFactory di test non è attiva: i test scriverebbero nelle impostazioni reali");

        assertTrue(Preferences.userRoot().getClass().getName().contains("InMemoryPreferences"),
            "userRoot non è quella in memoria: " + Preferences.userRoot().getClass());
    }

    @Test
    @DisplayName("quello che i test scrivono non esce dal processo")
    void writesStayInMemory() {
        Preferences node = Preferences.userRoot().node("it/touchinformatica/xmleditor/util");
        node.put("xsd_folder", "/tmp/valore-di-test");

        assertEquals("/tmp/valore-di-test", node.get("xsd_folder", null));
        // Non essendoci backing store, niente di tutto questo raggiunge ~/.java
    }
}
