package it.touchinformatica.xmleditor.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Il confronto fra versioni e la lettura della risposta di GitHub sono le due
 * parti che possono sbagliare in silenzio: un confronto storto proporrebbe
 * aggiornamenti verso versioni più vecchie, o non ne proporrebbe affatto.
 * Entrambe si verificano senza toccare la rete.
 */
class UpdateCheckerTest {

    // ──────────────────────────────────────────────
    // CONFRONTO VERSIONI
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("riconosce quale delle due versioni è più recente")
    void comparesVersions() {
        assertTrue(UpdateChecker.compareVersions("2.6.0", "2.5.0") > 0);
        assertTrue(UpdateChecker.compareVersions("2.5.1", "2.5.0") > 0);
        assertTrue(UpdateChecker.compareVersions("3.0.0", "2.9.9") > 0);
        assertTrue(UpdateChecker.compareVersions("2.5.0", "2.6.0") < 0);
        assertEquals(0, UpdateChecker.compareVersions("2.5.0", "2.5.0"));
    }

    @Test
    @DisplayName("confronta i numeri, non le stringhe")
    void comparesNumericallyNotLexically() {
        // "10" viene prima di "9" in ordine alfabetico: l'errore classico
        assertTrue(UpdateChecker.compareVersions("2.10.0", "2.9.0") > 0);
        assertTrue(UpdateChecker.compareVersions("10.0.0", "9.0.0") > 0);
    }

    @Test
    @DisplayName("la v del tag git non conta")
    void ignoresTagPrefix() {
        assertEquals(0, UpdateChecker.compareVersions("v2.5.0", "2.5.0"));
        assertTrue(UpdateChecker.compareVersions("v2.6.0", "2.5.0") > 0);
    }

    @Test
    @DisplayName("versioni con un numero diverso di parti")
    void handlesDifferentLengths() {
        assertEquals(0, UpdateChecker.compareVersions("2.5", "2.5.0"));
        assertTrue(UpdateChecker.compareVersions("2.5.1", "2.5") > 0);
    }

    @Test
    @DisplayName("una versione illeggibile non fa proporre aggiornamenti a caso")
    void handlesGarbage() {
        assertTrue(UpdateChecker.compareVersions("sviluppo", "2.5.0") < 0);
        assertTrue(UpdateChecker.compareVersions(null, "2.5.0") < 0);
    }

    // ──────────────────────────────────────────────
    // LETTURA DELLA RISPOSTA GITHUB
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("legge versione, nome, note e link dalla risposta")
    void parsesRelease() {
        String json = """
            {"url":"https://api.github.com/repos/x/y/releases/1",
             "html_url":"https://github.com/x/y/releases/tag/v2.6.0",
             "tag_name":"v2.6.0",
             "name":"XML Editor v2.6.0",
             "draft":false,
             "body":"Prima riga\\r\\nSeconda riga con \\"virgolette\\"",
             "assets":[{"name":"xmleditor_2.6.0_amd64.deb"}]}
            """;

        var release = UpdateChecker.parseRelease(json).orElseThrow();

        assertEquals("2.6.0", release.version(), "la v del tag va tolta");
        assertEquals("XML Editor v2.6.0", release.name());
        assertEquals("https://github.com/x/y/releases/tag/v2.6.0", release.pageUrl());
        assertEquals("Prima riga\nSeconda riga con \"virgolette\"", release.notes(),
            "escape non risolti nelle note");
    }

    @Test
    @DisplayName("il nome del campo non si confonde con tag_name")
    void doesNotConfuseNameWithTagName() {
        String json = "{\"tag_name\":\"v3.0.0\",\"name\":\"Release tre\",\"html_url\":\"http://x\"}";

        var release = UpdateChecker.parseRelease(json).orElseThrow();

        assertEquals("3.0.0", release.version());
        assertEquals("Release tre", release.name());
    }

    @Test
    @DisplayName("senza tag_name non c'è release")
    void requiresTagName() {
        assertTrue(UpdateChecker.parseRelease("{\"name\":\"senza tag\"}").isEmpty());
        assertTrue(UpdateChecker.parseRelease("").isEmpty());
        assertTrue(UpdateChecker.parseRelease("non è json").isEmpty());
    }

    @Test
    @DisplayName("una release senza note resta utilizzabile")
    void handlesMissingNotes() {
        var release = UpdateChecker.parseRelease(
            "{\"tag_name\":\"v2.6.0\",\"html_url\":\"http://x\"}").orElseThrow();

        assertEquals("2.6.0", release.version());
        assertEquals("", release.notes());
        assertEquals("v2.6.0", release.name(), "senza nome si ripiega sul tag");
    }

    @Test
    @DisplayName("il controllo automatico è attivo di default e si può spegnere")
    void autoCheckToggle() {
        assertTrue(UpdateChecker.isAutoCheckEnabled(), "atteso attivo di default");

        UpdateChecker.setAutoCheckEnabled(false);
        assertFalse(UpdateChecker.isAutoCheckEnabled());

        UpdateChecker.setAutoCheckEnabled(true);
        assertTrue(UpdateChecker.isAutoCheckEnabled());
    }
}
