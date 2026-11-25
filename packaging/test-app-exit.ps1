param([string]$Launcher = (Join-Path $PSScriptRoot '../target/windows-build/image/AutoCheck/AutoCheck.exe'))
$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$launcherPath = [IO.Path]::GetFullPath($Launcher)
if (-not (Test-Path -LiteralPath $launcherPath -PathType Leaf)) { throw "Launcher not found: $launcherPath" }
function Get-TestProcesses {
    @(Get-Process -Name AutoCheck -ErrorAction SilentlyContinue | Where-Object { $_.Path -eq $launcherPath })
}
if ((Get-TestProcesses).Count -gt 0) { throw 'Refusing to close an already running instance of this application image.' }
$testRoot = Join-Path $root ('target/app-exit-check/' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $testRoot -Force | Out-Null
$oldLocalAppData = $env:LOCALAPPDATA
$oldJavaOptions = $env:JAVA_TOOL_OPTIONS
$oldJdkOptions = $env:JDK_JAVA_OPTIONS
$oldPrivateOptions = $env:_JAVA_OPTIONS
[xml]$project = Get-Content -LiteralPath (Join-Path $root 'pom.xml') -Raw
$releaseVersion = [string]$project.project.version
$runs = @()
try {
    # Keep the real user's progress, settings and lock untouched.
    $env:LOCALAPPDATA = $testRoot
    $env:JAVA_TOOL_OPTIONS = $null
    $env:JDK_JAVA_OPTIONS = $null
    $env:_JAVA_OPTIONS = $null
    # This check targets closing the main window after onboarding. First-launch notice
    # acknowledgment and repeat-start behavior are covered by ReleaseNotesTest.
    $testData = Join-Path $testRoot 'AutoCheck'
    New-Item -ItemType Directory -Path $testData -Force | Out-Null
    @{ acknowledgedVersions = @(@('1.2.1',$releaseVersion) | Select-Object -Unique) } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $testData 'release-notes.json') -Encoding utf8
    foreach ($run in 1..2) {
        $process = Start-Process -FilePath $launcherPath -WorkingDirectory $env:TEMP -WindowStyle Hidden -PassThru
        $deadline = [DateTime]::UtcNow.AddSeconds(20)
        $windowProcess = $null
        do {
            $windowProcess = Get-TestProcesses | Where-Object { $_.MainWindowHandle -ne [IntPtr]::Zero } | Select-Object -First 1
            if ($windowProcess) { break }
            if ($process.HasExited) { throw "Launcher exited before opening its window (run $run, code $($process.ExitCode))." }
            Start-Sleep -Milliseconds 100
        } while ([DateTime]::UtcNow -lt $deadline)
        if (-not $windowProcess) { throw "Application window did not appear (run $run)." }
        if (-not $windowProcess.CloseMainWindow()) { throw "Could not request window close (run $run)." }
        $deadline = [DateTime]::UtcNow.AddSeconds(15)
        while ((Get-TestProcesses).Count -gt 0 -and [DateTime]::UtcNow -lt $deadline) { Start-Sleep -Milliseconds 100 }
        if ((Get-TestProcesses).Count -gt 0) { throw "Native launcher/JVM remained after window close (run $run)." }
        if (-not $process.WaitForExit(5000) -or $process.ExitCode -ne 0) { throw "Native launcher did not exit cleanly (run $run)." }
        $lockPath = Join-Path $testRoot 'AutoCheck/app.lock'
        $lock = [IO.File]::Open($lockPath, [IO.FileMode]::Open, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
        $lock.Dispose()
        $runs += [ordered]@{ run = $run; exitCode = $process.ExitCode; remainingProcesses = 0; lockReleased = $true }
    }
    $report = [ordered]@{ status = 'passed'; launcher = $launcherPath; launcherSha256 = (Get-FileHash -LiteralPath $launcherPath -Algorithm SHA256).Hash; runs = $runs }
    $report | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $testRoot 'app-exit-check.json') -Encoding utf8
    $imageCheck = Join-Path $root 'target/windows-build/app-exit-check.json'
    $report | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $imageCheck -Encoding utf8
    Write-Output 'NATIVE_CLOSE_RELAUNCH_PASSED'
} finally {
    $env:LOCALAPPDATA = $oldLocalAppData
    $env:JAVA_TOOL_OPTIONS = $oldJavaOptions
    $env:JDK_JAVA_OPTIONS = $oldJdkOptions
    $env:_JAVA_OPTIONS = $oldPrivateOptions
    # Only this test image can be terminated, and only if the test left it running.
    Get-TestProcesses | Stop-Process -Force -ErrorAction SilentlyContinue
}
