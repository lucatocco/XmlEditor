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
        recentFiles.remove(path);          // rimuovi se già presente (evita duplicati)
        recentFiles.add(0, path);          // aggiungi in cima
        if (recentFiles.size() > MAX_RECENT) {
            recentFiles.remove(recentFiles.size() - 1);
        }
        save();
    }

    public List<Path> getRecentFiles() {
        // Filtra file che non esistono più
        recentFiles.removeIf(p -> !Files.exists(p));
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
    }

    private void load() {
        int count = prefs.getInt(PREF_KEY_COUNT, 0);
        for (int i = 0; i < count; i++) {
            String val = prefs.get(PREF_KEY_PREFIX + i, null);
            if (val != null) {
                Path p = Path.of(val);
                if (Files.exists(p)) recentFiles.add(p);
            }
        }
    }
}
