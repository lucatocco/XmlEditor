package it.touchinformatica.xmleditor.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Il rischio del multilingua è che una lingua resti indietro rispetto all'altra:
 * questi test lo trasformano in una build rossa invece che in un menu con dentro
 * una chiave grezza tipo "menu.file.open".
 */
class I18nTest {

    private static final String BUNDLE = "it.touchinformatica.xmleditor.i18n.messages";

    private final String originale = I18n.getLanguage();

    @AfterEach
    void ripristina() {
        I18n.setLanguage(originale);
    }

    private Set<String> keysOf(Locale locale) {
        return new TreeSet<>(ResourceBundle.getBundle(BUNDLE, locale,
            ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES))
            .keySet());
    }

    @Test
    @DisplayName("italiano e inglese hanno esattamente le stesse chiavi")
    void languagesHaveSameKeys() {
        Set<String> en = keysOf(Locale.ENGLISH);
        Set<String> it = keysOf(Locale.ITALIAN);

        Set<String> mancantiInItaliano = new TreeSet<>(en); mancantiInItaliano.removeAll(it);
        Set<String> mancantiInInglese  = new TreeSet<>(it); mancantiInInglese.removeAll(en);

        assertTrue(mancantiInItaliano.isEmpty(), "chiavi senza traduzione italiana: " + mancantiInItaliano);
        assertTrue(mancantiInInglese.isEmpty(),  "chiavi presenti solo in italiano: " + mancantiInInglese);
    }

    @Test
    @DisplayName("nessun testo è rimasto vuoto")
    void noEmptyTranslations() {
        for (Locale locale : List.of(Locale.ENGLISH, Locale.ITALIAN)) {
            ResourceBundle b = ResourceBundle.getBundle(BUNDLE, locale,
                ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES));
            for (String key : b.keySet()) {
                assertFalse(b.getString(key).isBlank(),
                    "testo vuoto per " + key + " in " + locale);
            }
        }
    }

    @Test
    @DisplayName("i segnaposto {0} coincidono tra le due lingue")
    void placeholdersMatch() {
        ResourceBundle en = ResourceBundle.getBundle(BUNDLE, Locale.ENGLISH,
            ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES));
        ResourceBundle it = ResourceBundle.getBundle(BUNDLE, Locale.ITALIAN,
            ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES));

        for (String key : en.keySet()) {
            assertEquals(placeholders(en.getString(key)), placeholders(it.getString(key)),
                "segnaposto diversi per la chiave " + key);
        }
    }

    private Set<String> placeholders(String text) {
        Set<String> found = new TreeSet<>();
        var m = java.util.regex.Pattern.compile("\\{(\\d+)\\}").matcher(text);
        while (m.find()) found.add(m.group(1));
        return found;
    }

    @Test
    @DisplayName("il cambio di lingua restituisce testi diversi")
    void switchingLanguageChangesText() {
        I18n.setLanguage("en");
        String english = I18n.t("menu.file.open");

        assertTrue(I18n.setLanguage("it"));
        assertNotEquals(english, I18n.t("menu.file.open"));
        assertEquals("Apri…", I18n.t("menu.file.open"));
    }

    @Test
    @DisplayName("una lingua sconosciuta viene ignorata")
    void ignoresUnknownLanguage() {
        I18n.setLanguage("en");

        assertFalse(I18n.setLanguage("de"));
        assertEquals("en", I18n.getLanguage());
    }

    @Test
    @DisplayName("i segnaposto vengono sostituiti dagli argomenti")
    void formatsArguments() {
        I18n.setLanguage("en");

        assertEquals("Saved: /tmp/a.xml", I18n.t("log.saved", "/tmp/a.xml"));
        assertTrue(I18n.t("status.caret", 1, 2, 3, "UTF-8", "LF").startsWith("Line 1:2"));
    }

    @Test
    @DisplayName("una chiave inesistente restituisce la chiave, senza esplodere")
    void unknownKeyIsVisibleNotFatal() {
        assertEquals("chiave.inventata", I18n.t("chiave.inventata"));
    }
}
