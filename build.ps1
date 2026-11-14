param(
    [switch]$Installer,
    [string]$Version = "1.0"
)

$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
Set-Location $root

$buildDir = Join-Path $root "build"
$classesDir = Join-Path $buildDir "classes"
$libDir = Join-Path $buildDir "lib"
$outputDir = Join-Path $root "output"
$appDir = Join-Path $outputDir "Aero"
$exePath = Join-Path $appDir "Aero.exe"
$zipPath = Join-Path $outputDir "Aero-portable.zip"

# --- Find a JDK -------------------------------------------------------------
function Test-Jdk([string]$Path) {
    return $Path -and (Test-Path (Join-Path $Path "bin\jpackage.exe"))
}

function Find-Jdk([string]$Path) {
    if (Test-Jdk $Path) { return $Path }
    if (Test-Path -LiteralPath $Path) {
        $child = Get-ChildItem -LiteralPath $Path -Directory -ErrorAction SilentlyContinue |
            Where-Object { Test-Jdk $_.FullName } |
            Select-Object -First 1
        if ($child) { return $child.FullName }
    }
    return $null
}

function Install-PortableJdk {
    $cacheBase = if ($env:LOCALAPPDATA) {
        Join-Path $env:LOCALAPPDATA "AeroBuild"
    } else {
        Join-Path $env:TEMP "AeroBuild"
    }
    $installRoot = Join-Path $cacheBase "jdk-21"

    $cached = Find-Jdk $installRoot
    if ($cached) { return $cached }

    $arch = if ($env:PROCESSOR_ARCHITECTURE -eq "ARM64") { "aarch64" } else { "x64" }
    $url = "https://api.adoptium.net/v3/binary/latest/21/ga/windows/$arch/jdk/hotspot/normal/eclipse?project=jdk"
    $zip = Join-Path ([System.IO.Path]::GetTempPath()) "aero-jdk21-$arch.zip"

    Write-Host "No JDK found. Downloading Temurin JDK 21 (Windows $arch)..." -ForegroundColor Yellow
    Write-Host "This is a one-time download of about 190 MB." -ForegroundColor Yellow

    $oldProgress = $ProgressPreference
    try {
        $ProgressPreference = "SilentlyContinue"
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
        Invoke-WebRequest -Uri $url -OutFile $zip -UseBasicParsing
    } catch {
        throw "Could not download a JDK automatically. Install Java 17+ from https://adoptium.net and run build.ps1 again.`n$($_.Exception.Message)"
    } finally {
        $ProgressPreference = $oldProgress
    }

    if (-not (Test-Path -LiteralPath $zip) -or (Get-Item -LiteralPath $zip).Length -lt 50MB) {
        Remove-Item -LiteralPath $zip -Force -ErrorAction SilentlyContinue
        throw "The downloaded JDK file looks invalid. Install Java 17+ from https://adoptium.net and run build.ps1 again."
    }

    Write-Host "Extracting JDK..." -ForegroundColor Cyan
    if (Test-Path -LiteralPath $installRoot) {
        Remove-Item -LiteralPath $installRoot -Recurse -Force -ErrorAction SilentlyContinue
    }
    New-Item -ItemType Directory -Force -Path $installRoot | Out-Null

    try {
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        [System.IO.Compression.ZipFile]::ExtractToDirectory($zip, $installRoot)
    } catch {
        throw "Could not extract the downloaded JDK. Install Java 17+ from https://adoptium.net and run build.ps1 again.`n$($_.Exception.Message)"
    } finally {
        Remove-Item -LiteralPath $zip -Force -ErrorAction SilentlyContinue
    }

    $installed = Find-Jdk $installRoot
    if (-not $installed) {
        throw "The downloaded JDK could not be found after extraction. Install Java 17+ from https://adoptium.net and run build.ps1 again."
    }
    return $installed
}

$jdk = $null

# 1. JAVA_HOME, if set and complete
if (Test-Jdk $env:JAVA_HOME) {
    $jdk = $env:JAVA_HOME
}

# 2. java.exe from PATH
if (-not $jdk) {
    $java = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($java) {
        $candidate = Split-Path (Split-Path $java.Source -Parent) -Parent
        if (Test-Jdk $candidate) { $jdk = $candidate }
    }
}

# 3. IntelliJ JDKs (~\.jdks) and common install locations
if (-not $jdk) {
    $roots = @(
        "$env:USERPROFILE\.jdks",
        "$env:ProgramFiles\Java",
        "$env:ProgramFiles\Eclipse Adoptium",
        "${env:ProgramFiles(x86)}\Java",
        "$env:ProgramFiles\Microsoft",
        "$env:ProgramFiles\Amazon Corretto",
        "$env:ProgramFiles\BellSoft",
        "$env:LOCALAPPDATA\Programs\Eclipse Adoptium",
        "$env:LOCALAPPDATA\Programs\Microsoft"
    )

    $candidates = foreach ($rootPath in $roots) {
        if (Test-Path $rootPath) {
            Get-ChildItem -Path $rootPath -Directory -ErrorAction SilentlyContinue |
                ForEach-Object { $_.FullName }
        }
    }

    $jdk = $candidates |
        Where-Object { Test-Jdk $_ } |
        Sort-Object -Descending |
        Select-Object -First 1
}

if (-not $jdk) {
    $jdk = Install-PortableJdk
}
Write-Host "Using JDK: $jdk" -ForegroundColor Cyan

