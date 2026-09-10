#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────
#  XML Editor — installazione per il solo utente corrente
#
#  Non richiede privilegi di amministratore: tutto finisce sotto ~/.local,
#  secondo le convenzioni XDG. Pensato per macchine su cui non si ha root.
#
#  Uso:
#    ./install/install-user.sh                  scarica l'ultima versione da GitHub
#    ./install/install-user.sh pacchetto.tar.gz usa un archivio già scaricato
#    ./install/install-user.sh --build          compila dai sorgenti (serve JDK 21+ e Maven)
#
#  Per rimuoverlo:  ./install/uninstall-user.sh
# ─────────────────────────────────────────────────────────────
set -euo pipefail

APP_ID="xmleditor"
APP_NAME="XML Editor"
REPO="lucatocco/XmlEditor"

APP_DIR="${XDG_DATA_HOME:-$HOME/.local/share}/${APP_ID}"
BIN_DIR="$HOME/.local/bin"
DESKTOP_DIR="${XDG_DATA_HOME:-$HOME/.local/share}/applications"
ICON_DIR="${XDG_DATA_HOME:-$HOME/.local/share}/icons/hicolor/512x512/apps"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
CYAN='\033[0;36m'; BOLD='\033[1m'; RESET='\033[0m'
info()    { echo -e "${CYAN}▶ $*${RESET}"; }
success() { echo -e "${GREEN}✔ $*${RESET}"; }
warn()    { echo -e "${YELLOW}⚠ $*${RESET}"; }
error()   { echo -e "${RED}✖ $*${RESET}" >&2; exit 1; }

[ "$(id -u)" -ne 0 ] || warn "Eseguito come root: l'applicazione finirà nella home di root.
   Per un'installazione di sistema usa install.sh o il pacchetto .deb."

echo -e "${BOLD}"
echo "╔══════════════════════════════════════════╗"
echo "║   ${APP_NAME} — installazione utente      ║"
echo "╚══════════════════════════════════════════╝"
echo -e "${RESET}"

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

# ── Da dove prendere l'applicazione ──────────────────────────
SOURCE="${1:-}"

if [ "$SOURCE" = "--build" ]; then
    # ── Compilazione dai sorgenti ────────────────────────────
    command -v mvn      >/dev/null || error "Maven non trovato"
    command -v jpackage >/dev/null || error "jpackage non trovato: serve un JDK 21 o superiore"

    VERSION=$(sed -n 's|^[[:space:]]*<version>\(.*\)</version>.*|\1|p' "$PROJECT_DIR/pom.xml" | head -1)
    [ -n "$VERSION" ] || error "Impossibile leggere la versione da pom.xml"

    info "Compilazione della versione ${VERSION}…"
    (cd "$PROJECT_DIR" && mvn clean package -q -P linux)

    mkdir -p "$TMP/input"
    cp "$PROJECT_DIR/target/XmlEditor-${VERSION}.jar" "$TMP/input/"

    info "Generazione dell'immagine applicativa…"
    jpackage --type app-image --name XmlEditor --app-version "$VERSION" \
        --input "$TMP/input" --main-jar "XmlEditor-${VERSION}.jar" \
        --main-class it.touchinformatica.xmleditor.MainApp \
        --module-path "$PROJECT_DIR/target/javafx-libs" \
        --add-modules javafx.controls,java.net.http,jdk.crypto.ec \
        --java-options "-Dfile.encoding=UTF-8" \
        --icon "$PROJECT_DIR/install/xmleditor.png" \
        --dest "$TMP/image"
    IMAGE="$TMP/image/XmlEditor"

elif [ -n "$SOURCE" ]; then
    # ── Archivio indicato dall'utente ────────────────────────
    [ -f "$SOURCE" ] || error "Archivio non trovato: $SOURCE"
    info "Estrazione di $(basename "$SOURCE")…"
    mkdir -p "$TMP/image" && tar -xzf "$SOURCE" -C "$TMP/image"
    IMAGE="$(find "$TMP/image" -maxdepth 2 -name bin -type d | head -1 | xargs dirname)"
    [ -n "$IMAGE" ] || error "L'archivio non contiene un'applicazione valida"

