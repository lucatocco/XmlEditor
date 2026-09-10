#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────
#  XML Editor — rimozione dell'installazione utente
#
#  Rimuove quanto installato da install-user.sh sotto ~/.local.
#  Non tocca eventuali installazioni di sistema (pacchetto .deb o install.sh),
#  che si rimuovono con:  sudo apt remove xmleditor  /  sudo ./install/uninstall.sh
# ─────────────────────────────────────────────────────────────
set -euo pipefail

APP_ID="xmleditor"
APP_DIR="${XDG_DATA_HOME:-$HOME/.local/share}/${APP_ID}"
BIN="$HOME/.local/bin/${APP_ID}"
DESKTOP="${XDG_DATA_HOME:-$HOME/.local/share}/applications/${APP_ID}.desktop"
ICON="${XDG_DATA_HOME:-$HOME/.local/share}/icons/hicolor/512x512/apps/${APP_ID}.png"

GREEN='\033[0;32m'; CYAN='\033[0;36m'; BOLD='\033[1m'; RESET='\033[0m'
info()    { echo -e "${CYAN}▶ $*${RESET}"; }
success() { echo -e "${GREEN}✔ $*${RESET}"; }

echo -e "${BOLD}Rimozione di XML Editor (installazione utente)…${RESET}"

REMOVED=0
for TARGET in "$APP_DIR" "$BIN" "$DESKTOP" "$ICON"; do
    if [ -e "$TARGET" ]; then
        info "Rimozione di $TARGET"
        rm -rf "$TARGET"
        REMOVED=1
    fi
done

if [ "$REMOVED" -eq 0 ]; then
    echo "Nessuna installazione utente trovata in ~/.local."
    exit 0
fi

command -v update-desktop-database >/dev/null \
    && update-desktop-database "$(dirname "$DESKTOP")" 2>/dev/null || true
command -v gtk-update-icon-cache >/dev/null \
    && gtk-update-icon-cache -f -t "${XDG_DATA_HOME:-$HOME/.local/share}/icons/hicolor" 2>/dev/null || true

success "XML Editor rimosso dall'installazione utente."
