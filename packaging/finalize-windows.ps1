$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$distribution = Join-Path $root 'dist'
$release = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'cache/temurin-release.json') -Raw | ConvertFrom-Json
$reportPath = Join-Path $root 'target/windows-build/distribution-check.json'
$check = Get-Content -LiteralPath $reportPath -Raw | ConvertFrom-Json
if ($check.status -ne 'passed') { throw 'Distribution self-test has not passed' }
$artifacts = @('AutoCheck-1.1.2.exe','AutoCheck-1.1.2-Windows-x64-Portable.zip')
foreach ($name in $artifacts) { if (-not (Test-Path -LiteralPath (Join-Path $distribution $name))) { throw "Missing artifact: $name" } }
Copy-Item -LiteralPath $reportPath -Destination (Join-Path $distribution 'distribution-check.json') -Force
Copy-Item -LiteralPath (Join-Path $PSScriptRoot '使用说明.txt') -Destination $distribution -Force
$files = @($artifacts | ForEach-Object {
    $item = Get-Item -LiteralPath (Join-Path $distribution $_)
    [ordered]@{ name = $item.Name; bytes = $item.Length; sha256 = (Get-FileHash -LiteralPath $item.FullName -Algorithm SHA256).Hash }
})
$installationTest = 'not run'
$installationReport = Join-Path $distribution 'installation-check.json'
if (Test-Path -LiteralPath $installationReport) {
    $installation = Get-Content -LiteralPath $installationReport -Raw | ConvertFrom-Json
    if ($installation.installerSha256 -eq $files[0].sha256 -and $installation.status -eq 'passed') { $installationTest = 'passed' }
}
$manifest = [ordered]@{
    applicationVersion = '1.1.2'; platform = 'Windows x64'; javaVersion = $check.javaVersion
    installationScope = 'per-machine'; installationRequiresAdministrator = $true
    javaSource = $release.binary.package.link; javaSha256 = $release.binary.package.checksum
    browserRevision = '1255'; browserVersion = $check.browserVersion
    buildCheck = 'passed'; installationAndUninstallCheck = $installationTest; includesUserData = $false
    files = $files
}
$exitCheckPath = Join-Path $root 'target/windows-build/app-exit-check.json'
if (Test-Path -LiteralPath $exitCheckPath) {
    $exitCheck = Get-Content -LiteralPath $exitCheckPath -Raw | ConvertFrom-Json
    $imageLauncher = Join-Path $root 'target/windows-build/image/AutoCheck/AutoCheck.exe'
    if ($exitCheck.status -eq 'passed' -and $exitCheck.launcherSha256 -eq (Get-FileHash -LiteralPath $imageLauncher -Algorithm SHA256).Hash) {
        $manifest.nativeCloseAndRelaunchCheck = 'passed'
        Copy-Item -LiteralPath $exitCheckPath -Destination (Join-Path $distribution 'app-exit-check.json') -Force
    }
}
$manifest | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $distribution 'build-manifest.json') -Encoding utf8
($files | ForEach-Object { $_.sha256 + '  ' + $_.name }) | Set-Content -LiteralPath (Join-Path $distribution 'SHA256SUMS.txt') -Encoding ascii
Write-Output 'WINDOWS_DISTRIBUTION_READY'
Get-ChildItem -LiteralPath $distribution -File | Select-Object Name,Length
