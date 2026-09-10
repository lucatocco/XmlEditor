package it.touchinformatica.xmleditor.util;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Controlla se su GitHub è stata pubblicata una versione più recente.
 *
 * <p>Si limita a chiedere e riferire: scaricare e installare resta all'utente.
 * Un'applicazione desktop non può installarsi da sola senza privilegi di
 * amministratore, e chiederli per un aggiornamento è peggio del problema che
 * risolve.</p>
 *
 * <p>Il controllo automatico avviene al più una volta al giorno e si può
 * disattivare; se la rete non c'è, tace.</p>
 */
public final class UpdateChecker {

    /** Release trovata su GitHub. */
    public record Release(String version, String name, String notes, String pageUrl) {}

    private static final String PREF_ENABLED    = "check_updates";
    private static final String PREF_LAST_CHECK = "last_update_check";

    private static final Preferences PREFS = Preferences.userNodeForPackage(UpdateChecker.class);

    private static final Duration TIMEOUT = Duration.ofSeconds(6);

    private UpdateChecker() {}

    // ──────────────────────────────────────────────
    // PREFERENZE
    // ──────────────────────────────────────────────

    public static boolean isAutoCheckEnabled() {
        return PREFS.getBoolean(PREF_ENABLED, true);
    }

    public static void setAutoCheckEnabled(boolean enabled) {
        PREFS.putBoolean(PREF_ENABLED, enabled);
    }

    /** true se oggi il controllo automatico non è ancora stato fatto. */
    public static boolean shouldCheckToday() {
        return PREFS.getLong(PREF_LAST_CHECK, 0) != LocalDate.now().toEpochDay();
    }

    private static void markCheckedToday() {
        PREFS.putLong(PREF_LAST_CHECK, LocalDate.now().toEpochDay());
    }

    // ──────────────────────────────────────────────
    // CONTROLLO
    // ──────────────────────────────────────────────

    /**
     * Interroga GitHub e restituisce la release solo se è più recente di quella
     * in esecuzione.
     *
     * <p>Bloccante: va invocato fuori dal thread dell'interfaccia.</p>
     *
     * @return la release più recente, o empty se siamo aggiornati o la rete manca
     */
    public static Optional<Release> findNewerRelease() {
        return fetchLatest().filter(r -> compareVersions(r.version(), AppInfo.version()) > 0);
    }

    /**
     * Ultima release pubblicata, aggiornata o meno.
     * Usato dal controllo manuale, che deve poter dire "sei già aggiornato".
     */
    public static Optional<Release> fetchLatest() {
        markCheckedToday();
        String url = "https://api.github.com/repos/" + AppInfo.repository() + "/releases/latest";
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build()) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Accept", "application/vnd.github+json")
                // GitHub rifiuta le richieste senza User-Agent
                .header("User-Agent", "XmlEditor/" + AppInfo.version())
                .timeout(TIMEOUT)
                .GET()
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return Optional.empty();
            return parseRelease(response.body());

        } catch (Exception e) {
            // Nessuna rete, timeout, limite di richieste raggiunto: si tace
            return Optional.empty();
        }
    }

    // ──────────────────────────────────────────────
    // PARSING
    // ──────────────────────────────────────────────

    /**
     * Estrae i campi che servono dalla risposta di GitHub.
     *
     * <p>Bastano quattro stringhe: tirarsi dietro una libreria JSON per questo
     * peserebbe più del resto dell'applicazione.</p>
     */
    public static Optional<Release> parseRelease(String json) {
        String tag = field(json, "tag_name");
        if (tag == null || tag.isBlank()) return Optional.empty();

        String version = tag.startsWith("v") || tag.startsWith("V") ? tag.substring(1) : tag;
        String name    = field(json, "name");
        String notes   = field(json, "body");
        String page    = field(json, "html_url");

        return Optional.of(new Release(
            version,
            name != null && !name.isBlank() ? name : tag,
            notes != null ? notes : "",
            page != null ? page : "https://github.com/" + AppInfo.repository() + "/releases/latest"
        ));
    }

    /** Valore di un campo stringa di primo livello, con le sequenze di escape risolte. */
    private static String field(String json, String name) {
        Matcher m = Pattern.compile("\"" + name + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(json);
        return m.find() ? unescape(m.group(1)) : null;
    }

    private static String unescape(String raw) {
        StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c != '\\' || i + 1 >= raw.length()) { out.append(c); continue; }
            char next = raw.charAt(++i);
            switch (next) {
                case 'n'  -> out.append('\n');
                case 't'  -> out.append('\t');
                case 'r'  -> { }                    // i CR di GitHub sporcherebbero il testo
                case 'u'  -> {
                    if (i + 4 < raw.length()) {
                        out.append((char) Integer.parseInt(raw.substring(i + 1, i + 5), 16));
                        i += 4;
                    }
                }
                default   -> out.append(next);      // \" \\ \/ e simili
            }
        }
        return out.toString();
    }

    // ──────────────────────────────────────────────
    // CONFRONTO VERSIONI
    // ──────────────────────────────────────────────

    /**
     * Confronta due versioni tipo {@code 2.5.0}.
     *
     * @return negativo se a precede b, zero se pari, positivo se a è più recente
     */
    public static int compareVersions(String a, String b) {
        String[] left  = normalize(a).split("\\.");
        String[] right = normalize(b).split("\\.");
        for (int i = 0; i < Math.max(left.length, right.length); i++) {
            int l = i < left.length  ? number(left[i])  : 0;
            int r = i < right.length ? number(right[i]) : 0;
            if (l != r) return Integer.compare(l, r);
        }
        return 0;
    }

    private static String normalize(String version) {
        if (version == null) return "0";
        String v = version.trim();
        if (v.startsWith("v") || v.startsWith("V")) v = v.substring(1);
        return v.isBlank() ? "0" : v;
    }

    private static int number(String part) {
        Matcher m = Pattern.compile("^(\\d+)").matcher(part);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }
}
