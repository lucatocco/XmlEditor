package it.touchinformatica.xmleditor.service;

import org.w3c.dom.Document;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Servizio per operazioni XML: pretty print e validazione XSD.
 * Usa solo librerie JDK standard — nessuna dipendenza esterna.
 */
public class XmlService {

    // ──────────────────────────────────────────────
    // PRETTY PRINT
    // ──────────────────────────────────────────────

    /**
     * Formatta il testo XML con indentazione a 4 spazi.
     *
     * @param xmlText testo XML grezzo
     * @return testo formattato
     * @throws Exception in caso di XML malformato
     */
    public String prettyPrint(String xmlText) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        // Protezione XXE
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);

        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new ByteArrayInputStream(xmlText.getBytes(StandardCharsets.UTF_8)));
        doc.normalize();

        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

        StringWriter sw = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(sw));
        return sw.toString();
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
    }

    /**
     * Singolo errore di validazione con riga e colonna.
     */
    public record ValidationError(int line, int column, String message, boolean fatal) {}

    /**
     * Valida il testo XML contro lo schema XSD fornito.
     *
     * @param xmlText   testo XML da validare
     * @param xsdPath   path del file .xsd
     * @return ValidationResult con lista errori
     */
    public ValidationResult validate(String xmlText, Path xsdPath) {
        List<ValidationError> errors = new ArrayList<>();

        try {
            SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            Schema schema = schemaFactory.newSchema(xsdPath.toFile());
            Validator validator = schema.newValidator();

            // Raccoglie tutti gli errori senza fermarsi al primo
            validator.setErrorHandler(new org.xml.sax.ErrorHandler() {
                @Override
                public void warning(SAXParseException e) {
                    errors.add(new ValidationError(e.getLineNumber(), e.getColumnNumber(), e.getMessage(), false));
                }
                @Override
                public void error(SAXParseException e) {
                    errors.add(new ValidationError(e.getLineNumber(), e.getColumnNumber(), e.getMessage(), false));
                }
                @Override
                public void fatalError(SAXParseException e) {
                    errors.add(new ValidationError(e.getLineNumber(), e.getColumnNumber(), e.getMessage(), true));
                }
            });

            validator.validate(new StreamSource(
                new ByteArrayInputStream(xmlText.getBytes(StandardCharsets.UTF_8))
            ));

            return errors.isEmpty()
                ? ValidationResult.ok()
                : new ValidationResult(false, errors);

        } catch (SAXException | IOException e) {
            errors.add(new ValidationError(0, 0, "Errore schema: " + e.getMessage(), true));
            return new ValidationResult(false, errors);
        }
    }

    // ──────────────────────────────────────────────
    // PARSE DOM (per il TreeView)
    // ──────────────────────────────────────────────

    /**
     * Parsa il testo XML e restituisce il Document DOM.
     * Usato dal TreePanel per costruire l'albero.
     */
    public Document parse(String xmlText) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new ByteArrayInputStream(xmlText.getBytes(StandardCharsets.UTF_8)));
    }
}
