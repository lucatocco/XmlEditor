#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────
#  XML Editor — generazione pacchetto DEB con jpackage
#
#  Prerequisiti:
#    - JDK 21+ (jpackage è incluso)
#    - Maven
#    - fakeroot     sudo apt install fakeroot
#
#  Uso:
#    ./install/build-deb.sh
#
#  Output:
#    target/installer/xmleditor_<versione>_amd64.deb
#
#  Il pacchetto si porta dentro il runtime Java, quindi sulla macchina di
#  destinazione non serve installare Java. Una volta installato compare fra le
#  applicazioni e apre i file .xml e .xsd con un doppio clic.
# ─────────────────────────────────────────────────────────────
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

RED='\033[0;31m'; GREEN='\033[0;32m'; CYAN='\033[0;36m'; BOLD='\033[1m'; RESET='\033[0m'
info()    { echo -e "${CYAN}▶ $*${RESET}"; }
success() { echo -e "${GREEN}✔ $*${RESET}"; }
error()   { echo -e "${RED}✖ $*${RESET}" >&2; exit 1; }

cd "$PROJECT_DIR"

# ── Prerequisiti ─────────────────────────────────────────────
command -v jpackage >/dev/null || error "jpackage non trovato: serve un JDK 21 o superiore"
command -v mvn      >/dev/null || error "Maven non trovato:\n  sudo apt install maven"
command -v fakeroot >/dev/null || error "fakeroot non trovato:\n  sudo apt install fakeroot"

# ── Versione ─────────────────────────────────────────────────
VERSION=$(sed -n 's|^[[:space:]]*<version>\(.*\)</version>.*|\1|p' pom.xml | head -1)
[ -n "$VERSION" ] || error "Impossibile leggere la versione da pom.xml"

echo -e "${BOLD}"
echo "╔══════════════════════════════════════════╗"
echo "║   Build DEB — XML Editor v${VERSION}          ║"
echo "╚══════════════════════════════════════════╝"
echo -e "${RESET}"

# ── Build ────────────────────────────────────────────────────
info "Compilazione e test…"
mvn clean package -q -P linux
success "target/XmlEditor-${VERSION}.jar"

INPUT="target/jpackage-input"
OUT="target/installer"
rm -rf "$INPUT" "$OUT"
mkdir -p "$INPUT" "$OUT"
cp "target/XmlEditor-${VERSION}.jar" "$INPUT/"

# ── Pacchetto ────────────────────────────────────────────────
info "Generazione del pacchetto DEB…"
jpackage \
    --type         deb \
    --name         "XML Editor" \
    --app-version  "$VERSION" \
    --vendor       "Touch Informatica S.r.l.s." \
    --description  "XML file editor with syntax highlighting and XSD validation" \
    --input        "$INPUT" \
    --main-jar     "XmlEditor-${VERSION}.jar" \
    --main-class   it.touchinformatica.xmleditor.MainApp \
    --module-path  target/javafx-libs \
    --add-modules  javafx.controls \
    --java-options "-Dfile.encoding=UTF-8" \
    --icon         install/xmleditor.png \
    --linux-shortcut \
    --linux-menu-group "Utility;TextEditor" \
    --linux-package-name xmleditor \
    --linux-deb-maintainer lucatocco@gmail.com \
    --linux-app-category utils \
    --file-associations install/linux-xml.properties \
    --file-associations install/linux-xsd.properties \
    --resource-dir install/jpackage \
    --dest         "$OUT"

DEB=$(ls "$OUT"/*.deb | head -1)
success "$(basename "$DEB")  ($(du -h "$DEB" | cut -f1))"

echo
echo "Per installarlo:"
echo -e "    ${BOLD}sudo apt install $(realpath "$DEB")${RESET}"
echo "Per rimuoverlo:"
echo -e "    ${BOLD}sudo apt remove xmleditor${RESET}"
