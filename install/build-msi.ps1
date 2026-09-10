# ─────────────────────────────────────────────────────────────
#  XML Editor — generazione installer MSI con jpackage
#
#  Prerequisiti (da installare UNA VOLTA sul PC di build):
#    1. JDK 21+        https://adoptium.net
#    2. Apache Maven   winget install Apache.Maven
#    3. WiX Toolset 3  winget install WiX.WiX
#
#  Uso (PowerShell):
#    powershell -ExecutionPolicy Bypass -File build-msi.ps1
#
#  Output:
#    install\dist\XML-Editor-<versione>.msi
# ─────────────────────────────────────────────────────────────
#Requires -Version 5.1

$APP_NAME    = "XML Editor"
$APP_ID      = "XmlEditor"
$VENDOR      = "Touch Informatica S.r.l.s."

$SCRIPT_DIR  = Split-Path -Parent $MyInvocation.MyCommand.Path
$PROJECT_DIR = Split-Path -Parent $SCRIPT_DIR

# Versione: unica fonte di verità è pom.xml
$VERSION     = ([xml](Get-Content "$PROJECT_DIR\pom.xml")).project.version
if (-not $VERSION) { Write-Host "  X Impossibile leggere la versione da pom.xml" -ForegroundColor Red; exit 1 }
$JAR_NAME    = "$APP_ID-$VERSION.jar"
$TARGET_DIR  = "$PROJECT_DIR\target"
$DIST_DIR    = "$SCRIPT_DIR\dist"

# ── Colori ──────────────────────────────────────────────────
function Write-Info    { param($m) Write-Host "  > $m" -ForegroundColor Cyan }
function Write-Success { param($m) Write-Host "  OK $m" -ForegroundColor Green }
function Write-Err     { param($m) Write-Host "  X $m"  -ForegroundColor Red; exit 1 }

Write-Host ""
Write-Host "============================================" -ForegroundColor White
Write-Host "   Build MSI — $APP_NAME v$VERSION"          -ForegroundColor White
Write-Host "============================================" -ForegroundColor White
Write-Host ""

# ── Verifica Java 21+ ────────────────────────────────────────
Write-Info "Verifica JDK..."
try {
    $javaOut = & java -version 2>&1
    $javaVer = ($javaOut | Select-String 'version "?(\d+)' |
        ForEach-Object { $_.Matches[0].Groups[1].Value }) -as [int]
} catch { Write-Err "Java non trovato. Installare JDK 21: https://adoptium.net" }
if ($javaVer -lt 21) { Write-Err "JDK 21 richiesto (trovato: $javaVer)." }

# jpackage è in JAVA_HOME/bin
$jpackage = "$env:JAVA_HOME\bin\jpackage.exe"
if (-not (Test-Path $jpackage)) {
    # Fallback: cerca nel PATH
    $jpackage = (Get-Command jpackage -ErrorAction SilentlyContinue)?.Source
}
if (-not $jpackage -or -not (Test-Path $jpackage)) {
    Write-Err "jpackage non trovato. Assicurarsi che JAVA_HOME punti al JDK 21."
}
Write-Success "JDK $javaVer — jpackage trovato"

# ── Verifica Maven ───────────────────────────────────────────
Write-Info "Verifica Maven..."
try { $null = & mvn --version 2>&1 } catch {
    Write-Err "Maven non trovato. Installare con: winget install Apache.Maven"
}
Write-Success "Maven OK"

# ── Verifica WiX ─────────────────────────────────────────────
Write-Info "Verifica WiX Toolset (necessario per .msi)..."
$wix = Get-Command "candle.exe" -ErrorAction SilentlyContinue
if (-not $wix) {
    Write-Host ""
    Write-Host "  WiX Toolset 3 non trovato." -ForegroundColor Yellow
    Write-Host "  Installare con:  winget install WiX.WiX" -ForegroundColor Yellow
    Write-Host "  Oppure scaricare da: https://github.com/wixtoolset/wix3/releases" -ForegroundColor Yellow
    Write-Host ""
    Write-Err "WiX richiesto per generare il file .msi"
}
Write-Success "WiX OK"

