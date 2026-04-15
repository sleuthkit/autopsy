<#
.SYNOPSIS
    Step 2 of the Autopsy Windows release process: build the installer from
    the versioned folder produced by ant build-zip.

.DESCRIPTION
    The release process is split into two steps so that EXEs can be signed
    before being packaged into an installer:

      Step 1  (build machine):
              ant build-zip
              Produces: dist\autopsy-X.Y.Z\

      [sign]  Sign the EXEs in dist\autopsy-X.Y.Z\bin\ (autopsy64.exe, etc.)

      Step 2  (this script, run from the repo root on a machine with
              Advanced Installer installed):
              .\release_scripts\build_windows_installer.ps1 X.Y.Z
              Produces: dist\autopsy-X.Y.Z-64bit.msi

    The script uses the dist\autopsy-X.Y.Z\ folder directly, bundles a JRE
    with jlink, configures the Advanced Installer project, and builds the MSI.

.PARAMETER Version
    The Autopsy version number (e.g. 21.0.0). Used to locate the folder
    and name all output artifacts.

.PARAMETER AiPath
    Path to AdvancedInstaller.com. Defaults to the standard installation
    location for Advanced Installer 22.3.

.PARAMETER JdkHome
    Path to the JDK used to bundle a JRE with jlink. Defaults to the
    JDK_HOME environment variable.

.EXAMPLE
    # From the repo root:
    .\release_scripts\build_windows_installer.ps1 21.0.0
#>
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string]$Version,

    [string]$AiPath = "C:\Program Files (x86)\Caphyon\Advanced Installer 23.5.1\bin\x86\AdvancedInstaller.com",

    [string]$JdkHome = $env:JDK_HOME
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

function Invoke-AI {
    param([string[]]$Arguments)
    Write-Host "  AI: $($Arguments -join ' ')"
    & $AiPath @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "AdvancedInstaller.com exited with code $LASTEXITCODE"
    }
}

# ---------------------------------------------------------------------------
# Derive paths from version
# ---------------------------------------------------------------------------

# All paths are relative to the repository root, which is one level above
# this script's directory (release_scripts/).
$repoRoot   = Split-Path -Parent $PSScriptRoot
$distDir    = Join-Path $repoRoot "dist"
$instDir    = Join-Path $distDir  "autopsy-$Version"
$appDir     = Join-Path $instDir  "autopsy-$Version"
$aipSrc     = Join-Path $repoRoot "installer_autopsy\installer_autopsy.aip"
$aipBase    = Join-Path $distDir  "installer_autopsy_$Version-base.aip"
$aip64      = Join-Path $distDir  "installer_autopsy_$Version-64.aip"

# ---------------------------------------------------------------------------
# Validate inputs
# ---------------------------------------------------------------------------

if (-not (Test-Path $instDir)) {
    throw "Folder not found: $instDir`nRun 'ant build-zip' first to produce this folder."
}
if (-not (Test-Path $appDir)) {
    throw "Expected subfolder not found: $appDir`nEnsure the folder produced by 'ant build-zip' contains a subfolder named 'autopsy-$Version'."
}

if (-not (Test-Path $AiPath)) {
    throw "Advanced Installer not found: $AiPath`nUse -AiPath to specify the correct path."
}

if (-not $JdkHome) {
    throw "JDK_HOME is not set. Set the environment variable or pass -JdkHome."
}
if (-not (Test-Path "$JdkHome\bin\jlink.exe")) {
    throw "jlink.exe not found under JdkHome: $JdkHome"
}

if (-not (Test-Path $aipSrc)) {
    throw "AIP template not found: $aipSrc`nRun this script from (or with paths relative to) the repository root."
}

Write-Host ""
Write-Host "=== Autopsy Windows installer build ==="
Write-Host "  Version:     $Version"
Write-Host "  Folder:      $instDir"
Write-Host "  JDK:         $JdkHome"
Write-Host "  AI:          $AiPath"
Write-Host ""

# ---------------------------------------------------------------------------
# Bundle a JRE using jlink
# ---------------------------------------------------------------------------

Write-Host ""
Write-Host "--- Bundling JRE ---"
$jreDir = Join-Path $appDir "jre"
if (Test-Path $jreDir) { Remove-Item -Recurse -Force $jreDir }

& "$JdkHome\bin\jlink.exe" `
    --add-modules ALL-MODULE-PATH `
    --output     $jreDir          `
    --no-man-pages                `
    --no-header-files             `
    --compress=2
if ($LASTEXITCODE -ne 0) { throw "jlink failed." }
Write-Host "  JRE written to $jreDir"

# ---------------------------------------------------------------------------
# Update autopsy.conf with JVM options and jdkhome
# ---------------------------------------------------------------------------

Write-Host ""
Write-Host "--- Updating autopsy.conf ---"
$confFile = Join-Path $appDir "etc\autopsy.conf"
if (-not (Test-Path $confFile)) { throw "autopsy.conf not found in staging dir: $confFile" }

$jvmArgs = (
    '"--branding autopsy' +
    ' -J-Xms24m' +
    ' -J-XX:+UseStringDeduplication' +
    ' -J-Dprism.order=sw' +
    ' -J--add-opens=java.base/java.lang=ALL-UNNAMED' +
    ' -J--add-opens=java.base/java.net=ALL-UNNAMED' +
    ' -J--add-opens=java.desktop/javax.swing=ALL-UNNAMED' +
    ' -J--add-opens=javafx.controls/javafx.scene.control.skin=ALL-UNNAMED' +
    ' -J--add-exports=java.desktop/sun.awt=ALL-UNNAMED' +
    ' -J--add-exports=javafx.controls/com.sun.javafx.scene.control.inputmap=ALL-UNNAMED' +
    ' -J--add-exports=javafx.base/com.sun.javafx.event=ALL-UNNAMED"'
)

$conf = Get-Content $confFile -Raw
$conf = $conf -replace '(?m)^default_options=.*$', "default_options=$jvmArgs"
if ($conf -match '(?m)^jdkhome=') {
    $conf = $conf -replace '(?m)^jdkhome=.*$', 'jdkhome="jre"'
} else {
    $conf = $conf.TrimEnd() + "`r`njdkhome=`"jre`"`r`n"
}
Set-Content -Path $confFile -Value $conf -NoNewline
Write-Host "  Done."

