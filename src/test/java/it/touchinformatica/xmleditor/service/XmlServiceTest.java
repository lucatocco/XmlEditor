package it.touchinformatica.xmleditor.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test di {@link XmlService}.
 *
 * <p>Coprono i due modi in cui il pretty print ha corrotto documenti reali:
 * l'indentazione che si sommava a ogni esecuzione e i caratteri accentati
 * rovinati sui file non UTF-8.</p>
 */
class XmlServiceTest {

    private final XmlService service = new XmlService();

    // ──────────────────────────────────────────────
    // PRETTY PRINT — INDENTAZIONE
    // ──────────────────────────────────────────────

    @Nested
    @DisplayName("Pretty print")
    class PrettyPrint {

        @Test
        @DisplayName("ripetuto sullo stesso documento dà sempre lo stesso risultato")
        void isIdempotent() throws Exception {
            String source = "<?xml version=\"1.0\"?>\n<root>\n    <a>1</a>\n    <b>2</b>\n</root>";

            String first  = service.prettyPrint(source);
            String second = service.prettyPrint(first);
            String third  = service.prettyPrint(second);

            assertEquals(first, second, "la seconda passata ha cambiato il documento");
            assertEquals(second, third, "la terza passata ha cambiato il documento");
        }

        @Test
        @DisplayName("non accumula righe vuote su un documento già indentato")
        void doesNotAddBlankLines() throws Exception {
            String source = "<?xml version=\"1.0\"?>\n<root>\n    <a>1</a>\n    <b>2</b>\n</root>";

            long lines = service.prettyPrint(source).lines().filter(l -> !l.isBlank()).count();

            // dichiarazione + <root> + <a> + <b> + </root>
            assertEquals(5, lines);
        }

        @Test
        @DisplayName("indenta un documento compatto")
        void indentsCompactDocument() throws Exception {
            String result = service.prettyPrint("<r><a x=\"1\">v</a><b><c/></b></r>");

            assertTrue(result.contains("\n    <a x=\"1\">v</a>"), result);
            assertTrue(result.contains("\n        <c/>"), result);
        }

        @Test
        @DisplayName("lascia intatto il contenuto misto")
        void preservesMixedContent() throws Exception {
            String result = service.prettyPrint("<r><p>ciao <b>mondo</b> bello</p></r>");

            assertTrue(result.contains("<p>ciao <b>mondo</b> bello</p>"),
                "il testo attorno al tag è stato spezzato:\n" + result);
        }

        @Test
        @DisplayName("lascia intatte le sezioni CDATA")
        void preservesCdata() throws Exception {
            String result = service.prettyPrint("<r><d><![CDATA[   spazi   ]]></d></r>");

            assertTrue(result.contains("<![CDATA[   spazi   ]]>"), result);
        }

        @Test
        @DisplayName("rispetta xml:space=\"preserve\"")
        void preservesExplicitWhitespace() throws Exception {
            String result = service.prettyPrint("<r><pre xml:space=\"preserve\">  a  b  </pre></r>");

            assertTrue(result.contains("<pre xml:space=\"preserve\">  a  b  </pre>"), result);
        }

        @Test
        @DisplayName("mantiene gli attributi e i namespace")
        void preservesNamespaces() throws Exception {
            String result = service.prettyPrint("<ns:r xmlns:ns=\"http://x\"><ns:a>1</ns:a></ns:r>");

            assertTrue(result.contains("xmlns:ns=\"http://x\""), result);
            assertTrue(result.contains("<ns:a>1</ns:a>"), result);
        }

        @Test
        @DisplayName("un documento malformato solleva un'eccezione")
        void rejectsMalformedXml() {
            assertThrows(Exception.class, () -> service.prettyPrint("<r><a></r>"));
        }
    }

    // ──────────────────────────────────────────────
    // PRETTY PRINT — ENCODING
    // ──────────────────────────────────────────────

    @Nested
    @DisplayName("Encoding")
    class Encodings {

        private static final Charset LATIN1 = StandardCharsets.ISO_8859_1;

