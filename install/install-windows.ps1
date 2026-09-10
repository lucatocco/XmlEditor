# ─────────────────────────────────────────────────────────────
#  XML Editor — script di installazione per Windows (PowerShell)
#  Eseguire come Amministratore:
#    powershell -ExecutionPolicy Bypass -File install-windows.ps1
# ─────────────────────────────────────────────────────────────
#Requires -Version 5.1

$APP_NAME    = "XML Editor"
$APP_ID      = "xmleditor"
$INSTALL_DIR = "$env:ProgramFiles\XmlEditor"

$SCRIPT_DIR  = Split-Path -Parent $MyInvocation.MyCommand.Path
$PROJECT_DIR = Split-Path -Parent $SCRIPT_DIR

# Versione: unica fonte di verità è pom.xml
$VERSION     = ([xml](Get-Content "$PROJECT_DIR\pom.xml")).project.version
if (-not $VERSION) { Write-Host "  X Impossibile leggere la versione da pom.xml" -ForegroundColor Red; exit 1 }
$JAR_NAME    = "XmlEditor-$VERSION.jar"

# ── Colori ──────────────────────────────────────────────────
function Write-Info    { param($m) Write-Host "  > $m" -ForegroundColor Cyan }
function Write-Success { param($m) Write-Host "  OK $m" -ForegroundColor Green }
function Write-Warn    { param($m) Write-Host "  ! $m"  -ForegroundColor Yellow }
function Write-Err     { param($m) Write-Host "  X $m"  -ForegroundColor Red; exit 1 }

Write-Host ""
Write-Host "============================================" -ForegroundColor White
Write-Host "   Installazione $APP_NAME v$VERSION"        -ForegroundColor White
Write-Host "============================================" -ForegroundColor White
Write-Host ""

# ── Verifica privilegi ───────────────────────────────────────
if (-not ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()
        ).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    Write-Warn "Riavvio con privilegi di Amministratore..."
    Start-Process powershell -Verb RunAs -ArgumentList "-ExecutionPolicy Bypass -File `"$($MyInvocation.MyCommand.Path)`""
    exit
}

# ── Verifica Java ────────────────────────────────────────────
Write-Info "Verifica prerequisiti..."
try {
    $javaOut = & java -version 2>&1
    $javaVer = ($javaOut | Select-String 'version "?(\d+)' | ForEach-Object {
        $_.Matches[0].Groups[1].Value }) -as [int]
} catch {
    Write-Err "Java non trovato. Installare OpenJDK 21 da: https://adoptium.net"
}
if ($javaVer -lt 21) {
    Write-Err "Java 21 o superiore richiesto (trovato: $javaVer). Scaricare da: https://adoptium.net"
}
Write-Success "Java $javaVer trovato"

# ── Verifica Maven ───────────────────────────────────────────
try {
    $null = & mvn --version 2>&1
} catch {
    Write-Err "Maven non trovato. Installare Maven da: https://maven.apache.org/download.cgi`nOppure: winget install Apache.Maven"
}
Write-Success "Maven trovato"

# ── Build ────────────────────────────────────────────────────
Write-Info "Compilazione del progetto (profilo Windows)..."
Push-Location $PROJECT_DIR
try {
    & mvn clean package -q -DskipTests -P windows
    if ($LASTEXITCODE -ne 0) { Write-Err "Build Maven fallita." }
} finally {
    Pop-Location
}
if (-not (Test-Path "$PROJECT_DIR\target\$JAR_NAME")) {
    Write-Err "Build fallita: $JAR_NAME non trovato in target\"
}
Write-Success "Build completata"

# ── Installazione file ───────────────────────────────────────
Write-Info "Installazione in $INSTALL_DIR..."
New-Item -ItemType Directory -Force -Path "$INSTALL_DIR"               | Out-Null
New-Item -ItemType Directory -Force -Path "$INSTALL_DIR\javafx-libs"   | Out-Null
New-Item -ItemType Directory -Force -Path "$INSTALL_DIR\classpath-libs" | Out-Null