# ---------------------------------------------------------------------------
# Configure the Advanced Installer project
# ---------------------------------------------------------------------------

Write-Host ""
Write-Host "--- Configuring Advanced Installer project ---"
Copy-Item $aipSrc $aipBase -Force

# Generate a fresh product code for this release
Invoke-AI @('/edit', $aipBase, '/SetProductCode', '-langid', '1033')

# Update the product version (the regex matches any existing X.Y.Z value)
$aipContent = Get-Content $aipBase -Raw
$aipContent = $aipContent -replace `
    'ProductVersion" Value="\d[\d.]*"', `
    "ProductVersion`" Value=`"$Version`""
Set-Content -Path $aipBase -Value $aipContent -NoNewline

Copy-Item $aipBase $aip64 -Force

# 64-bit specific settings
Invoke-AI @('/edit', $aip64, '/SetAppdir',     '-buildname', 'DefaultBuild', '-path', "[ProgramFiles64Folder][ProductName]-$Version")
Invoke-AI @('/edit', $aip64, '/SetPackageType', 'x64')

# ---------------------------------------------------------------------------
# Add staging directory contents to the installer
# ---------------------------------------------------------------------------

Write-Host ""
Write-Host "--- Adding files to installer ---"

# Add each top-level item from the staging directory (Advanced Installer
# recurses into directories automatically).
foreach ($item in Get-ChildItem $appDir) {
    $type = if ($item.PSIsContainer) { 'Folder' } else { 'File' }
    Invoke-AI @('/edit', $aip64, "/Add$type", 'APPDIR', $item.FullName)
}

# Remove the 32-bit autopsy.exe (keep autopsy64.exe)
# Invoke-AI @('/edit', $aip64, '/DelFile',   'APPDIR\bin\autopsy.exe')

# Remove 32-bit GStreamer binaries
# Invoke-AI @('/edit', $aip64, '/DelFolder', 'APPDIR\autopsy\gstreamer\1.0\x86')


# Replace the mixed-arch lib folder with only the amd64 DLLs
$libRelPath  = 'autopsy\modules\lib'
$libInstPath = Join-Path $appDir "autopsy\modules\lib\amd64"
Invoke-AI @('/edit', $aip64, '/DelFolder', "APPDIR\$libRelPath")
foreach ($dll in Get-ChildItem $libInstPath -File) {
    Invoke-AI @('/edit', $aip64, '/AddFile', "APPDIR\$libRelPath", $dll.FullName)
}

# Replace the mixed-arch PhotoRec folder with only the 64-bit binaries
$photorecInst = Join-Path $appDir "autopsy\photorec_exec"
Invoke-AI @('/edit', $aip64, '/DelFolder', 'APPDIR\autopsy\photorec_exec')
Invoke-AI @('/edit', $aip64, '/AddFolder', 'APPDIR\autopsy\photorec_exec', "$photorecInst\64-bit\bin")

# Copy the etc folder to AppData (preserves user config across upgrades)
$etcSrc = Join-Path $repoRoot "installer_autopsy\etc"
Invoke-AI @('/edit', $aip64, '/AddFolder', 'AppDataFolder\autopsy', $etcSrc)

# ---------------------------------------------------------------------------
# Add shortcuts
# ---------------------------------------------------------------------------

Write-Host ""
Write-Host "--- Adding shortcuts ---"
$iconPath = Join-Path $appDir "icon.ico"
Invoke-AI @('/edit', $aip64, '/NewShortcut',
    '-name',   "Autopsy $Version",
    '-dir',    'DesktopFolder',
    '-target', 'APPDIR\bin\autopsy64.exe',
    '-icon',   $iconPath)
Invoke-AI @('/edit', $aip64, '/NewShortcut',
    '-name',   "Autopsy $Version",
    '-dir',    'SHORTCUTDIR',
    '-target', 'APPDIR\bin\autopsy64.exe',
    '-icon',   $iconPath)

# ---------------------------------------------------------------------------
# Build the MSI
# ---------------------------------------------------------------------------

Write-Host ""
Write-Host "--- Building MSI ---"
Invoke-AI @('/build', $aip64)

# Move the output MSI to the dist directory
$outputDir = Join-Path $distDir "installer_autopsy_$Version-64-SetupFiles"
$msiSrc    = Join-Path $outputDir "installer_autopsy_$Version-64.msi"
$msiDest   = Join-Path $distDir  "autopsy-$Version-64bit.msi"
Move-Item $msiSrc $msiDest -Force

# Clean up the AI build cache
$cacheDir = Join-Path $distDir "installer_autopsy_$Version-64-cache"
if (Test-Path $cacheDir) { Remove-Item -Recurse -Force $cacheDir }

Write-Host ""
Write-Host "=== Done ==="
Write-Host "  Installer: $msiDest"
