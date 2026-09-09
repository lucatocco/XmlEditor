package it.touchinformatica.xmleditor.util;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
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

    /**
     * Chiave derivata da SHA-256 del path assoluto.
     *
     * <p>Con {@code String.hashCode()} due file diversi potevano collidere e
     * scambiarsi la posizione salvata: 32 bit su un hash non crittografico si
     * collidono senza sforzo. La chiave deve stare sotto gli 80 caratteri
     * imposti da Preferences, quindi si troncano i primi 16 byte.</p>
     */
    private String makeKey(Path path) {
        String absolute = path.toAbsolutePath().toString();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(absolute.getBytes(StandardCharsets.UTF_8));
            byte[] head = new byte[16];
            System.arraycopy(digest, 0, head, 0, head.length);
            return PREF_PREFIX + HexFormat.of().formatHex(head);
        } catch (NoSuchAlgorithmException e) {
            return PREF_PREFIX + Integer.toHexString(absolute.hashCode());
        }
    }
}