Copy-Item "$PROJECT_DIR\target\$JAR_NAME"             "$INSTALL_DIR\" -Force
Copy-Item "$PROJECT_DIR\target\javafx-libs\*.jar"     "$INSTALL_DIR\javafx-libs\" -Force
if (Test-Path "$PROJECT_DIR\target\classpath-libs\*.jar") {
    Copy-Item "$PROJECT_DIR\target\classpath-libs\*.jar" "$INSTALL_DIR\classpath-libs\" -Force
}
Write-Success "File installati"

# ── Launcher .bat ─────────────────────────────────────────────
Write-Info "Creazione launcher $INSTALL_DIR\xmleditor.bat..."
$launcher = @"
@echo off
setlocal enabledelayedexpansion
set INSTALL_DIR=%~dp0
set CP_EXTRA=
for %%f in ("%INSTALL_DIR%classpath-libs\*.jar") do (
    set CP_EXTRA=!CP_EXTRA!;%%f
)
start "" javaw -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 ^
    --module-path "%INSTALL_DIR%javafx-libs" ^
    --add-modules javafx.controls,java.net.http ^
    --add-opens=javafx.graphics/com.sun.glass.ui=ALL-UNNAMED ^
    --add-opens=javafx.base/com.sun.javafx.runtime=ALL-UNNAMED ^
    -cp "%INSTALL_DIR%$JAR_NAME!CP_EXTRA!" ^
    it.touchinformatica.xmleditor.MainApp %*
"@
Set-Content -Path "$INSTALL_DIR\xmleditor.bat" -Value $launcher -Encoding ASCII
Write-Success "Launcher creato"

# ── Aggiunta al PATH di sistema ──────────────────────────────
Write-Info "Aggiunta al PATH di sistema..."
$sysPath = [Environment]::GetEnvironmentVariable("Path", "Machine")
if ($sysPath -notlike "*$INSTALL_DIR*") {
    [Environment]::SetEnvironmentVariable("Path", "$sysPath;$INSTALL_DIR", "Machine")
    Write-Success "PATH aggiornato (effettivo alla riapertura del terminale)"
} else {
    Write-Success "Gia' nel PATH"
}

# ── Collegamento Desktop ──────────────────────────────────────
Write-Info "Creazione collegamento sul Desktop..."
$desktop = [Environment]::GetFolderPath("CommonDesktopDirectory")
$wsh     = New-Object -ComObject WScript.Shell
$lnk     = $wsh.CreateShortcut("$desktop\XML Editor.lnk")
$lnk.TargetPath       = "$INSTALL_DIR\xmleditor.bat"
$lnk.WorkingDirectory = $INSTALL_DIR
$lnk.Description      = "XML Editor — Touch Informatica"
if (Test-Path "$SCRIPT_DIR\xmleditor.ico") {
    $lnk.IconLocation = "$SCRIPT_DIR\xmleditor.ico"
}
$lnk.Save()
Write-Success "Collegamento Desktop creato"

# ── Registrazione Apri Con ───────────────────────────────────
Write-Info "Associazione file .xml e .txt..."
$batPath = "$INSTALL_DIR\xmleditor.bat"
foreach ($ext in @(".xml", ".xsd", ".txt")) {
    $regKey = "HKCU:\Software\Classes\$ext\OpenWithList\xmleditor.bat"
    New-Item -Path $regKey -Force | Out-Null
}
Write-Success "Associazioni registrate (visibili in 'Apri con')"

Write-Host ""
Write-Host "============================================" -ForegroundColor Green
Write-Host "   Installazione completata con successo!" -ForegroundColor Green
Write-Host "============================================" -ForegroundColor Green
Write-Host ""
Write-Host "  Avvio da terminale: xmleditor [file.xml]"
Write-Host "  Collegamento:       Desktop -> XML Editor"
Write-Host ""
