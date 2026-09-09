package it.touchinformatica.xmleditor.util;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Gestisce la lista dei file aperti di recente usando Java Preferences API.
 * I file vengono persistiti tra sessioni.
 */
public class RecentFilesManager {

    private static final int MAX_RECENT = 10;
    private static final String PREF_KEY_PREFIX = "recent_";
    private static final String PREF_KEY_COUNT  = "recent_count";

    private final Preferences prefs;
    private final List<Path> recentFiles = new ArrayList<>();

    public RecentFilesManager() {
        this.prefs = Preferences.userNodeForPackage(RecentFilesManager.class);
        load();
    }

    public void add(Path path) {
        // Sempre in forma assoluta: lo stesso file aperto con un path relativo
        // (da riga di comando) altrimenti entrava in lista una seconda volta
        Path absolute = path.toAbsolutePath().normalize();
        recentFiles.remove(absolute);      // rimuovi se già presente (evita duplicati)
        recentFiles.add(0, absolute);      // aggiungi in cima
        if (recentFiles.size() > MAX_RECENT) {
            recentFiles.remove(recentFiles.size() - 1);
        }
        save();
    }

    public List<Path> getRecentFiles() {
        // Filtra i file che non esistono più, e rende persistente la rimozione:
        // senza il save() ricomparivano al riavvio successivo
        if (recentFiles.removeIf(p -> !Files.exists(p))) save();
        return List.copyOf(recentFiles);
    }

    public void clear() {
        recentFiles.clear();
        save();
    }

    private void save() {
        prefs.putInt(PREF_KEY_COUNT, recentFiles.size());
        for (int i = 0; i < recentFiles.size(); i++) {
            prefs.put(PREF_KEY_PREFIX + i, recentFiles.get(i).toAbsolutePath().toString());
        }
        // Ripulisce le voci lasciate da una lista più lunga
        for (int i = recentFiles.size(); i < MAX_RECENT; i++) {
            prefs.remove(PREF_KEY_PREFIX + i);
        }
    }

    private void load() {
        int count = prefs.getInt(PREF_KEY_COUNT, 0);
        for (int i = 0; i < count; i++) {
            String val = prefs.get(PREF_KEY_PREFIX + i, null);
            if (val != null) {
                Path p = Path.of(val).toAbsolutePath().normalize();
                if (Files.exists(p)) recentFiles.add(p);
            }
        }
    }
}
