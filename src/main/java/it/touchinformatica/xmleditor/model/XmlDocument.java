package it.touchinformatica.xmleditor.model;

import java.nio.file.Path;

/**
 * Modello che rappresenta il documento XML correntemente aperto.
 */
public class XmlDocument {

    private Path filePath;
    private String content;
    private boolean modified;

    public XmlDocument(Path filePath, String content) {
        this.filePath = filePath;
        this.content = content;
        this.modified = false;
    }

    public Path getFilePath() { return filePath; }
    public void setFilePath(Path filePath) { this.filePath = filePath; }

    public String getContent() { return content; }
    public void setContent(String content) {
        this.content = content;
        this.modified = true;
    }

    public boolean isModified() { return modified; }
    public void markSaved() { this.modified = false; }

    public String getFileName() {
        return filePath != null ? filePath.getFileName().toString() : "Nuovo documento";
    }
}
