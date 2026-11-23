param([string]$Installer = (Join-Path $PSScriptRoot '../dist/AutoCheck-1.1.0.exe'))
$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$installerPath = [IO.Path]::GetFullPath($Installer)
$testRoot = Join-Path $root ('target/installer-check/' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $testRoot -Force | Out-Null
$installDirectory = Join-Path $testRoot 'AutoCheck'

Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
public static class AutoCheckInstallerResources {
    [DllImport("kernel32.dll", CharSet=CharSet.Unicode, SetLastError=true)]
    private static extern IntPtr LoadLibraryEx(string name, IntPtr reserved, uint flags);
    [DllImport("kernel32.dll", CharSet=CharSet.Unicode, SetLastError=true)]
    private static extern IntPtr FindResource(IntPtr module, string name, IntPtr type);
    [DllImport("kernel32.dll", SetLastError=true)]
    private static extern IntPtr LoadResource(IntPtr module, IntPtr resource);
    [DllImport("kernel32.dll")]
    private static extern IntPtr LockResource(IntPtr resource);
    [DllImport("kernel32.dll")]
    private static extern uint SizeofResource(IntPtr module, IntPtr resource);
    [DllImport("kernel32.dll")]
    private static extern bool FreeLibrary(IntPtr module);
    [DllImport("msi.dll", CharSet=CharSet.Unicode)]
    public static extern int MsiQueryProductState(string productCode);
    public static byte[] Read(string file, string name) {
        IntPtr module = LoadLibraryEx(file, IntPtr.Zero, 0x22);
        if (module == IntPtr.Zero) throw new InvalidOperationException("Unable to read installer resources");
        try {
            IntPtr resource = FindResource(module, name, new IntPtr(10));
            if (resource == IntPtr.Zero) throw new InvalidOperationException("Missing installer resource: " + name);
            byte[] bytes = new byte[SizeofResource(module, resource)];
            Marshal.Copy(LockResource(LoadResource(module, resource)), bytes, 0, bytes.Length);
            return bytes;
        } finally { FreeLibrary(module); }
    }
}
'@

$productCode = [Text.Encoding]::UTF8.GetString([AutoCheckInstallerResources]::Read($installerPath, 'product_code')).Trim([char]0)
$productCode = [Guid]::Parse($productCode).ToString('B')
if ([AutoCheckInstallerResources]::MsiQueryProductState($productCode) -ne -1) {
    throw "The product is already registered. Refusing to alter an existing installation: $productCode"
}
$installLog = Join-Path $testRoot 'install.log'
$uninstallLog = Join-Path $testRoot 'uninstall.log'
$report = Join-Path $testRoot 'installed-check.json'
$outcome = [ordered]@{ productCode = $productCode; installerSha256 = (Get-FileHash -LiteralPath $installerPath -Algorithm SHA256).Hash; status = 'failed' }
try {
    $process = Start-Process -FilePath $installerPath -ArgumentList @('/qn','/norestart',('INSTALLDIR="' + $installDirectory + '"'),'JP_INSTALL_DESKTOP_SHORTCUT=','JP_INSTALL_STARTMENU_SHORTCUT=','/l*v',('"' + $installLog + '"')) -WindowStyle Hidden -PassThru
    if (-not $process.WaitForExit(180000)) { throw 'Installer test timed out' }
    if ($process.ExitCode -notin @(0,3010)) { throw "Installer exited with $($process.ExitCode). See $installLog" }
    $outcome.install = 'passed'
    $launcher = Join-Path $installDirectory 'AutoCheck.exe'
    if (-not (Test-Path -LiteralPath $launcher)) { throw 'Installed launcher not found' }
    $oldPath = $env:PATH
    $oldJavaHome = $env:JAVA_HOME
    try {
        $env:PATH = Join-Path $env:SystemRoot 'System32'
        $env:JAVA_HOME = Join-Path $testRoot 'no-java'
        $app = Start-Process -FilePath $launcher -ArgumentList @('--self-test',('"' + $report + '"')) -WorkingDirectory $env:TEMP -WindowStyle Hidden -PassThru
        if (-not $app.WaitForExit(90000)) { $app.Kill(); throw 'Installed application test timed out' }
        $check = Get-Content -LiteralPath $report -Raw | ConvertFrom-Json
        if ($app.ExitCode -ne 0 -or $check.status -ne 'passed') { throw 'Installed application self-test failed' }
        if (-not $check.javaHome.StartsWith($installDirectory, [StringComparison]::OrdinalIgnoreCase)) { throw 'Installed application used an external Java runtime' }
        if (-not $check.browserExecutable.StartsWith($installDirectory, [StringComparison]::OrdinalIgnoreCase)) { throw 'Installed application used an external browser' }
        $outcome.installedApplication = 'passed'
    } finally { $env:PATH = $oldPath; $env:JAVA_HOME = $oldJavaHome }
} finally {
    if ([AutoCheckInstallerResources]::MsiQueryProductState($productCode) -ne -1) {
        $uninstall = Start-Process -FilePath (Join-Path $env:SystemRoot 'System32/msiexec.exe') -ArgumentList @('/x',$productCode,'/qn','/norestart','/l*v',('"' + $uninstallLog + '"')) -WindowStyle Hidden -PassThru
        if (-not $uninstall.WaitForExit(180000)) { throw 'Uninstaller test timed out' }
        if ($uninstall.ExitCode -notin @(0,3010) -or [AutoCheckInstallerResources]::MsiQueryProductState($productCode) -ne -1) { throw "Uninstall test failed. See $uninstallLog" }
        $outcome.uninstall = 'passed'
    }
    if ($outcome.install -eq 'passed' -and $outcome.installedApplication -eq 'passed' -and $outcome.uninstall -eq 'passed') { $outcome.status = 'passed' }
    $outcome | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $root 'dist/installation-check.json') -Encoding utf8
}
Write-Output 'INSTALLATION_AND_UNINSTALL_PASSED'
