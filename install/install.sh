#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────
#  XML Editor — script di installazione per Linux Mint / Ubuntu
# ─────────────────────────────────────────────────────────────
set -e

APP_NAME="XML Editor"
APP_ID="xmleditor"
VERSION="2.0.0"
JAR_NAME="XmlEditor-${VERSION}.jar"
INSTALL_DIR="/opt/xmleditor"
BIN_LINK="/usr/local/bin/${APP_ID}"
DESKTOP_DIR="/usr/share/applications"
ICON_DIR="/usr/share/pixmaps"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

# ── Colori ──────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
CYAN='\033[0;36m'; BOLD='\033[1m'; RESET='\033[0m'

info()    { echo -e "${CYAN}▶ $*${RESET}"; }
success() { echo -e "${GREEN}✔ $*${RESET}"; }
warn()    { echo -e "${YELLOW}⚠ $*${RESET}"; }
error()   { echo -e "${RED}✖ $*${RESET}" >&2; exit 1; }

echo -e "${BOLD}"
echo "╔══════════════════════════════════════════╗"
echo "║   Installazione ${APP_NAME} v${VERSION}      ║"
echo "╚══════════════════════════════════════════╝"
echo -e "${RESET}"

# ── Verifica privilegi ───────────────────────────────────────
if [ "$EUID" -ne 0 ]; then
    warn "Richiesti privilegi di amministratore. Riesecuzione con sudo…"
    exec sudo bash "$0" "$@"
fi

# ── Verifica prerequisiti ────────────────────────────────────
info "Verifica prerequisiti…"

if ! command -v java &>/dev/null; then
    error "Java non trovato. Installare OpenJDK 21:\n  sudo apt install openjdk-21-jdk"
fi

JAVA_VER=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d. -f1)
if [ -z "$JAVA_VER" ] || [ "$JAVA_VER" -lt 21 ]; then
    error "Java 21 o superiore richiesto (trovato: $JAVA_VER).\n  sudo apt install openjdk-21-jdk"
fi
success "Java ${JAVA_VER} trovato"

if ! command -v mvn &>/dev/null; then
    error "Maven non trovato. Installare con:\n  sudo apt install maven"
fi
success "Maven trovato"

# ── Build ────────────────────────────────────────────────────
info "Compilazione del progetto…"
cd "$PROJECT_DIR"
# Rimuovi artefatti di build precedenti che potrebbero essere owned da root
rm -rf target/ 2>/dev/null || true
# Esegui la build come utente originale (non root) per evitare
# che i file in target/ diventino di proprietà di root
BUILD_USER="${SUDO_USER:-$(logname 2>/dev/null || echo "$USER")}"
if [ "$BUILD_USER" != "root" ] && [ -n "$BUILD_USER" ]; then
    sudo -u "$BUILD_USER" mvn clean package -q -DskipTests -P linux
else
    mvn clean package -q -DskipTests -P linux
fi
if [ ! -f "target/${JAR_NAME}" ]; then
    error "Build fallita: ${JAR_NAME} non trovato in target/"
fi
success "Build completata → target/${JAR_NAME}"

# ── Installazione file ───────────────────────────────────────
info "Installazione in ${INSTALL_DIR}…"
mkdir -p "$INSTALL_DIR"
cp "target/${JAR_NAME}" "${INSTALL_DIR}/${JAR_NAME}"
success "JAR copiato"

# ── Installazione JavaFX JARs ─────────────────────────────────
info "Installazione librerie JavaFX…"
if [ ! -d "target/javafx-libs" ] || [ -z "$(ls target/javafx-libs/*.jar 2>/dev/null)" ]; then
    error "Librerie JavaFX non trovate in target/javafx-libs/. Eseguire prima: mvn package"