# ── Build Maven (profilo Windows) ────────────────────────────
Write-Info "Compilazione Maven con profilo Windows..."
Push-Location $PROJECT_DIR
try {
    & mvn clean package -q -DskipTests -P windows
    if ($LASTEXITCODE -ne 0) { Write-Err "Build Maven fallita." }
} finally { Pop-Location }

if (-not (Test-Path "$TARGET_DIR\$JAR_NAME")) {
    Write-Err "JAR non trovato: $TARGET_DIR\$JAR_NAME"
}
Write-Success "Build completata"

# ── Prepara cartella input per jpackage ──────────────────────
Write-Info "Preparazione input jpackage..."
$inputDir = "$TARGET_DIR\jpackage-input"
Remove-Item -Recurse -Force $inputDir -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $inputDir | Out-Null
Copy-Item "$TARGET_DIR\$JAR_NAME" "$inputDir\" -Force
Write-Success "Input pronto: $inputDir"

# ── Icona .ico (opzionale) ───────────────────────────────────
$iconArg = @()
$icoPath = "$SCRIPT_DIR\xmleditor.ico"
if (Test-Path $icoPath) {
    $iconArg = @("--icon", $icoPath)
    Write-Success "Icona trovata: $icoPath"
} else {
    Write-Host "  ! Icona .ico non trovata in install\ — il MSI userà icona predefinita" -ForegroundColor Yellow
}

# ── Output dir ───────────────────────────────────────────────
Remove-Item -Recurse -Force $DIST_DIR -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $DIST_DIR | Out-Null

# ── Esecuzione jpackage ──────────────────────────────────────
Write-Info "Generazione MSI con jpackage (puo' richiedere qualche minuto)..."

$jpackageArgs = @(
    "--type",        "msi",
    "--name",        $APP_NAME,
    "--app-version", $VERSION,
    "--vendor",      $VENDOR,
    "--description", "Editor XML professionale con syntax highlighting e validazione XSD",
    "--input",       $inputDir,
    "--main-jar",    $JAR_NAME,
    "--main-class",  "it.touchinformatica.xmleditor.MainApp",
    "--module-path", "$TARGET_DIR\javafx-libs",
    "--add-modules", "javafx.controls,java.net.http",
    "--java-options", "-Dfile.encoding=UTF-8",
    "--java-options", "-Dsun.stdout.encoding=UTF-8",
    "--java-options", "--add-opens=javafx.graphics/com.sun.glass.ui=ALL-UNNAMED",
    "--java-options", "--add-opens=javafx.base/com.sun.javafx.runtime=ALL-UNNAMED",
    "--dest",        $DIST_DIR,
    "--win-menu",
    "--win-shortcut",
    "--win-dir-chooser",
    "--win-menu-group", "Touch Informatica",
    "--win-upgrade-uuid", "a3f1e2d4-7b6c-4e8a-9d2f-1c3b5a7e9f0d"
) + $iconArg

& $jpackage @jpackageArgs
if ($LASTEXITCODE -ne 0) { Write-Err "jpackage fallito." }

# ── Risultato ────────────────────────────────────────────────
$msi = Get-ChildItem "$DIST_DIR\*.msi" | Select-Object -First 1
Write-Host ""
Write-Host "============================================" -ForegroundColor Green
Write-Host "   MSI generato con successo!" -ForegroundColor Green
Write-Host "============================================" -ForegroundColor Green
Write-Host ""
Write-Host "  File: $($msi.FullName)"
Write-Host "  Dim:  $([math]::Round($msi.Length / 1MB, 1)) MB"
Write-Host ""
Write-Host "  Distribuire il file .msi agli utenti finali." -ForegroundColor White
Write-Host "  Non richede Java installato sul PC di destinazione." -ForegroundColor White
Write-Host ""
