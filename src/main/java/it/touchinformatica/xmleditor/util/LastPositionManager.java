package it.touchinformatica.xmleditor.util;

import java.nio.file.Path;
import java.util.prefs.Preferences;

public class LastPositionManager {

    private static final String PREF_PREFIX = "pos_";

    private final Preferences prefs;

    public LastPositionManager() {
        this.prefs = Preferences.userNodeForPackage(LastPositionManager.class);
    }

    public void savePosition(Path path, int line) {
        if (path == null) return;
        prefs.putInt(makeKey(path), line);
    }

    public int loadPosition(Path path) {
        if (path == null) return 1;
        return prefs.getInt(makeKey(path), 1);
    }

    private String makeKey(Path path) {
        int hash = path.toAbsolutePath().toString().hashCode();
        return PREF_PREFIX + Integer.toHexString(hash);
    }
}
