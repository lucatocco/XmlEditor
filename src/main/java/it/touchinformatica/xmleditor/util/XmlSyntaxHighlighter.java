package it.touchinformatica.xmleditor.util;

import javafx.application.Platform;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Syntax highlighting XML per RichTextFX CodeArea.
 * Riconosce: tag, attributi, valori, commenti, CDATA, dichiarazione XML.
 */
public class XmlSyntaxHighlighter {

    // Gruppi regex ordinati per priorità
    private static final Pattern XML_PATTERN = Pattern.compile(
        "(?<COMMENT><!--[\\s\\S]*?-->)"
        + "|(?<CDATA><!\\[CDATA\\[[\\s\\S]*?\\]\\]>)"
        + "|(?<XMLDECL><\\?[^?]*\\?>)"
        + "|(?<TAG></?[\\w:\\-]+)"
        + "|(?<ATTRNAME>[\\w:\\-]+)(?=\\s*=)"
        + "|=\\s*(?<ATTRVALUE>\"[^\"]*\"|'[^']*')"
        + "|(?<CLOSETAG>/?\\s*>)"
    );

    /**
     * Calcola gli StyleSpans per il testo XML dato.
     * Da chiamare in un thread e applicare con Platform.runLater().
     */
    public static StyleSpans<Collection<String>> computeHighlighting(String text) {
        Matcher matcher = XML_PATTERN.matcher(text);
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
        int lastEnd = 0;

        while (matcher.find()) {
            String styleClass = getStyleClass(matcher);
            int styleStart = matcher.start();
            int styleEnd   = matcher.end();
            if (matcher.group("ATTRVALUE") != null) {
                styleStart = matcher.start("ATTRVALUE");
            }
            spansBuilder.add(Collections.emptyList(), styleStart - lastEnd);
            spansBuilder.add(Collections.singleton(styleClass), styleEnd - styleStart);
            lastEnd = matcher.end();
        }
        spansBuilder.add(Collections.emptyList(), text.length() - lastEnd);
        return spansBuilder.create();
    }

    private static String getStyleClass(Matcher m) {
        if (m.group("COMMENT")  != null) return "xml-comment";
        if (m.group("CDATA")    != null) return "xml-cdata";
        if (m.group("XMLDECL")  != null) return "xml-decl";
        if (m.group("TAG")      != null) return "xml-tag";
        if (m.group("ATTRNAME") != null) return "xml-attr-name";
        if (m.group("ATTRVALUE")!= null) return "xml-attr-value";
        if (m.group("CLOSETAG") != null) return "xml-tag";
        return "xml-text";
    }

    /** Testo oltre questa dimensione non viene colorato (evita freeze su file enormi). */
    private static final int MAX_HIGHLIGHT_CHARS = 500_000;

    /**
     * Collega il syntax highlighting in tempo reale a una CodeArea.
     * Usa un ScheduledExecutorService per debounce senza dipendenze da org.reactfx.
     */
    public static void bind(CodeArea codeArea) {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "xml-highlighter");
            t.setDaemon(true);
            return t;
        });
        AtomicReference<ScheduledFuture<?>> pending = new AtomicReference<>();

        codeArea.textProperty().addListener((obs, oldText, newText) -> {
            ScheduledFuture<?> prev = pending.get();
            if (prev != null) prev.cancel(false);
            if (newText.length() > MAX_HIGHLIGHT_CHARS) return;
            pending.set(executor.schedule(() -> {
                StyleSpans<Collection<String>> spans = computeHighlighting(newText);
                Platform.runLater(() -> {
                    if (codeArea.getLength() > 0) {
                        codeArea.setStyleSpans(0, spans);
                    }
                });
            }, 150, TimeUnit.MILLISECONDS));
        });
    }
}
