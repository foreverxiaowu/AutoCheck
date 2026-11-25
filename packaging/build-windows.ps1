param([switch]$SkipBuild)
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
Set-Location -LiteralPath $projectRoot
$cache = Join-Path $PSScriptRoot 'cache'
$workRoot = Join-Path $projectRoot 'target/windows-build'
$distribution = Join-Path $projectRoot 'dist'
New-Item -ItemType Directory -Path $cache, $distribution -Force | Out-Null

function Reset-BuildDirectory([string]$path) {
    $resolved = [IO.Path]::GetFullPath($path)
    $allowed = [IO.Path]::GetFullPath($workRoot)
    if ($resolved -ne $allowed -and -not $resolved.StartsWith($allowed + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to remove a directory outside the packaging workspace: $resolved"
    }
    if (Test-Path -LiteralPath $resolved) { Remove-Item -LiteralPath $resolved -Recurse -Force }
    New-Item -ItemType Directory -Path $resolved -Force | Out-Null
}

if (-not $SkipBuild) {
    & mvn -q package
    if ($LASTEXITCODE -ne 0) { throw 'Maven build failed' }
}
$jar = Join-Path $projectRoot 'target/autocheck-1.2.1.jar'
if (-not (Test-Path -LiteralPath $jar)) { throw "Missing application JAR: $jar" }

# Only Eclipse Temurin's OpenJDK runtime is redistributed, with its legal directory intact.
$metadataPath = Join-Path $cache 'temurin-release.json'
if (-not (Test-Path -LiteralPath $metadataPath)) {
    $assets = Invoke-RestMethod 'https://api.adoptium.net/v3/assets/latest/17/hotspot?architecture=x64&image_type=jre&os=windows&vendor=eclipse'
    @($assets)[0] | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $metadataPath -Encoding utf8
}
$release = Get-Content -LiteralPath $metadataPath -Raw | ConvertFrom-Json
$runtimeZip = Join-Path $cache 'temurin17-jre.zip'
if (-not (Test-Path -LiteralPath $runtimeZip)) {
    Invoke-WebRequest -Uri $release.binary.package.link -OutFile $runtimeZip -TimeoutSec 600
}
if ((Get-FileHash -LiteralPath $runtimeZip -Algorithm SHA256).Hash -ne $release.binary.package.checksum) { throw 'Temurin runtime checksum mismatch' }
$runtimeRoot = Join-Path $cache 'runtime'
if (-not (Test-Path -LiteralPath $runtimeRoot)) { Expand-Archive -LiteralPath $runtimeZip -DestinationPath $runtimeRoot }
$runtime = (Get-ChildItem -LiteralPath $runtimeRoot -Directory | Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'bin/java.exe') } | Select-Object -First 1).FullName
if (-not $runtime) { throw 'Temurin runtime directory not found' }

# Version matches com.microsoft.playwright 1.65.1. Download only if it is not already cached.
$browserRevision = '1255'
$cachedBrowser = Join-Path $cache "browsers/chromium-$browserRevision/chrome-win64"
$userBrowser = Join-Path $env:LOCALAPPDATA "ms-playwright/chromium-$browserRevision/chrome-win64"
if (Test-Path -LiteralPath (Join-Path $cachedBrowser 'chrome.exe')) { $browser = $cachedBrowser }
elseif (Test-Path -LiteralPath (Join-Path $userBrowser 'chrome.exe')) { $browser = $userBrowser }
else {
    $oldBrowserPath = $env:PLAYWRIGHT_BROWSERS_PATH
    try {
        $env:PLAYWRIGHT_BROWSERS_PATH = Join-Path $cache 'browsers'
        & java -cp $jar com.microsoft.playwright.CLI install chromium --no-shell
        if ($LASTEXITCODE -ne 0) { throw 'Browser download failed' }
    } finally { $env:PLAYWRIGHT_BROWSERS_PATH = $oldBrowserPath }
    $browser = $cachedBrowser
}
if (-not (Test-Path -LiteralPath (Join-Path $browser 'chrome.exe'))) { throw 'Browser executable missing' }

$wix = Join-Path $cache 'wix'
if (-not (Test-Path -LiteralPath (Join-Path $wix 'candle.exe'))) {
    $wixZip = Join-Path $cache 'wix314-binaries.zip'
    Invoke-WebRequest -Uri 'https://github.com/wixtoolset/wix3/releases/download/wix3141rtm/wix314-binaries.zip' -OutFile $wixZip -TimeoutSec 600
    Expand-Archive -LiteralPath $wixZip -DestinationPath $wix -Force
}

