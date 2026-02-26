#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────
#  XML Editor — script di disinstallazione
# ─────────────────────────────────────────────────────────────
set -e

APP_ID="xmleditor"
INSTALL_DIR="/opt/xmleditor"
BIN_LINK="/usr/local/bin/${APP_ID}"
DESKTOP_FILE="/usr/share/applications/${APP_ID}.desktop"
ICON_DIR="/usr/share/pixmaps"
HICO_DIR="/usr/share/icons/hicolor"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
CYAN='\033[0;36m'; BOLD='\033[1m'; RESET='\033[0m'

info()    { echo -e "${CYAN}▶ $*${RESET}"; }
success() { echo -e "${GREEN}✔ $*${RESET}"; }

if [ "$EUID" -ne 0 ]; then
    echo -e "${YELLOW}Richiesti privilegi di amministratore. Riesecuzione con sudo…${RESET}"
    exec sudo bash "$0" "$@"
fi

echo -e "${BOLD}Disinstallazione XML Editor…${RESET}"

info "Rimozione file installati…"
rm -rf "$INSTALL_DIR"
rm -f "$BIN_LINK"
rm -f "$DESKTOP_FILE"
rm -f "${ICON_DIR}/${APP_ID}.svg"
rm -f "${ICON_DIR}/${APP_ID}.png"
rm -f "${HICO_DIR}/scalable/apps/${APP_ID}.svg"
for SIZE in 48 64 128 256; do
    rm -f "${HICO_DIR}/${SIZE}x${SIZE}/apps/${APP_ID}.png"
done

info "Aggiornamento database…"
update-desktop-database /usr/share/applications 2>/dev/null || true
update-mime-database /usr/share/mime            2>/dev/null || true
gtk-update-icon-cache -f -t "$HICO_DIR"        2>/dev/null || true

success "XML Editor disinstallato."