else
    # ── Ultima versione pubblicata ───────────────────────────
    command -v curl >/dev/null || error "curl non trovato: scarica l'archivio a mano e passalo come argomento"
    info "Ricerca dell'ultima versione pubblicata…"
    URL=$(curl -fsSL "https://api.github.com/repos/${REPO}/releases/latest" \
        | grep -o '"browser_download_url": *"[^"]*linux-x64.tar.gz"' \
        | head -1 | cut -d'"' -f4)
    [ -n "$URL" ] || error "Nessun archivio Linux nell'ultima release.
   In alternativa: ./install/install-user.sh --build"

    info "Scaricamento di $(basename "$URL")…"
    curl -fL --progress-bar -o "$TMP/app.tar.gz" "$URL"
    mkdir -p "$TMP/image" && tar -xzf "$TMP/app.tar.gz" -C "$TMP/image"
    IMAGE="$(find "$TMP/image" -maxdepth 2 -name bin -type d | head -1 | xargs dirname)"
    [ -n "$IMAGE" ] || error "L'archivio scaricato non contiene un'applicazione valida"
fi

[ -x "$IMAGE/bin/XmlEditor" ] || error "Lanciatore non trovato in $IMAGE/bin"

# ── Installazione ────────────────────────────────────────────
info "Installazione in ${APP_DIR}…"
rm -rf "$APP_DIR"
mkdir -p "$(dirname "$APP_DIR")"
cp -r "$IMAGE" "$APP_DIR"
chmod +x "$APP_DIR/bin/XmlEditor"
success "Applicazione installata ($(du -sh "$APP_DIR" | cut -f1))"

# ── Comando da terminale ─────────────────────────────────────
mkdir -p "$BIN_DIR"
cat > "$BIN_DIR/$APP_ID" <<LAUNCHER
#!/usr/bin/env bash
exec "$APP_DIR/bin/XmlEditor" "\$@"
LAUNCHER
chmod +x "$BIN_DIR/$APP_ID"
success "Comando ${APP_ID} creato in ${BIN_DIR}"

# ── Icona e voce nel menu ────────────────────────────────────
mkdir -p "$ICON_DIR" "$DESKTOP_DIR"
if [ -f "$APP_DIR/lib/XmlEditor.png" ]; then
    cp "$APP_DIR/lib/XmlEditor.png" "$ICON_DIR/${APP_ID}.png"
elif [ -f "$PROJECT_DIR/install/xmleditor.png" ]; then
    cp "$PROJECT_DIR/install/xmleditor.png" "$ICON_DIR/${APP_ID}.png"
fi

cat > "$DESKTOP_DIR/${APP_ID}.desktop" <<DESKTOP
[Desktop Entry]
Name=${APP_NAME}
Comment=XML file editor with syntax highlighting and XSD validation
Exec=${APP_DIR}/bin/XmlEditor %f
Icon=${APP_ID}
Terminal=false
Type=Application
Categories=Utility;TextEditor;
Keywords=xml;xsd;editor;text;
StartupNotify=true
StartupWMClass=XmlEditor
MimeType=application/xml;application/xsd+xml;
DESKTOP
chmod 644 "$DESKTOP_DIR/${APP_ID}.desktop"

command -v update-desktop-database >/dev/null && update-desktop-database "$DESKTOP_DIR" 2>/dev/null || true
command -v gtk-update-icon-cache   >/dev/null && gtk-update-icon-cache -f -t "${ICON_DIR%/512x512/apps}" 2>/dev/null || true
success "Voce nel menu applicazioni creata"

# ── PATH ─────────────────────────────────────────────────────
echo
case ":$PATH:" in
    *":$BIN_DIR:"*)
        success "Installazione completata. Avvia con:  ${BOLD}${APP_ID}${RESET}" ;;
    *)
        success "Installazione completata."
        warn "${BIN_DIR} non è nel PATH. Per usare il comando da terminale:"
        echo "    echo 'export PATH=\"\$HOME/.local/bin:\$PATH\"' >> ~/.bashrc && source ~/.bashrc"
        echo "  Oppure avvia direttamente:  ${APP_DIR}/bin/XmlEditor" ;;
esac
echo "  Per rimuoverlo:  ${SCRIPT_DIR}/uninstall-user.sh"
