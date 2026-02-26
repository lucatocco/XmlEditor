# XmlEditor — JavaFX + Maven

Editor XML completo con tutte le funzionalità di un notepad professionale.

---

## Avvio rapido

```bash
cd XmlEditor
mvn javafx:run
```

---

## Funzionalità

### File
| Azione | Tasto |
|--------|-------|
| Nuovo documento | Ctrl+N |
| Apri XML/XSD | Ctrl+O |
| Salva | Ctrl+S |
| Salva come | Ctrl+Shift+S |
| File recenti | Menu File → Recenti (persistiti tra sessioni) |
| Chiudi | Menu File → Chiudi |

### Modifica (notepad completo)
| Azione | Tasto |
|--------|-------|
| Annulla | Ctrl+Z |
| Ripeti | Ctrl+Shift+Z |
| Taglia | Ctrl+X |
| Copia | Ctrl+C |
| Incolla | Ctrl+V |
| Seleziona tutto | Ctrl+A |
| Cerca (con nav avanti/indietro) | Ctrl+F |
| Vai a riga | Ctrl+G |

### Visualizza
- **A capo automatico** (word wrap) — toggle nel menu Visualizza
- **Encoding** — ricarica il file con UTF-8, ISO-8859-1, UTF-16, Windows-1252

### XML
| Azione | Tasto |
|--------|-------|
| Pretty Print | Ctrl+P |
| Carica schema XSD | Menu XML |
| Valida XSD | Ctrl+E |

### Indicatori
- **Asterisco nel titolo** (`* nome.xml`) quando ci sono modifiche non salvate
- **Statusbar** con riga:colonna corrente, totale righe, encoding
- **Dialogo di conferma** prima di chiudere/aprire con modifiche non salvate
- **Log colorato** (verde/arancio/rosso) per tutte le operazioni
- **Click su errore XSD** → salta alla riga dell'errore nell'editor

---

## Struttura progetto

```
XmlEditor/
├── pom.xml
└── src/main/
    ├── java/
    │   ├── module-info.java
    │   └── it/touchinformatica/xmleditor/
    │       ├── MainApp.java
    │       ├── model/XmlDocument.java
    │       ├── service/XmlService.java          ← pretty print + validazione (solo JDK)
    │       ├── util/
    │       │   ├── XmlSyntaxHighlighter.java
    │       │   └── RecentFilesManager.java      ← NUOVO: persistenza file recenti
    │       ├── view/
    │       │   ├── MainStage.java               ← menu completo + toolbar
    │       │   ├── EditorPane.java              ← undo/redo/cut/copy/paste/selectAll/wordwrap
    │       │   ├── TreePane.java
    │       │   ├── SearchBar.java
    │       │   └── LogPane.java                 ← log + statusbar riga:col
    │       └── controller/
    │           └── MainController.java          ← nuovo/apri/salva/encoding/goToLine/confirm
    └── resources/css/editor.css                 ← tema dark + stili menu/dialog
```

---

## Prerequisiti

```bash
# Java 21+
java -version

# Maven 3.8+
mvn -version

# Avvio
mvn javafx:run
```
