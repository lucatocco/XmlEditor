package it.touchinformatica.xmleditor.util;

import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.prefs.Preferences;

/**
 * Testi dell'interfaccia nelle lingue disponibili.
 *
 * <p>La lingua scelta è persistita con Preferences, quindi sopravvive ai riavvii.
 * Alla prima esecuzione si parte da quella del sistema operativo, se è tra quelle
 * supportate, altrimenti dall'inglese.</p>
 *
 * <p>I testi stanno in {@code i18n/messages.properties} (inglese, lingua di base)
 * e {@code i18n/messages_it.properties}. Da Java 9 i file .properties dei
 * ResourceBundle sono letti in UTF-8, quindi gli accenti si scrivono direttamente.</p>
 */
public final class I18n {

    /** Una lingua selezionabile dal menu Impostazioni. */
    public record Language(String code, String label) {}

    public static final List<Language> AVAILABLE = List.of(
        new Language("en", "English"),
        new Language("it", "Italiano")
    );

    private static final String BUNDLE   = "it.touchinformatica.xmleditor.i18n.messages";
    private static final String PREF_KEY = "language";

    private static final Preferences PREFS = Preferences.userNodeForPackage(I18n.class);

    private static Locale locale = initialLocale();
    private static ResourceBundle bundle = load(locale);

    private I18n() {}

    // ──────────────────────────────────────────────
    // TESTI
    // ──────────────────────────────────────────────

    /**
     * Testo corrispondente alla chiave.
     * Se la chiave manca restituisce la chiave stessa, così un testo dimenticato
     * si vede nell'interfaccia invece di far cadere l'applicazione.
     */
    public static String t(String key) {
        try {
            return bundle.getString(key);
        } catch (MissingResourceException e) {
            return key;
        }
    }

    /** Testo con segnaposto {@code {0}}, {@code {1}}, … sostituiti dagli argomenti. */
    public static String t(String key, Object... args) {
        return MessageFormat.format(t(key), args);
    }

    // ──────────────────────────────────────────────
    // LINGUA
    // ──────────────────────────────────────────────

    /** Codice della lingua attiva, es. "it". */
    public static String getLanguage() {
        return locale.getLanguage();
    }

    /**
     * Cambia lingua e la rende persistente.
     *
     * @return true se la lingua è effettivamente cambiata
     */
    public static boolean setLanguage(String code) {
        if (code == null || code.equals(locale.getLanguage())) return false;
        if (AVAILABLE.stream().noneMatch(l -> l.code().equals(code))) return false;

        locale = Locale.of(code);
        bundle = load(locale);
        PREFS.put(PREF_KEY, code);
        return true;
    }

    // ──────────────────────────────────────────────
    // CARICAMENTO
    // ──────────────────────────────────────────────

    private static Locale initialLocale() {
        String saved = PREFS.get(PREF_KEY, null);
        if (saved != null && AVAILABLE.stream().anyMatch(l -> l.code().equals(saved))) {
            return Locale.of(saved);
        }
        // Prima esecuzione: si prova la lingua del sistema
        String system = Locale.getDefault().getLanguage();
        return AVAILABLE.stream().anyMatch(l -> l.code().equals(system))
            ? Locale.of(system)
            : Locale.ENGLISH;
    }

    private static ResourceBundle load(Locale target) {
        // Locale.ROOT come fallback: senza, una lingua di sistema non supportata
        // farebbe caricare il bundle di quella lingua se un giorno esistesse
        return ResourceBundle.getBundle(BUNDLE, target,
            ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES));
    }
}