Reset-BuildDirectory $workRoot
$stagingDistribution = Join-Path $workRoot 'distribution'
New-Item -ItemType Directory -Path $stagingDistribution -Force | Out-Null
$inputDirectory = Join-Path $workRoot 'input'
$imageDirectory = Join-Path $workRoot 'image'
New-Item -ItemType Directory -Path $inputDirectory,$imageDirectory -Force | Out-Null
Copy-Item -LiteralPath $jar -Destination $inputDirectory
Copy-Item -LiteralPath $browser -Destination (Join-Path $inputDirectory 'browser') -Recurse
Copy-Item -LiteralPath (Join-Path $PSScriptRoot '使用说明.txt'),(Join-Path $PSScriptRoot 'THIRD-PARTY-NOTICES.txt') -Destination $inputDirectory
Copy-Item -LiteralPath (Join-Path $projectRoot 'examples/waybills.txt') -Destination (Join-Path $inputDirectory '示例运单号.txt')
Copy-Item -LiteralPath (Join-Path $projectRoot 'examples/17track-waybills.txt'),(Join-Path $projectRoot 'examples/sf-waybills.txt') -Destination $inputDirectory
Copy-Item -LiteralPath (Join-Path $projectRoot 'examples/ewe-waybills.txt') -Destination $inputDirectory
Copy-Item -LiteralPath (Join-Path $projectRoot 'examples/4px-waybills.txt') -Destination $inputDirectory

& (Join-Path $PSScriptRoot 'new-icon.ps1')
$icon = Join-Path $PSScriptRoot 'autocheck.ico'
& jpackage --type app-image --name AutoCheck --app-version 1.2.1 --vendor AutoCheck --description 'Multi-carrier batch tracking: YTO, 17TRACK, SF, EWE and 4PX' --input $inputDirectory --main-jar autocheck-1.2.1.jar --main-class cn.autocheck.Main --runtime-image $runtime --dest $imageDirectory --icon $icon --java-options '-Dfile.encoding=UTF-8'
if ($LASTEXITCODE -ne 0) { throw 'jpackage application image failed' }
$image = Join-Path $imageDirectory 'AutoCheck'

# Exercise the native launcher with Java and browsers absent from PATH.
$oldPath = $env:PATH
$oldJavaHome = $env:JAVA_HOME
$oldJdkOptions = $env:JDK_JAVA_OPTIONS
$oldJavaToolOptions = $env:JAVA_TOOL_OPTIONS
$report = Join-Path $workRoot 'distribution-check.json'
try {
    $env:PATH = Join-Path $env:SystemRoot 'System32'
    $env:JAVA_HOME = Join-Path $workRoot 'java-is-not-installed'
    $env:JDK_JAVA_OPTIONS = $null
    $env:JAVA_TOOL_OPTIONS = $null
    $process = Start-Process -FilePath (Join-Path $image 'AutoCheck.exe') -ArgumentList @('--self-test', ('"' + $report + '"')) -WorkingDirectory $env:TEMP -WindowStyle Hidden -PassThru
    if (-not $process.WaitForExit(90000)) { $process.Kill(); throw 'Distribution check timed out' }
    if ($process.ExitCode -ne 0 -or -not (Test-Path -LiteralPath $report)) { throw "Distribution check failed: exit $($process.ExitCode)" }
    $check = Get-Content -LiteralPath $report -Raw | ConvertFrom-Json
    if ($check.status -ne 'passed') { throw "Distribution check failed: $($check.error)" }
    if (-not $check.javaHome.StartsWith($image, [StringComparison]::OrdinalIgnoreCase)) { throw 'Distribution used an external Java runtime' }
    if (-not $check.browserExecutable.StartsWith($image, [StringComparison]::OrdinalIgnoreCase)) { throw 'Distribution used an external browser' }
} finally {
    $env:PATH = $oldPath
    $env:JAVA_HOME = $oldJavaHome
    $env:JDK_JAVA_OPTIONS = $oldJdkOptions
    $env:JAVA_TOOL_OPTIONS = $oldJavaToolOptions
}

& (Join-Path $PSScriptRoot 'test-app-exit.ps1') -Launcher (Join-Path $image 'AutoCheck.exe')

$env:PATH = $wix + [IO.Path]::PathSeparator + $oldPath
try {
    # Machine-wide MSI requests elevation for install/uninstall, including rollback-file security.
    # Keep the upgrade family, but old per-user installations must be removed in their original context.
    & jpackage --type exe --name AutoCheck --app-version 1.2.1 --vendor AutoCheck --app-image $image --icon $icon --dest $stagingDistribution --install-dir AutoCheckApp --win-dir-chooser --win-menu --win-menu-group AutoCheck --win-shortcut --win-shortcut-prompt --win-upgrade-uuid '48a785ab-b688-4c8b-a090-a0b3ed5cfb3a' --temp (Join-Path $workRoot 'installer')
    if ($LASTEXITCODE -ne 0) { throw 'jpackage Windows installer failed' }
} finally { $env:PATH = $oldPath }

$portableZip = Join-Path $stagingDistribution 'AutoCheck-1.2.1-Windows-x64-Portable.zip'
Compress-Archive -LiteralPath $image -DestinationPath $portableZip -CompressionLevel Optimal -Force
Copy-Item -LiteralPath (Join-Path $stagingDistribution 'AutoCheck-1.2.1.exe'),$portableZip -Destination $distribution -Force
Copy-Item -LiteralPath $report -Destination (Join-Path $distribution 'distribution-check.json')
& (Join-Path $PSScriptRoot 'finalize-windows.ps1')