        @Test
        @DisplayName("gli accenti sopravvivono a un documento dichiarato ISO-8859-1")
        void keepsAccentedCharacters() throws Exception {
            String source = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><r><n>Città perché è</n></r>";

            String result = service.prettyPrint(source, LATIN1);

            assertTrue(result.contains("Città perché è"),
                "i caratteri accentati sono stati re-interpretati:\n" + result);
        }

        @Test
        @DisplayName("la dichiarazione riporta l'encoding di salvataggio, non quello di partenza")
        void declarationFollowsSaveCharset() throws Exception {
            String source = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><r><n>x</n></r>";

            assertTrue(service.prettyPrint(source, LATIN1).startsWith("<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>"));
            assertTrue(service.prettyPrint(source, StandardCharsets.UTF_8).startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"));
        }

        @Test
        @DisplayName("i caratteri non rappresentabili diventano riferimenti numerici")
        void escapesUnrepresentableCharacters() throws Exception {
            // L'euro non esiste in ISO-8859-1: senza escape andrebbe perso al salvataggio
            String result = service.prettyPrint("<r><n>100 €</n></r>", LATIN1);

            assertTrue(result.contains("&#8364;"), result);
            assertFalse(result.contains("€"), result);
        }

        @Test
        @DisplayName("il documento sopravvive al giro completo su disco in ISO-8859-1")
        void survivesDiskRoundTrip(@TempDir Path dir) throws Exception {
            String source = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><r><n>Città € 100</n></r>";
            Path file = dir.resolve("fattura.xml");

            Files.writeString(file, service.prettyPrint(source, LATIN1), LATIN1);
            String reread = Files.readString(file, LATIN1);

            assertEquals(service.prettyPrint(source, LATIN1), reread);
            assertTrue(service.prettyPrint(reread, LATIN1).contains("Città"), reread);
        }

        @Test
        @DisplayName("conserva standalone=\"yes\"")
        void keepsStandalone() throws Exception {
            String result = service.prettyPrint("<?xml version=\"1.0\" standalone=\"yes\"?><r><a/></r>");

            assertTrue(result.contains("standalone=\"yes\""), result);
        }
    }

    // ──────────────────────────────────────────────
    // VALIDAZIONE XSD
    // ──────────────────────────────────────────────

    @Nested
    @DisplayName("Validazione XSD")
    class Validation {

        private Path writeSchema(Path dir) throws Exception {
            Path xsd = dir.resolve("nota.xsd");
            Files.writeString(xsd, """
                <?xml version="1.0"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:element name="nota">
                    <xs:complexType>
                      <xs:sequence>
                        <xs:element name="testo" type="xs:string"/>
                      </xs:sequence>
                    </xs:complexType>
                  </xs:element>
                </xs:schema>
                """);
            return xsd;
        }

        @Test
        @DisplayName("un documento conforme è valido")
        void acceptsValidDocument(@TempDir Path dir) throws Exception {
            var result = service.validate("<nota><testo>ciao</testo></nota>", writeSchema(dir));

            assertTrue(result.valid(), () -> "errori inattesi: " + result.errors());
            assertTrue(result.errors().isEmpty());
        }

        @Test
        @DisplayName("un documento non conforme riporta riga e messaggio")
        void reportsErrorPosition(@TempDir Path dir) throws Exception {
            var result = service.validate("<nota><sbagliato>x</sbagliato></nota>", writeSchema(dir));

            assertFalse(result.valid());
            assertFalse(result.errors().isEmpty());
            assertTrue(result.errors().get(0).line() > 0, "riga non riportata");
            assertNotNull(result.errors().get(0).message());
        }

        @Test
        @DisplayName("gli accenti non fanno fallire la validazione di un documento ISO-8859-1")
        void validatesLatin1Document(@TempDir Path dir) throws Exception {
            String xml = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><nota><testo>Città</testo></nota>";

            var result = service.validate(xml, writeSchema(dir));

            assertTrue(result.valid(), () -> "errori inattesi: " + result.errors());
        }

        @Test
        @DisplayName("uno schema inesistente riporta un errore invece di sollevare un'eccezione")
        void handlesMissingSchema(@TempDir Path dir) {
            var result = service.validate("<nota/>", dir.resolve("assente.xsd"));

            assertFalse(result.valid());
            assertTrue(result.errors().get(0).fatal());
        }
    }
}
