param([Parameter(Mandatory=$true)][string]$Msi, [Parameter(Mandatory=$true)][string]$ExpectedVersion)
$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$msiPath = (Resolve-Path -LiteralPath $Msi).Path
$installer = New-Object -ComObject WindowsInstaller.Installer
$database = $installer.OpenDatabase($msiPath, 0) # Read-only: never installs or changes the registered product.
try {
$properties = @{}
$view = $database.OpenView('SELECT * FROM Property')
try {
    $view.Execute()
    while ($record = $view.Fetch()) {
        $properties[$record.StringData(1)] = $record.StringData(2)
        [Runtime.InteropServices.Marshal]::FinalReleaseComObject($record) | Out-Null
    }
} finally { $view.Close(); [Runtime.InteropServices.Marshal]::FinalReleaseComObject($view) | Out-Null }
if ($properties.ProductVersion -ne $ExpectedVersion) { throw 'Installer version does not match pom.xml.' }
if ($properties.UpgradeCode -ne '{48A785AB-B688-4C8B-A090-A0B3ED5CFB3A}') { throw 'Installer upgrade family changed unexpectedly.' }
$upgrades = @()
$view = $database.OpenView('SELECT * FROM Upgrade')
try {
    $view.Execute()
    while ($record = $view.Fetch()) {
        $upgrades += [ordered]@{ upgradeCode = $record.StringData(1); maximumVersion = $record.StringData(3); action = $record.StringData(7) }
        [Runtime.InteropServices.Marshal]::FinalReleaseComObject($record) | Out-Null
    }
} finally { $view.Close(); [Runtime.InteropServices.Marshal]::FinalReleaseComObject($view) | Out-Null }
if (-not ($upgrades | Where-Object { $_.upgradeCode -eq $properties.UpgradeCode -and $_.maximumVersion -eq $ExpectedVersion -and $_.action -eq 'JP_UPGRADABLE_FOUND' })) {
    throw 'Installer is missing the older-version upgrade rule.'
}
$existing = @(Get-ItemProperty -Path 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\*','HKLM:\Software\Microsoft\Windows\CurrentVersion\Uninstall\*' -ErrorAction SilentlyContinue |
    Where-Object { $_.DisplayName -eq 'AutoCheck' })
foreach ($app in $existing) {
    if ($app.PSChildName -eq $properties.ProductCode) { throw 'ProductCode matches an installed package: increase the application version to avoid error 1638.' }
}
$report = [ordered]@{
    status = 'passed'; version = $properties.ProductVersion; productCode = $properties.ProductCode; upgradeCode = $properties.UpgradeCode
    previousVersions = @($existing | ForEach-Object { [ordered]@{ version = $_.DisplayVersion; productCode = $_.PSChildName } })
    olderVersionUpgradeRule = 'passed'; installationPerformed = $false
}
$report | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $root 'target/windows-build/installer-identity-check.json') -Encoding utf8
Write-Output 'INSTALLER_IDENTITY_CHECK_PASSED'
} finally {
    [Runtime.InteropServices.Marshal]::FinalReleaseComObject($database) | Out-Null
    [Runtime.InteropServices.Marshal]::FinalReleaseComObject($installer) | Out-Null
}
