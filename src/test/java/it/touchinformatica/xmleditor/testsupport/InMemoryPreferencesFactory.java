package it.touchinformatica.xmleditor.testsupport;

import java.util.HashMap;
import java.util.Map;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.prefs.PreferencesFactory;

/**
 * Preferences che vivono solo in memoria, per la durata dei test.
 *
 * <p>Serve perché l'applicazione persiste cartella XSD, lingua, file recenti e
 * ultima posizione con {@link Preferences}: senza questa sostituzione i test
 * sovrascriverebbero le impostazioni reali di chi li esegue — è già successo,
 * con la cartella XSD dell'utente rimpiazzata da una directory temporanea.</p>
 *
 * <p>Attivata da surefire con
 * {@code -Djava.util.prefs.PreferencesFactory=...InMemoryPreferencesFactory}.</p>
 */
public class InMemoryPreferencesFactory implements PreferencesFactory {

    private final Preferences userRoot   = new InMemoryPreferences(null, "");
    private final Preferences systemRoot = new InMemoryPreferences(null, "");

    @Override public Preferences userRoot()   { return userRoot; }
    @Override public Preferences systemRoot() { return systemRoot; }

    private static class InMemoryPreferences extends AbstractPreferences {

        private final Map<String, String> values   = new HashMap<>();
        private final Map<String, InMemoryPreferences> children = new HashMap<>();

        InMemoryPreferences(InMemoryPreferences parent, String name) {
            super(parent, name);
        }

        @Override protected void putSpi(String key, String value) { values.put(key, value); }
        @Override protected String getSpi(String key)             { return values.get(key); }
        @Override protected void removeSpi(String key)            { values.remove(key); }

        @Override protected String[] keysSpi()          { return values.keySet().toArray(new String[0]); }
        @Override protected String[] childrenNamesSpi() { return children.keySet().toArray(new String[0]); }

        @Override protected AbstractPreferences childSpi(String name) {
            return children.computeIfAbsent(name, n -> new InMemoryPreferences(this, n));
        }

        @Override protected void removeNodeSpi() {
            values.clear();
            children.clear();
        }

        // Niente da sincronizzare: i valori non escono dalla memoria del processo
        @Override protected void syncSpi()  throws BackingStoreException { }
        @Override protected void flushSpi() throws BackingStoreException { }
    }
}