fi
mkdir -p "${INSTALL_DIR}/javafx-libs"
cp target/javafx-libs/*.jar "${INSTALL_DIR}/javafx-libs/"
success "Librerie JavaFX installate"

# ── Installazione classpath JARs (reactfx, richtextfx, saxon, ecc.) ──────────
info "Installazione librerie classpath (richtextfx, reactfx, …)…"
if [ -d "target/classpath-libs" ] && [ -n "$(ls target/classpath-libs/*.jar 2>/dev/null)" ]; then
    mkdir -p "${INSTALL_DIR}/classpath-libs"
    cp target/classpath-libs/*.jar "${INSTALL_DIR}/classpath-libs/"
    success "Librerie classpath installate"
else
    warn "target/classpath-libs vuota o assente — possibile errore a runtime con richtextfx"
fi

# ── Script di avvio ──────────────────────────────────────────
info "Creazione script di avvio ${BIN_LINK}…"
cat > "$BIN_LINK" <<'LAUNCHER'
#!/usr/bin/env bash
# Avvio XML Editor

# Costruisce il classpath aggiuntivo con tutte le dipendenze non-JavaFX
# (reactfx, richtextfx, saxon, …)
CP_EXTRA=""
if [ -d "/opt/xmleditor/classpath-libs" ]; then
    for jar in /opt/xmleditor/classpath-libs/*.jar; do
        [ -f "$jar" ] && CP_EXTRA="${CP_EXTRA}:${jar}"
    done
fi

exec java \
    -Dfile.encoding=UTF-8 \
    -Dsun.stdout.encoding=UTF-8 \
    --module-path "/opt/xmleditor/javafx-libs" \
    --add-modules javafx.controls,javafx.fxml \
    --add-opens=javafx.graphics/com.sun.glass.ui=ALL-UNNAMED \
    --add-opens=javafx.base/com.sun.javafx.runtime=ALL-UNNAMED \
    -cp "/opt/xmleditor/XmlEditor-2.0.0.jar${CP_EXTRA}" \
    it.touchinformatica.xmleditor.MainApp "$@"
LAUNCHER
chmod +x "$BIN_LINK"
success "Script di avvio creato"

# ── Icona ────────────────────────────────────────────────────
info "Installazione icona…"
cp "${SCRIPT_DIR}/xmleditor.svg" "${ICON_DIR}/xmleditor.svg"

# Genera PNG 256x256 se rsvg-convert o inkscape sono disponibili
PNG_INSTALLED=false
if command -v rsvg-convert &>/dev/null; then
    rsvg-convert -w 256 -h 256 "${ICON_DIR}/xmleditor.svg" \
        -o "${ICON_DIR}/xmleditor.png" 2>/dev/null && PNG_INSTALLED=true
elif command -v inkscape &>/dev/null; then
    inkscape --export-type=png --export-width=256 --export-height=256 \
        --export-filename="${ICON_DIR}/xmleditor.png" \
        "${ICON_DIR}/xmleditor.svg" &>/dev/null && PNG_INSTALLED=true
fi

# Installa anche nelle cartelle hicolor per migliore compatibilità
for SIZE in 48 64 128 256; do
    HICO_DIR="/usr/share/icons/hicolor/${SIZE}x${SIZE}/apps"
    mkdir -p "$HICO_DIR"
    if $PNG_INSTALLED && command -v rsvg-convert &>/dev/null; then
        rsvg-convert -w "$SIZE" -h "$SIZE" "${ICON_DIR}/xmleditor.svg" \
            -o "${HICO_DIR}/xmleditor.png" 2>/dev/null || true
    elif $PNG_INSTALLED && command -v inkscape &>/dev/null; then
        inkscape --export-type=png --export-width="$SIZE" --export-height="$SIZE" \
            --export-filename="${HICO_DIR}/xmleditor.png" \
            "${ICON_DIR}/xmleditor.svg" &>/dev/null || true
    fi
done

ICON_HICO_SVG="/usr/share/icons/hicolor/scalable/apps"
mkdir -p "$ICON_HICO_SVG"
cp "${ICON_DIR}/xmleditor.svg" "${ICON_HICO_SVG}/xmleditor.svg"
success "Icona installata"

# ── Desktop entry ────────────────────────────────────────────
info "Installazione voce nel menu applicazioni…"
cp "${SCRIPT_DIR}/xmleditor.desktop" "${DESKTOP_DIR}/xmleditor.desktop"
chmod 644 "${DESKTOP_DIR}/xmleditor.desktop"
success "Desktop entry installato"

# ── Aggiornamento database ────────────────────────────────────
info "Aggiornamento database applicazioni e MIME…"
update-desktop-database "$DESKTOP_DIR" 2>/dev/null || true
update-mime-database /usr/share/mime 2>/dev/null || true
if command -v gtk-update-icon-cache &>/dev/null; then
    gtk-update-icon-cache -f -t /usr/share/icons/hicolor 2>/dev/null || true
fi

# ── Associazione MIME (default) ──────────────────────────────
info "Impostazione come applicazione predefinita per XML…"
if command -v xdg-mime &>/dev/null; then
    xdg-mime default xmleditor.desktop application/xml   2>/dev/null || true
    xdg-mime default xmleditor.desktop text/xml          2>/dev/null || true
    xdg-mime default xmleditor.desktop application/xsd+xml 2>/dev/null || true
fi
success "Associazioni MIME configurate"

echo ""
echo -e "${GREEN}${BOLD}"
echo "╔══════════════════════════════════════════╗"
echo "║   Installazione completata con successo! ║"
echo "╚══════════════════════════════════════════╝"
echo -e "${RESET}"
echo -e "  Avvio:  ${BOLD}xmleditor [file.xml]${RESET}"
echo -e "  Menu:   Cerca '${APP_NAME}' nel menu applicazioni"
echo -e "  File:   Clic destro su .xml → Apri con → ${APP_NAME}"
echo ""
