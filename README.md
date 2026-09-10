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
| Open by dropping files onto the window | drag and drop |
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
- **Updates** — at startup the application asks GitHub whether a newer release
  exists and, if so, shows its release notes with a button that opens the download
  page. It checks at most once a day, says nothing when there is nothing to say,
  and stays silent when the network is unavailable. Help → Check for Updates runs
  the same check on demand and always reports the outcome; Settings → Check for
  updates at startup turns the automatic one off.

  The application never downloads or installs anything by itself: installing needs
  administrator rights, and asking for them to self-update would be worse than the
  problem it solves.

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

**The schema is chosen when the file is opened**, not when you press Validate, so
validation usually runs without asking anything. The folder is indexed once — every
schema's `targetNamespace` and the root elements it declares — and the document is
matched against it, from the most reliable criterion to the weakest:

1. a schema loaded manually for the current session (Load Single XSD)
2. the file named in the document's `xsi:schemaLocation` / `noNamespaceSchemaLocation`
3. **the schema whose `targetNamespace` is the namespace of the document's root
   element** — an exact match, version included
4. **a schema declaring the document's root element** as a global element; if several
   do, the last by name wins, which is the most recent version
5. a schema with the same base name as the XML file (`invoice.xml` → `invoice.xsd`)
6. only if nothing matched, a dialog that shows the document's root element and
   namespace and lets you pick a schema from the folder **or load one from
   anywhere else** — nothing is preselected, because a document of a format the
   folder does not cover has no right answer in that list

The log says which schema was selected and why. For a folder of CBI schemas, where
file names carry the version (`CBIBdyPaymentRequest.00.04.00.xsd`) and the namespace
repeats it (`urn:CBI:xsd:CBIBdyPaymentRequest.00.04.00`), criterion 3 identifies the
right schema even when the XML file is named after the transaction rather than the
format. The XSD folder is remembered across restarts.

### Indicators
- **Asterisk in the tab title** (`* file.xml`) when there are unsaved changes
- **Status bar** with current line:column, total lines, encoding and line ending
- **Confirmation dialog** before closing or replacing a document with unsaved changes
- **Color-coded log** (green/orange/red) for every operation
- **Click a node in the tree** → selects that element's opening tag in the editor and
  scrolls to it, so the cursor lands on the tag you clicked rather than at the start
  of the line
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
│   ├── build-deb.sh              ← local DEB build with jpackage
│   ├── install-user.sh           ← install under ~/.local, no root needed
│   ├── uninstall-user.sh
│   ├── build-msi.ps1             ← local MSI build with jpackage
│   ├── install.sh                ← build from source and install (Linux)
│   ├── uninstall.sh
│   ├── install-windows.ps1
│   ├── bump-version.sh           ← version bump + git tag + release
│   ├── jpackage/                 ← desktop entry template used by jpackage
│   ├── linux-xml.properties      ← .xml file association
│   ├── linux-xsd.properties      ← .xsd file association
│   ├── xmleditor.desktop
│   ├── xmleditor.png             ← icon for the Linux package
│   └── xmleditor.svg
├── .github/workflows/
│   ├── build-packages.yml        ← MSI and DEB builds (also reusable)
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

Ready-made installers for both platforms are attached to every
[release](https://github.com/lucatocco/XmlEditor/releases). They bundle a Java
runtime, so **Java does not need to be installed** on the target machine.

**Linux** — download the `.deb` and install it:

```bash
sudo apt install ./xmleditor_<version>_amd64.deb
sudo apt remove xmleditor            # to remove it
```

The application then appears among the installed applications, under Accessories,
and opens `.xml` and `.xsd` files on double click. The package is built on Ubuntu
22.04, so it installs on Ubuntu 22.04 and later, Debian 12 and Linux Mint 21/22.

**Linux without root** — on a machine where you have no administrator rights,
install under your home directory instead:

```bash
./install/install-user.sh                     # downloads the latest release
./install/install-user.sh package.tar.gz      # or uses an archive you already have
./install/install-user.sh --build             # or builds from source (JDK 21 + Maven)
./install/uninstall-user.sh                   # to remove it
```

Everything goes under `~/.local` following the XDG conventions: the application in
`~/.local/share/xmleditor`, a `xmleditor` command in `~/.local/bin`, and a menu entry
in `~/.local/share/applications`. The archive carries its own Java runtime, so nothing
has to be installed system-wide.

**Windows** — download the `.msi` and run it. Windows shows a SmartScreen warning
because the installer is not code-signed: choose *More info* → *Run anyway*.

### Building the packages yourself

```bash
./install/build-deb.sh                                              # Linux, needs fakeroot
powershell -ExecutionPolicy Bypass -File install\build-msi.ps1       # Windows, needs WiX
```

`install/install.sh` is still there to build from source and install into
`/opt/xmleditor`; it requires JDK 21 and Maven on the machine.

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
`pom.xml`, builds the Windows and Linux packages in parallel, and publishes a GitHub
Release with both installers attached. The Linux job also asserts that the package
carries a valid desktop entry, so a release can never ship without its menu shortcut.

Without `--push` nothing leaves your machine: the script prints the commands to run.

Versions must be `MAJOR.MINOR.PATCH` — jpackage rejects suffixes such as `-SNAPSHOT`
in the MSI's `--app-version`.

---

Developed by Luca Tocco — Touch Informatica S.r.l.s. — <https://www.touchinformatica.it>