# --- Clean previous build ---------------------------------------------------
# If Aero is still running from output, its EXE locks the folder and jpackage
# cannot replace it. Stop only instances that belong to this project.
$outputPrefix = [System.IO.Path]::GetFullPath($outputDir).TrimEnd('\') + '\'
$running = Get-Process -Name "Aero" -ErrorAction SilentlyContinue | Where-Object {
    $path = $null
    try { $path = $_.Path } catch { }
    return $path -and $path.StartsWith($outputPrefix, [System.StringComparison]::OrdinalIgnoreCase)
}
if ($running) {
    Write-Host "Aero is running from output. Closing it for the build..." -ForegroundColor Yellow
    $running | Stop-Process -Force
    Start-Sleep -Milliseconds 800
}

function Remove-BuildFolder([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path)) { return }
    for ($attempt = 1; $attempt -le 3; $attempt++) {
        try {
            Remove-Item -LiteralPath $Path -Recurse -Force -ErrorAction Stop
            return
        } catch {
            if ($attempt -eq 3) {
                throw "Could not remove '$Path'. Close Aero and any program using this folder, then try again."
            }
            Start-Sleep -Milliseconds 400
        }
    }
}

function New-PortableZip([string]$SourceDir, [string]$Destination) {
    Add-Type -AssemblyName System.IO.Compression.FileSystem

    for ($attempt = 1; $attempt -le 5; $attempt++) {
        try {
            if (Test-Path -LiteralPath $Destination) {
                Remove-Item -LiteralPath $Destination -Force -ErrorAction Stop
            }

            [System.IO.Compression.ZipFile]::CreateFromDirectory(
                $SourceDir,
                $Destination,
                [System.IO.Compression.CompressionLevel]::Optimal,
                $true
            )

            # A recently built EXE can be locked by antivirus while zipping.
            # Verify the archive instead of silently shipping a partial ZIP.
            $zip = [System.IO.Compression.ZipFile]::OpenRead($Destination)
            try {
                $exe = $zip.Entries | Where-Object {
                    $_.FullName -eq "Aero\Aero.exe" -or $_.FullName -eq "Aero/Aero.exe"
                }
                $jar = $zip.Entries | Where-Object {
                    $_.FullName -eq "Aero\app\aero.jar" -or $_.FullName -eq "Aero/app/aero.jar"
                }
                if (-not $exe -or $exe.Length -le 0 -or -not $jar -or $jar.Length -le 0) {
                    throw "The ZIP is incomplete."
                }
            } finally {
                $zip.Dispose()
            }
            return
        } catch {
            if ($attempt -eq 5) {
                throw "Could not create the portable ZIP after 5 attempts: $($_.Exception.Message)"
            }
            Write-Host "ZIP is temporarily locked (probably by antivirus). Retrying in 2 seconds..." -ForegroundColor Yellow
            Start-Sleep -Seconds 2
        }
    }
}

Remove-BuildFolder $buildDir
Remove-BuildFolder $outputDir
New-Item -ItemType Directory -Force -Path $classesDir, $libDir, $outputDir | Out-Null

# --- Compile ----------------------------------------------------------------
$sourceRoot = Join-Path $root "src"
$sources = Get-ChildItem -Path $sourceRoot -Recurse -Filter *.java |
    ForEach-Object { $_.FullName }
if (-not $sources) {
    throw "No Java sources found in '$sourceRoot'."
}

& "$jdk\bin\javac.exe" -encoding UTF-8 --release 17 -d $classesDir $sources
if ($LASTEXITCODE -ne 0) { throw "Compile error." }

& "$jdk\bin\jar.exe" --create --file (Join-Path $libDir "aero.jar") --main-class com.quickchat.Main -C $classesDir .
if ($LASTEXITCODE -ne 0) { throw "JAR creation error." }

# --- Package ----------------------------------------------------------------
$common = @(
    "--name", "Aero",
    "--app-version", $Version,
    "--vendor", "Aero",
    "--description", "Aero - Lightweight Chat",
    "--input", $libDir,
    "--main-jar", "aero.jar",
    "--main-class", "com.quickchat.Main",
    "--add-modules", "java.base,java.desktop,java.logging",
    "--java-options", "-Xmx128m",
    "--java-options", "-Dfile.encoding=UTF-8",
    "--dest", $outputDir
)
if (Test-Path "icon.ico") { $common += @("--icon", "icon.ico") }

& "$jdk\bin\jpackage.exe" --type app-image @common
if ($LASTEXITCODE -ne 0) { throw "jpackage (app-image) error." }

if (-not (Test-Path -LiteralPath $exePath)) {
    throw "jpackage did not create '$exePath'."
}

# --- Portable ZIP -----------------------------------------------------------
New-PortableZip $appDir $zipPath

$zipSizeMb = [math]::Round((Get-Item -LiteralPath $zipPath).Length / 1MB, 1)
Write-Host "`nDone:" -ForegroundColor Green
Write-Host "  output\Aero\Aero.exe" -ForegroundColor Green
Write-Host "  output\Aero-portable.zip ($zipSizeMb MB)" -ForegroundColor Green

# --- Optional installer -----------------------------------------------------
if ($Installer) {
    & "$jdk\bin\jpackage.exe" --type exe @common --win-menu --win-shortcut --win-dir-chooser --win-per-user-install
    if ($LASTEXITCODE -ne 0) {
        Write-Warning "Setup could not be created (is WiX installed?). The portable version is still available."
    } else {
        Write-Host "  output\Aero-$Version.exe" -ForegroundColor Green
    }
}
