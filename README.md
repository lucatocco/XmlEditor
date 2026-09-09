# XmlEditor — JavaFX + Maven

A full-featured XML editor: tabs, syntax highlighting, tree view, XSD validation
and pretty printing.

---

## Quick start

```bash
cd XmlEditor
mvn javafx:run
```

---

## Features

### Tabs
| Action | Shortcut |
|--------|----------|
| New tab | Ctrl+T |
| Close tab | Ctrl+W |
| Next tab | Ctrl+Tab |
| Previous tab | Ctrl+Shift+Tab |

Each tab is an independent session with its own editor, tree, log and document state.

### File
| Action | Shortcut |
|--------|----------|
| New document | Ctrl+N |
| Open XML/XSD/TXT (multiple files at once) | Ctrl+O |
| Save | Ctrl+S |
| Save As | Ctrl+Shift+S |
| Recent files | File → Recent Files (persisted across sessions) |
| Exit | Alt+F4 |

### Edit
| Action | Shortcut |
|--------|----------|
| Undo | Ctrl+Z |
| Redo | Ctrl+Shift+Z |
| Cut | Ctrl+X |
| Copy | Ctrl+C |
| Paste | Ctrl+V |
| Select all | Ctrl+A |
| Find (with previous/next navigation) | Ctrl+F |
| Go to line | Ctrl+G |

### Settings
- **Language** — Settings → Language switches the whole interface between
  **English** and **Italiano** immediately, without restarting. The choice is
  remembered; on first run the application follows your system language.

### View
- **Word wrap** — toggle in the View menu, remembered per tab
- **Encoding** — the encoding is detected when a file is opened, from its byte order
  mark or its XML declaration, so a Latin-1 invoice opens with its accents intact.
  The View menu can still reload the file as UTF-8, ISO-8859-1, UTF-16 or Windows-1252.

### XML
| Action | Shortcut |
|--------|----------|
| Pretty Print | Ctrl+P |
| Validate against XSD | Ctrl+E |
| Set XSD folder | XML menu |
| Load a single XSD | XML menu |
| XSD folder info | XML menu |

**Pretty Print** rebuilds the indentation from scratch, so running it repeatedly always
yields the same result. Mixed content (`<p>text <b>tag</b> text</p>`), CDATA sections and
subtrees marked `xml:space="preserve"` are left untouched, and the XML declaration is
rewritten to match the encoding the file will actually be saved with.

**XSD validation** picks the schema in this order:

1. a schema loaded manually for the current session (Load Single XSD)
2. the file named in the document's `xsi:schemaLocation` / `noNamespaceSchemaLocation`,
   looked up in the configured XSD folder
3. a schema with the same base name as the XML file (`invoice.xml` → `invoice.xsd`)
4. otherwise, a picker listing every schema in the folder

The XSD folder is remembered across restarts.

### Indicators
- **Asterisk in the tab title** (`* file.xml`) when there are unsaved changes
- **Status bar** with current line:column, total lines, encoding and line ending
- **Confirmation dialog** before closing or replacing a document with unsaved changes
- **Color-coded log** (green/orange/red) for every operation
- **Click an XSD error** → jumps to the offending line in the editor
- **Last cursor position** is restored per file
- **The tree follows your edits**, rebuilt shortly after you stop typing
- **Saving is atomic** — the file is written beside the original and moved into place,
  so an interrupted save cannot leave a truncated file behind

---

## Project structure

```
XmlEditor/
├── pom.xml                       ← single source of truth for the version
├── install/
│   ├── install.sh                ← Linux install (builds, installs, desktop entry)
│   ├── uninstall.sh
│   ├── install-windows.ps1
│   ├── build-msi.ps1             ← local MSI build with jpackage
│   ├── bump-version.sh           ← version bump + git tag + release
│   ├── xmleditor.desktop
│   └── xmleditor.svg
├── .github/workflows/
│   ├── build-msi.yml             ← MSI build (also reusable)
│   └── release.yml               ← publishes a GitHub Release on every v* tag
└── src/main/
    ├── java/
    │   ├── module-info.java
    │   └── it/touchinformatica/xmleditor/
    │       ├── MainApp.java
    │       ├── model/XmlDocument.java
    │       ├── service/XmlService.java          ← pretty print + XSD validation (JDK only)
    │       ├── util/
    │       │   ├── AppInfo.java                 ← reads name and version at runtime
    │       │   ├── I18n.java                    ← interface texts, English/Italian
    │       │   ├── XmlSyntaxHighlighter.java
    │       │   ├── RecentFilesManager.java
    │       │   ├── LastPositionManager.java
    │       │   └── XsdFolderManager.java
    │       ├── view/
    │       │   ├── MainStage.java               ← window, menu bar, toolbar, tabs
    │       │   ├── EditorSession.java           ← one self-contained tab
    │       │   ├── EditorPane.java              ← CodeArea, undo/redo, encoding, word wrap
    │       │   ├── TreePane.java
    │       │   ├── SearchBar.java
    │       │   ├── LogPane.java
    │       │   └── StatusBar.java
    │       └── controller/MainController.java   ← one controller per tab
    └── resources/it/touchinformatica/xmleditor/
        ├── app.properties                       ← filtered by Maven, carries the version
        ├── i18n/
        │   ├── messages.properties              ← English (base language)
        │   └── messages_it.properties           ← Italian
        └── css/editor.css
```

Adding a language means adding one `messages_<code>.properties` next to these and one
entry in `I18n.AVAILABLE`; `I18nTest` then checks it against the English catalog.

---

## Requirements

```bash
java -version   # Java 21+
mvn -version    # Maven 3.8+
```

---

## Tests

```bash
mvn test
```

The suite covers the logic that has actually broken documents in the past: pretty
print idempotency, whitespace handling for mixed content and CDATA, encoding of
non-UTF-8 files, XSD schema lookup, and the translation catalogs — a key present in
one language but missing in the other fails the build. Tests also run in CI before
the MSI is built.

---

## Installation

**Linux (Mint / Ubuntu)** — builds, installs to `/opt/xmleditor` and registers the
desktop entry:

```bash
./install/install.sh
./install/uninstall.sh   # to remove it
```

**Windows** — either run the installer script, or build an MSI with jpackage
(requires the WiX Toolset):

```powershell
powershell -ExecutionPolicy Bypass -File install\install-windows.ps1
powershell -ExecutionPolicy Bypass -File install\build-msi.ps1
```

---

## Releasing a new version

The version lives in **`pom.xml` only**. Everything else — the About dialog, the install
scripts, the MSI and the CI pipelines — reads it from there, so a release is a one-line
change:

```bash
./install/bump-version.sh 2.1.0 --push
```

This updates `pom.xml`, commits, creates the `v2.1.0` tag and pushes it. The tag triggers
`.github/workflows/release.yml`, which checks that the tag matches the version in
`pom.xml`, builds the MSI and publishes a GitHub Release with the installer attached.

Without `--push` nothing leaves your machine: the script prints the commands to run.

Versions must be `MAJOR.MINOR.PATCH` — jpackage rejects suffixes such as `-SNAPSHOT`
in the MSI's `--app-version`.

---

Developed by Luca Tocco — Touch Informatica S.r.l.s. — <https://www.touchinformatica.it>
