#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────
#  XML Editor — cambio versione e rilascio
#
#  Uso:
#    ./install/bump-version.sh 2.1.0                  # aggiorna, committa e crea il tag
#    ./install/bump-version.sh 2.1.0 --push           # e lo pubblica su TUTTI i remote
#    ./install/bump-version.sh 2.1.0 --push github    # solo sui remote indicati
#
#  Aggiorna pom.xml (unica fonte di verità): tutto il resto — la finestra
#  Informazioni, gli script di installazione, l'MSI, la CI — legge da lì.
#
#  Il push del tag fa partire .github/workflows/release.yml, che builda l'MSI
#  e pubblica la GitHub Release con l'installer allegato.
# ─────────────────────────────────────────────────────────────
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
POM="${PROJECT_DIR}/pom.xml"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
CYAN='\033[0;36m'; BOLD='\033[1m'; RESET='\033[0m'
info()    { echo -e "${CYAN}▶ $*${RESET}"; }
success() { echo -e "${GREEN}✔ $*${RESET}"; }
warn()    { echo -e "${YELLOW}⚠ $*${RESET}"; }
error()   { echo -e "${RED}✖ $*${RESET}" >&2; exit 1; }

read_version() {
    sed -n 's|^[[:space:]]*<version>\(.*\)</version>.*|\1|p' "$1" | head -1
}

# ── Argomenti ────────────────────────────────────────────────
NEW_VERSION="${1:-}"
PUSH="${2:-}"
shift 2 2>/dev/null || true
REMOTES=("$@")          # remote espliciti dopo --push, altrimenti tutti quelli configurati

if [ -z "$NEW_VERSION" ]; then
    CURRENT=$(read_version "$POM")
    echo -e "Versione attuale: ${BOLD}${CURRENT}${RESET}"
    echo "Uso: $0 <nuova-versione> [--push [remote...]]"
    echo "Esempio: $0 2.1.0 --push"
    echo "Remote configurati: $(git remote | tr '\n' ' ')"
    exit 1
fi

# jpackage accetta solo versioni numeriche tipo 1.2.3 (niente -SNAPSHOT o suffissi)
if ! [[ "$NEW_VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    error "Versione non valida: '${NEW_VERSION}'. Formato richiesto: MAJOR.MINOR.PATCH (es. 2.1.0).
       jpackage rifiuta suffissi come -SNAPSHOT o -rc1 nella --app-version dell'MSI."
fi

cd "$PROJECT_DIR"

CURRENT=$(read_version "$POM")
[ -n "$CURRENT" ] || error "Impossibile leggere la versione da $POM"
[ "$CURRENT" != "$NEW_VERSION" ] || error "pom.xml è già alla versione ${NEW_VERSION}"

# ── Controlli preliminari ────────────────────────────────────
command -v git >/dev/null || error "git non trovato"
git rev-parse --git-dir >/dev/null 2>&1 || error "Non siamo in un repository git"

if [ -n "$(git status --porcelain)" ]; then
    error "Ci sono modifiche non committate. Committa o stasha prima di cambiare versione:
$(git status --short)"
fi

if git rev-parse "v${NEW_VERSION}" >/dev/null 2>&1; then
    error "Il tag v${NEW_VERSION} esiste già"
fi

echo -e "${BOLD}"
echo "╔══════════════════════════════════════════╗"
echo "║   Rilascio XML Editor                    ║"
echo "╚══════════════════════════════════════════╝"
echo -e "${RESET}"
info "Versione: ${CURRENT} → ${BOLD}${NEW_VERSION}${RESET}"

# ── Aggiornamento pom.xml ────────────────────────────────────
# Solo la prima occorrenza di <version>, che è quella del progetto:
# le altre appartengono a dipendenze e plugin e non vanno toccate.
info "Aggiornamento pom.xml…"
sed -i "0,\|<version>${CURRENT}</version>|s||<version>${NEW_VERSION}</version>|" "$POM"

APPLIED=$(read_version "$POM")
[ "$APPLIED" = "$NEW_VERSION" ] || error "Aggiornamento fallito: pom.xml riporta '${APPLIED}'"
success "pom.xml → ${NEW_VERSION}"

# ── Commit e tag ─────────────────────────────────────────────
info "Commit e tag…"
git add "$POM"
git commit -q -m "Release ${NEW_VERSION}"
git tag -a "v${NEW_VERSION}" -m "XML Editor ${NEW_VERSION}"
success "Creati commit e tag v${NEW_VERSION}"

# ── Push ─────────────────────────────────────────────────────
if [ "$PUSH" = "--push" ]; then
    BRANCH=$(git rev-parse --abbrev-ref HEAD)

    # Senza remote espliciti si pubblica su tutti: qui GitHub e GitLab vanno
    # tenuti allineati, e la Release la produce solo il workflow su GitHub.
    if [ ${#REMOTES[@]} -eq 0 ]; then
        mapfile -t REMOTES < <(git remote)
    fi
    [ ${#REMOTES[@]} -gt 0 ] || error "Nessun remote configurato"

    for REMOTE in "${REMOTES[@]}"; do
        info "Push di ${BRANCH} e del tag v${NEW_VERSION} su ${REMOTE}…"
        git push "$REMOTE" "$BRANCH"
        git push "$REMOTE" "v${NEW_VERSION}"
        success "${REMOTE} aggiornato"

        # Se è GitHub, il tag fa partire il workflow che pubblica la Release
        SLUG=$(git config --get "remote.${REMOTE}.url" \
               | sed -n 's|.*github\.com[:/]\(.*\)|\1|p' | sed 's|\.git$||')
        if [ -n "$SLUG" ]; then
            echo "  Avanzamento: https://github.com/${SLUG}/actions"
            echo "  Release:     https://github.com/${SLUG}/releases/tag/v${NEW_VERSION}"
        fi
    done
else
    warn "Niente è stato pubblicato. Per far partire il rilascio:"
    echo "    git push origin $(git rev-parse --abbrev-ref HEAD)"
    echo "    git push origin v${NEW_VERSION}"
fi
