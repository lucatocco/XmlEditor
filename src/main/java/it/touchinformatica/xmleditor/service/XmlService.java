package it.touchinformatica.xmleditor.service;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.*;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Servizio per operazioni XML: pretty print e validazione XSD.
 * Usa solo librerie JDK standard — nessuna dipendenza esterna.
 *
 * <p><b>Nota sull'encoding</b>: tutti i parser leggono il testo da un
 * {@link StringReader}, mai da byte. In questo modo la dichiarazione
 * {@code <?xml encoding="…"?>} viene ignorata e i caratteri accentati di un
 * documento non-UTF-8 (es. ISO-8859-1) non vengono re-interpretati male.</p>
 */
public class XmlService {

    // ──────────────────────────────────────────────
    // PRETTY PRINT
    // ──────────────────────────────────────────────

    /**
     * Formatta il testo XML con indentazione a 4 spazi, dichiarando UTF-8.
     *
     * @param xmlText testo XML grezzo
     * @return testo formattato
     * @throws Exception in caso di XML malformato
     */
    public String prettyPrint(String xmlText) throws Exception {
        return prettyPrint(xmlText, StandardCharsets.UTF_8);
    }

    /**
     * Formatta il testo XML con indentazione a 4 spazi.
     *
     * @param xmlText testo XML grezzo
     * @param charset encoding con cui il file verrà salvato: viene dichiarato
     *                nel prologo, così la dichiarazione resta coerente col file
     * @return testo formattato
     * @throws Exception in caso di XML malformato
     */
    public String prettyPrint(String xmlText, Charset charset) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        // DOCTYPE ammesso, ma il secure processing blocca entità esterne (XXE)
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);

        DocumentBuilder builder = factory.newDocumentBuilder();
        // Senza handler il parser stampa l'errore su stderr prima ancora che
        // il chiamante possa gestirlo: qui l'eccezione basta e avanza.
        builder.setErrorHandler(new org.xml.sax.ErrorHandler() {
            @Override public void warning(SAXParseException e) { }
            @Override public void error(SAXParseException e) throws SAXException { throw e; }
            @Override public void fatalError(SAXParseException e) throws SAXException { throw e; }
        });
        Document doc = builder.parse(new InputSource(new StringReader(xmlText)));
        doc.normalize();

        // L'indentazione la ricostruiamo noi nel DOM invece di lasciarla al
        // Transformer: quella del JDK si somma a quella già presente (righe vuote
        // che crescono a ogni pretty print) e spezza il contenuto misto.
        reindent(doc.getDocumentElement(), 0);

        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, charset.name());
        transformer.setOutputProperty(OutputKeys.INDENT, "no");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");

        StringWriter sw = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(sw));

        // Dichiarazione scritta a mano: quella del Transformer riporterebbe
        // l'encoding dichiarato nel file di partenza, non quello di salvataggio.
        return xmlDeclaration(doc, charset) + "\n" + sw.toString().strip() + "\n";
    }

    /** Prologo coerente con l'encoding con cui il file verrà effettivamente scritto. */
    private static String xmlDeclaration(Document doc, Charset charset) {
        String version = doc.getXmlVersion() != null ? doc.getXmlVersion() : "1.0";
        return "<?xml version=\"" + version + "\" encoding=\"" + charset.name() + "\""
             + (doc.getXmlStandalone() ? " standalone=\"yes\"" : "")
             + "?>";
    }

    private static final String INDENT_UNIT = "    ";

    /**
     * Riscrive l'indentazione del sottoalbero: elimina i nodi di testo di soli
     * spazi (l'indentazione vecchia) e ne inserisce di nuovi, uno per livello.
     *
     * <p>Restano intatti, perché lì ogni spazio è significativo:</p>
     * <ul>
     *   <li>gli elementi a contenuto misto (testo + tag, es. {@code <p>ciao <b>x</b></p>})</li>
     *   <li>i sottoalberi marcati {@code xml:space="preserve"}</li>
     *   <li>le sezioni CDATA, che non sono nodi TEXT_NODE</li>
     * </ul>
     *
     * @param element elemento da re-indentare
     * @param depth   profondità corrente, 0 per l'elemento radice
     */
    private static void reindent(Node element, int depth) {
        if (element == null || element.getNodeType() != Node.ELEMENT_NODE) return;
        if ("preserve".equals(((Element) element).getAttribute("xml:space"))) return;

        List<Node> children = new ArrayList<>();
        NodeList nodeList = element.getChildNodes();
        for (int i = 0; i < nodeList.getLength(); i++) children.add(nodeList.item(i));

        for (Node child : children) {
            if (child.getNodeType() == Node.TEXT_NODE && !child.getTextContent().isBlank()) {
                return;   // contenuto misto: lasciato esattamente com'è
            }
        }

        List<Node> kept = new ArrayList<>();
        for (Node child : children) {
            if (child.getNodeType() == Node.TEXT_NODE && child.getTextContent().isBlank()) {
                element.removeChild(child);
            } else {
                kept.add(child);
            }
        }
        if (kept.isEmpty()) return;

        Document doc = element.getOwnerDocument();
        String childIndent = "\n" + INDENT_UNIT.repeat(depth + 1);
        for (Node child : kept) {
            element.insertBefore(doc.createTextNode(childIndent), child);
            reindent(child, depth + 1);
        }
        element.appendChild(doc.createTextNode("\n" + INDENT_UNIT.repeat(depth)));
    }

    // ──────────────────────────────────────────────
    // VALIDAZIONE XSD
    // ──────────────────────────────────────────────

    /**
     * Risultato della validazione XSD.
     */
    public record ValidationResult(boolean valid, List<ValidationError> errors) {
        public static ValidationResult ok() {
            return new ValidationResult(true, List.of());
        }

        /**
         * Indica se a non funzionare è lo schema stesso: in quel caso il documento
         * non è stato validato e gli errori riguardano i file XSD, non il documento.
         */
        public boolean schemaBroken() {
            return errors.stream().anyMatch(ValidationError::inSchema);
        }
    }

    /** Gravità di un problema di validazione. */
    public enum Severity { WARNING, ERROR, FATAL }

    /**
     * Singolo problema di validazione con riga e colonna.
     *
     * @param line       riga, oppure 0 se non nota
     * @param column     colonna, oppure 0 se non nota
     * @param schemaFile nome del file XSD in cui si trova il problema (lo schema
     *                   scelto o uno da esso importato), oppure null se il problema
     *                   è nel documento: riga e colonna si riferiscono a quel file
     */
    public record ValidationError(int line, int column, String message,
                                  Severity severity, String schemaFile) {

        public boolean fatal() {
            return severity == Severity.FATAL;
        }

        public boolean inSchema() {
            return schemaFile != null;
        }
    }

    /**
     * Valida il testo XML contro lo schema XSD fornito.
     *
     * <p>Prima si compila lo schema raccogliendone tutti i problemi, compresi gli
     * avvisi: un {@code xs:import} di un file che non c'è è solo un avviso, ma è
     * la causa dei «tipo non risolto» che seguono e senza di esso quegli errori
     * sono incomprensibili. Se lo schema ha errori il documento non viene
     * validato: il risultato contro uno schema incompleto non avrebbe senso.</p>
     *
     * @param xmlText   testo XML da validare
     * @param xsdPath   path del file .xsd
     * @return ValidationResult con lista errori
     */
    public ValidationResult validate(String xmlText, Path xsdPath) {
        String xsdName = xsdPath.getFileName() != null
            ? xsdPath.getFileName().toString() : xsdPath.toString();

        // 1. Compilazione dello schema
        List<ValidationError> schemaProblems = new ArrayList<>();
        Schema schema;
        try {
            SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            schemaFactory.setErrorHandler(new ErrorHandler() {
                @Override
                public void warning(SAXParseException e) {
                    schemaProblems.add(schemaProblem(e, Severity.WARNING, xsdName));
                }
                @Override
                public void error(SAXParseException e) {
                    // Per il documento ogni errore dello schema è bloccante
                    schemaProblems.add(schemaProblem(e, Severity.FATAL, xsdName));
                }
                @Override
                public void fatalError(SAXParseException e) throws SAXException {
                    schemaProblems.add(schemaProblem(e, Severity.FATAL, xsdName));
                    throw e;
                }
            });
            schema = schemaFactory.newSchema(xsdPath.toFile());
        } catch (SAXException e) {
            // Di norma è l'errore appena registrato dall'handler
            if (schemaProblems.stream().noneMatch(ValidationError::fatal)) {
                schemaProblems.add(new ValidationError(0, 0, e.getMessage(), Severity.FATAL, xsdName));
            }
            return new ValidationResult(false, schemaProblems);
        }
        if (schemaProblems.stream().anyMatch(ValidationError::fatal)) {
            return new ValidationResult(false, schemaProblems);
        }

        // 2. Validazione del documento: raccoglie tutti gli errori senza fermarsi al primo
        List<ValidationError> errors = new ArrayList<>();
        try {
            Validator validator = schema.newValidator();
            validator.setErrorHandler(new ErrorHandler() {
                @Override
                public void warning(SAXParseException e) {
                    errors.add(documentProblem(e, Severity.WARNING));
                }
                @Override
                public void error(SAXParseException e) {
                    errors.add(documentProblem(e, Severity.ERROR));
                }
                @Override
                public void fatalError(SAXParseException e) {
                    errors.add(documentProblem(e, Severity.FATAL));
                }
            });

            validator.validate(new StreamSource(new StringReader(xmlText)));

        } catch (SAXException | IOException e) {
            // Un errore fatale (documento non ben formato) è già stato registrato
            // dall'handler: il parser poi si interrompe comunque con un'eccezione
            if (errors.stream().noneMatch(ValidationError::fatal)) {
                errors.add(new ValidationError(0, 0, e.getMessage(), Severity.FATAL, null));
            }
        }

        return errors.isEmpty()
            ? ValidationResult.ok()
            : new ValidationResult(false, errors);
    }

    private static ValidationError documentProblem(SAXParseException e, Severity severity) {
        return new ValidationError(Math.max(0, e.getLineNumber()), Math.max(0, e.getColumnNumber()),
            e.getMessage(), severity, null);
    }

    private static ValidationError schemaProblem(SAXParseException e, Severity severity, String mainSchema) {
        return new ValidationError(Math.max(0, e.getLineNumber()), Math.max(0, e.getColumnNumber()),
            e.getMessage(), severity, schemaFileName(e.getSystemId(), mainSchema));
    }

    /**
     * Nome del file XSD a cui si riferisce un problema: può essere lo schema
     * scelto o uno importato, e conta saperlo per andare a correggerlo.
     */
    private static String schemaFileName(String systemId, String fallback) {
        if (systemId == null || systemId.isBlank()) return fallback;
        try {
            String path = URI.create(systemId).getPath();   // decodifica %20 & co.
            if (path != null) {
                String name = path.substring(path.lastIndexOf('/') + 1);
                if (!name.isEmpty()) return name;
            }
        } catch (IllegalArgumentException e) {
            // systemId non è un URI valido: si mostra così com'è
        }
        return systemId;
    }
}
