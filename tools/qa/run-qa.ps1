# SPDX-FileCopyrightText: 2026 Focalis contributors
# SPDX-License-Identifier: LGPL-3.0-only

<#
.SYNOPSIS
Runs Focalis graphics QA scenarios in the development client and collects a report.

.DESCRIPTION
Each scenario launches runClient in QA mode with its own game folder under build/qa/game, waits for the
in-game probe to finish, and collects probe.json, logs and screenshots into build/qa/latest. The exit code is
0 when every scenario passed, 1 when any failed and 2 when the run couldn't start. See tools/qa/README.md.

.EXAMPLE
.\tools\qa\run-qa.ps1 -Scenario post-process-smoke

.EXAMPLE
.\tools\qa\run-qa.ps1 -Scenario all -AllowFullscreen
#>
[CmdletBinding()]
param(
    # Scenario names, or "all".
    [string[]]$Scenario,
    # Lists the scenarios and exits.
    [switch]$List,
    # Lets the resize scenario switch to fullscreen, which takes over the whole screen for a moment.
    [switch]$AllowFullscreen,
    # Uses runObfClient, the reobfuscated release jar, instead of runClient.
    [switch]$Obfuscated,
    # Turns on the experimental world target in every scenario, not just the ones that need it.
    [switch]$WorldTarget,
    # Turns on the experimental world programs in every scenario, with the focalis-world-routes test pack.
    [switch]$WorldPrograms,
    [int]$TimeoutSeconds = 420,
    # A running client that stops updating probe.json this long is treated as hung and gets a thread dump.
    [int]$StallSeconds = 90
)

Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'

# Shaders config for each scenario. The scenario steps themselves live in QaScenario.java.
$Scenarios = [ordered]@{
    'vanilla-baseline'               = @{ Shaders = $false; Pack = '' }
    'post-process-smoke'             = @{ Shaders = $true; Pack = 'focalis-depth-view' }
    'post-process-state-restore'     = @{ Shaders = $true; Pack = 'focalis-depth-view' }
    'post-process-high-texture-unit' = @{ Shaders = $true; Pack = 'focalis-depth-view' }
    'post-process-resize'            = @{ Shaders = $true; Pack = 'focalis-depth-view' }
    'post-process-failure'           = @{ Shaders = $true; Pack = 'focalis-depth-view' }
    'post-process-bad-pack'          = @{ Shaders = $true; Pack = 'focalis-broken' }
    'world-reload'                   = @{ Shaders = $true; Pack = 'focalis-depth-view' }
    'world-lifecycle'                = @{ Shaders = $true; Pack = 'focalis-depth-view' }
    'render-stages'                  = @{ Shaders = $true; Pack = 'focalis-depth-view' }
    'world-target-failure'           = @{ Shaders = $false; Pack = ''; WorldTarget = $true }
    'world-program-binding'          = @{ Shaders = $false; Pack = ''; WorldTarget = $true; WorldPrograms = $true }
    'world-program-failure'          = @{ Shaders = $false; Pack = ''; WorldTarget = $true; WorldPrograms = $true }
    'world-program-bad-pack'         = @{ Shaders = $false; Pack = ''; WorldTarget = $true; WorldPrograms = $true
        ProgramsPack = 'focalis-world-routes-broken' }
    'world-program-dimensions'       = @{ Shaders = $false; Pack = ''; WorldPrograms = $true
        ProgramsPack = 'focalis-dimension-routes' }
    'world-program-dimensions-zip'   = @{ Shaders = $false; Pack = ''; WorldPrograms = $true
        ProgramsPack = 'focalis-dimension-routes.zip' }
    'world-program-samplers'         = @{ Shaders = $false; Pack = ''; WorldTarget = $true; WorldPrograms = $true
        ProgramsPack = 'focalis-world-samplers' }
    'samplers-vanilla-reference'     = @{ Shaders = $false; Pack = '' }
    'world-program-frame-inputs'     = @{ Shaders = $false; Pack = ''; WorldPrograms = $true }
}

# Log lines that are expected in every run, and extra ones a scenario causes on purpose.
$AlwaysAllowed = @('Development QA mode is on', 'SignedJWT')
$ScenarioAllowed = @{
    'post-process-failure'  = @('Focalis QA injected failure', "Render listener of 'shaders' failed",
        "Feature 'shaders' is disabled for this session")
    'post-process-bad-pack' = @('The post pass stopped for this session')
    'world-target-failure'  = @('Focalis QA injected failure', "Render listener of 'world_target' failed",
        "Feature 'world_target' is disabled for this session")
    'world-program-failure' = @('Focalis QA injected failure', "Render listener of 'world_programs' failed",
        "Feature 'world_programs' is disabled for this session")
    'world-program-bad-pack' = @("can't be built, so", 'None of the world programs in shaders could be built')
    'world-program-dimensions' = @("world-1/gbuffers_textured can't be built, so",
        'None of the world programs in shaders/world-1 could be built')
    'world-program-dimensions-zip' = @("world-1/gbuffers_textured can't be built, so",
        'None of the world programs in shaders/world-1 could be built')
}

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$QaRoot = Join-Path $RepoRoot 'build\qa'
$GameDir = Join-Path $QaRoot 'game'
$LatestDir = Join-Path $QaRoot 'latest'
$TestPack = Join-Path $RepoRoot 'src\test\resources\shaderpacks\focalis-depth-view'
$RoutesPack = Join-Path $RepoRoot 'src\test\resources\shaderpacks\focalis-world-routes'
$DimensionsPack = Join-Path $RepoRoot 'src\test\resources\shaderpacks\focalis-dimension-routes'

if (-not ('FocalisQaWindow' -as [type])) {
    Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;

public static class FocalisQaWindow {
    [StructLayout(LayoutKind.Sequential)] struct RECT { public int Left, Top, Right, Bottom; }
    [DllImport("user32.dll")] static extern IntPtr SetThreadDpiAwarenessContext(IntPtr context);
    [DllImport("user32.dll")] static extern bool GetClientRect(IntPtr window, out RECT rect);
    [DllImport("user32.dll")] static extern bool GetWindowRect(IntPtr window, out RECT rect);
    [DllImport("user32.dll")] static extern bool MoveWindow(IntPtr window, int x, int y, int width, int height, bool repaint);

    static readonly IntPtr Unaware = new IntPtr(-1);

    // Minecraft isn't DPI aware, so the size it should see is set in its own coordinates.
    public static void ResizeClient(IntPtr window, int width, int height) {
        IntPtr previous = SetThreadDpiAwarenessContext(Unaware);
        try {
            RECT outer, inner;
            GetWindowRect(window, out outer);
            GetClientRect(window, out inner);
            int extraWidth = (outer.Right - outer.Left) - inner.Right;
            int extraHeight = (outer.Bottom - outer.Top) - inner.Bottom;
            MoveWindow(window, outer.Left, outer.Top, width + extraWidth, height + extraHeight, true);
        } finally {
            SetThreadDpiAwarenessContext(previous);
        }
    }
}
'@
}

function Write-Utf8([string]$Path, [string]$Text) {
    [IO.File]::WriteAllText($Path, $Text, (New-Object Text.UTF8Encoding($false)))
}

function Get-RelativePath([string]$Base, [string]$Path) {
    $baseUri = New-Object Uri(($Base.TrimEnd('\') + '\'))
    return [Uri]::UnescapeDataString($baseUri.MakeRelativeUri((New-Object Uri($Path))).ToString())
}

# Which source was tested. Git trouble only leaves these empty, it never fails the run.
function Get-SourceInfo {
    $source = [ordered]@{ head = $null; branch = $null; dirty = $null }
    if (-not (Get-Command git -CommandType Application -ErrorAction SilentlyContinue)) { return $source }
    $ErrorActionPreference = 'Continue'
    try {
        $head = & git -C $RepoRoot rev-parse HEAD 2>$null
        if ($LASTEXITCODE -eq 0 -and $head) { $source.head = "$head".Trim() }
        # Fails on a detached HEAD, which simply leaves the branch empty.
        $branch = & git -C $RepoRoot symbolic-ref --quiet --short HEAD 2>$null
        if ($LASTEXITCODE -eq 0 -and $branch) { $source.branch = "$branch".Trim() }
        # Lists tracked changes and untracked files, but never ignored ones like build, run or references.
        $status = @(& git -C $RepoRoot status --porcelain 2>$null)
        if ($LASTEXITCODE -eq 0) { $source.dirty = $status.Count -gt 0 }
    } catch { }
    return $source
}

# The release jar runObfClient actually loaded, straight from the QA mods folder.
function Get-ArtifactInfo {
    $jar = Get-ChildItem (Join-Path $GameDir 'mods') -Filter 'focalis-*.jar' -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if (-not $jar) { return $null }
    return [ordered]@{ file = $jar.Name; sha256 = (Get-FileHash -Algorithm SHA256 $jar.FullName).Hash.ToLowerInvariant() }
}

# Each scenario stages its own copy of the release jar. The run only has one when every scenario found a jar and
# they are all the same.
function Get-RunArtifact($Results) {
    $artifacts = New-Object System.Collections.Generic.List[object]
    foreach ($result in @($Results)) {
        if (-not $result.artifact) { return $null }
        $artifacts.Add($result.artifact)
    }
    $hashes = @($artifacts | ForEach-Object { $_.sha256 } | Select-Object -Unique)
    if ($artifacts.Count -eq 0 -or $hashes.Count -ne 1) { return $null }
    return [ordered]@{ file = $artifacts[0].file; sha256 = $hashes[0] }
}

# Stops a process and everything started under it, children first. A child can't be older than its parent, so a
# reused process id never pulls in an unrelated process. Returns how many processes were stopped.
function Stop-ProcessTree([int]$RootId) {
    $all = @(Get-CimInstance Win32_Process)
    $root = $all | Where-Object { $_.ProcessId -eq $RootId } | Select-Object -First 1
    if (-not $root) { return 0 }
    $tree = New-Object System.Collections.Generic.List[object]
    $parents = New-Object System.Collections.Generic.Queue[object]
    $parents.Enqueue($root)
    while ($parents.Count -gt 0) {
        $parent = $parents.Dequeue()
        foreach ($process in $all) {
            if ($process.ParentProcessId -eq $parent.ProcessId -and $process.ProcessId -ne $parent.ProcessId -and
                $process.CreationDate -ge $parent.CreationDate -and -not $tree.Contains($process)) {
                $tree.Add($process)
                $parents.Enqueue($process)
            }
        }
    }
    for ($i = $tree.Count - 1; $i -ge 0; $i--) {
        Stop-Process -Id $tree[$i].ProcessId -Force -ErrorAction SilentlyContinue
    }
    Stop-Process -Id $RootId -Force -ErrorAction SilentlyContinue
    return $tree.Count + 1
}

function Get-QaClient {
    Get-CimInstance Win32_Process -Filter "Name = 'java.exe' OR Name = 'javaw.exe'" |
        Where-Object { $_.CommandLine -and $_.CommandLine.Contains('-Dfocalis.qa.scenario=') } |
        Select-Object -First 1
}

function Get-QaWindow {
    $client = Get-QaClient
    if (-not $client) { return [IntPtr]::Zero }
    $process = Get-Process -Id $client.ProcessId -ErrorAction SilentlyContinue
    if (-not $process) { return [IntPtr]::Zero }
    return $process.MainWindowHandle
}

function Stop-QaClient {
    $client = Get-QaClient
    if ($client) { Stop-Process -Id $client.ProcessId -Force -ErrorAction SilentlyContinue }
}

# Uses the jstack that belongs to the client's own Java, so the dump matches the running VM.
function Save-ThreadDump([string]$File) {
    $client = Get-QaClient
    if (-not $client -or -not $client.ExecutablePath) { return $false }
    $jstack = Join-Path (Split-Path $client.ExecutablePath) 'jstack.exe'
    if (-not (Test-Path $jstack)) { return $false }
    try {
        $dump = & $jstack $client.ProcessId 2>&1
        Write-Utf8 $File (($dump | ForEach-Object { "$_" }) -join "`n")
        return $true
    } catch {
        return $false
    }
}

# Reads with delete sharing, so the client can keep replacing the file while it's open here.
function Read-Probe([string]$Path) {
    if (-not (Test-Path $Path)) { return $null }
    try {
        $stream = New-Object IO.FileStream($Path, [IO.FileMode]::Open, [IO.FileAccess]::Read,
            ([IO.FileShare]::ReadWrite -bor [IO.FileShare]::Delete))
        try {
            $reader = New-Object IO.StreamReader($stream)
            return ($reader.ReadToEnd() | ConvertFrom-Json)
        } finally {
            $stream.Dispose()
        }
    } catch {
        return $null
    }
}

function Initialize-GameDir([string]$Name) {
    $config = $Scenarios[$Name]
    foreach ($dir in @($GameDir, (Join-Path $GameDir 'config'), (Join-Path $GameDir 'shaderpacks'))) {
        New-Item -ItemType Directory -Force $dir | Out-Null
    }
    # A fresh world with a fixed seed every time, so runs are comparable.
    $world = Join-Path $GameDir 'saves\focalis-qa'
    if (Test-Path $world) { Remove-Item -Recurse -Force $world }
    # Only runObfClient puts the release jar here. A leftover copy would load Focalis twice in runClient.
    $mods = Join-Path $GameDir 'mods'
    if (Test-Path $mods) { Remove-Item -Recurse -Force $mods }

    Write-Utf8 (Join-Path $GameDir 'options.txt') (@(
            'soundCategory_master:0.0', 'pauseOnLostFocus:false', 'enableVsync:false', 'maxFps:120',
            'guiScale:2', 'renderDistance:8', 'tutorialStep:none', 'fboEnable:true', 'anaglyph3d:false',
            'mainHand:right', 'lang:en_us', 'fancyGraphics:true') -join "`n")

    $enabled = $(if ($config.Shaders) { 'true' } else { 'false' })
    $worldTargetEnabled = $(if ($WorldTarget -or ($config.ContainsKey('WorldTarget') -and $config.WorldTarget)) {
            'true' } else { 'false' })
    $worldProgramsEnabled = $(if ($WorldPrograms -or ($config.ContainsKey('WorldPrograms') -and $config.WorldPrograms)) {
            'true' } else { 'false' })
    $programsPack = $(if ($config.ContainsKey('ProgramsPack')) { $config.ProgramsPack } else { 'focalis-world-routes' })
    Write-Utf8 (Join-Path $GameDir 'config\focalis.cfg') @"
# Configuration file

~CONFIG_VERSION: 1

diagnostics {
    B:logGlExtensions=false
}

features {
    frame_stats {
        B:enabled=false
        I:reportIntervalSeconds=10
    }

    shaders {
        B:enabled=$enabled
        S:pack=$($config.Pack)
    }

    world_programs {
        B:enabled=$worldProgramsEnabled
        S:pack=$programsPack
    }

    world_target {
        B:enabled=$worldTargetEnabled
    }
}
"@

    $packs = Join-Path $GameDir 'shaderpacks'
    foreach ($pack in @('focalis-depth-view', 'focalis-broken')) {
        $target = Join-Path $packs $pack
        if (Test-Path $target) { Remove-Item -Recurse -Force $target }
        Copy-Item -Recurse $TestPack $target
    }
    # The broken pack is the test pack with one missing semicolon, which no driver accepts.
    $depth = Join-Path $packs 'focalis-broken\shaders\lib\depth.glsl'
    $text = [IO.File]::ReadAllText($depth)
    Write-Utf8 $depth ($text.Replace('/ 16.0);', '/ 16.0)'))
    foreach ($pack in @('focalis-world-routes', 'focalis-world-routes-broken')) {
        $target = Join-Path $packs $pack
        if (Test-Path $target) { Remove-Item -Recurse -Force $target }
        Copy-Item -Recurse $RoutesPack $target
    }
    # Every program of the broken copy shares one vertex shader, which loses a semicolon.
    $vertex = Join-Path $packs 'focalis-world-routes-broken\shaders\lib\vertex.glsl'
    $text = [IO.File]::ReadAllText($vertex)
    Write-Utf8 $vertex ($text.Replace('gl_Position = ftransform();', 'gl_Position = ftransform()'))
    $target = Join-Path $packs 'focalis-dimension-routes'
    if (Test-Path $target) { Remove-Item -Recurse -Force $target }
    Copy-Item -Recurse $DimensionsPack $target
    New-DimensionsZip (Join-Path $packs 'focalis-dimension-routes.zip')
    $target = Join-Path $packs 'focalis-world-samplers'
    if (Test-Path $target) { Remove-Item -Recurse -Force $target }
    Copy-Item -Recurse (Join-Path $RepoRoot 'src\test\resources\shaderpacks\focalis-world-samplers') $target
}

# The dimension pack as a ZIP, plus an entry for an empty world1 folder, which git can't keep in the folder pack.
function New-DimensionsZip([string]$Path) {
    Add-Type -AssemblyName System.IO.Compression, System.IO.Compression.FileSystem
    if (Test-Path $Path) { Remove-Item -Force $Path }
    $zip = [IO.Compression.ZipFile]::Open($Path, [IO.Compression.ZipArchiveMode]::Create)
    try {
        $root = (Resolve-Path $DimensionsPack).Path
        foreach ($file in Get-ChildItem -Recurse -File $root) {
            $name = $file.FullName.Substring($root.Length + 1).Replace('\', '/')
            $entry = $zip.CreateEntry($name)
            $stream = $entry.Open()
            try {
                $bytes = [IO.File]::ReadAllBytes($file.FullName)
                $stream.Write($bytes, 0, $bytes.Length)
            } finally {
                $stream.Dispose()
            }
        }
        $zip.CreateEntry('shaders/world1/') | Out-Null
    } finally {
        $zip.Dispose()
    }
}

function Test-LogLine([string]$Line, [string[]]$Allowed) {
    foreach ($allowed in $Allowed) {
        if ($Line.Contains($allowed)) { return $false }
    }
    return ($Line -cmatch '/(ERROR|WARN)\] \[Focalis') -or $Line.Contains('GL ERROR') -or ($Line -cmatch 'Exception')
}

function Invoke-Scenario([string]$Name) {
    $out = Join-Path $LatestDir $Name
    New-Item -ItemType Directory -Force $out | Out-Null
    Initialize-GameDir $Name
    $probeFile = Join-Path $out 'probe.json'
    $gradleLog = Join-Path $out 'gradle.log'
    $task = $(if ($Obfuscated) { 'runObfClient' } else { 'runClient' })
    $fullscreen = $(if ($AllowFullscreen) { 'true' } else { 'false' })
    $command = "cd /d `"$RepoRoot`" && .\gradlew.bat $task --console=plain `"-PqaScenario=$Name`" " +
        "`"-PqaGameDir=$GameDir`" `"-PqaOutputDir=$out`" -PqaFullscreen=$fullscreen > `"$gradleLog`" 2>&1"

    Write-Host "== $Name" -ForegroundColor Cyan
    $started = Get-Date
    $gradle = Start-Process cmd.exe -ArgumentList "/s /c `"$command`"" -WindowStyle Hidden -PassThru
    # Without holding the handle, ExitCode can come back empty once the process is gone.
    $null = $gradle.Handle
    $deadline = $started.AddSeconds($TimeoutSeconds)
    $handled = 0
    $timedOut = $false
    $stalled = $false
    $threadDump = $null
    $lastStep = ''
    $lastWrite = $null
    $lastWriteSeen = Get-Date
    $treeStopped = 0
    try {
        while (-not $gradle.HasExited) {
            if ((Get-Date) -gt $deadline) {
                $timedOut = $true
                Write-Warning "$Name timed out after $TimeoutSeconds seconds, stopping the client"
                Stop-QaClient
                break
            }
            $probe = Read-Probe $probeFile
            if (Test-Path $probeFile) {
                $stamp = (Get-Item $probeFile).LastWriteTimeUtc
                if ($stamp -ne $lastWrite) {
                    $lastWrite = $stamp
                    $lastWriteSeen = Get-Date
                }
            }
            if ($probe -and $probe.status -eq 'running' -and ((Get-Date) - $lastWriteSeen).TotalSeconds -gt $StallSeconds) {
                $stalled = $true
                Write-Warning "$Name stopped updating probe.json for $StallSeconds seconds at '$($probe.step)'"
                $dumpFile = Join-Path $out 'client-threads.txt'
                if (Save-ThreadDump $dumpFile) { $threadDump = "$Name/client-threads.txt" }
                Stop-QaClient
                break
            }
            if ($probe) {
                if ($probe.step -and $probe.step -ne $lastStep) {
                    $lastStep = $probe.step
                    Write-Host "   $lastStep"
                }
                if ($probe.request -and $probe.request.id -gt $handled) {
                    $handled = $probe.request.id
                    $window = Get-QaWindow
                    if ($window -ne [IntPtr]::Zero) {
                        [FocalisQaWindow]::ResizeClient($window, $probe.request.width, $probe.request.height)
                    }
                }
            }
            Start-Sleep -Milliseconds 500
        }
        $gradle.WaitForExit(60000) | Out-Null
    } finally {
        if ($timedOut -or $stalled -or -not $gradle.HasExited) {
            Stop-QaClient
            $gradle.WaitForExit(60000) | Out-Null
        }
        # Last resort. Only the cmd.exe started above and whatever runs under it are stopped.
        if (-not $gradle.HasExited) {
            Write-Warning "$Name`: Gradle still hasn't exited, stopping the processes this scenario started"
            if (-not $threadDump) {
                $dumpFile = Join-Path $out 'client-threads.txt'
                if (Save-ThreadDump $dumpFile) { $threadDump = "$Name/client-threads.txt" }
            }
            Stop-QaClient
            $treeStopped = Stop-ProcessTree $gradle.Id
            $gradle.WaitForExit(15000) | Out-Null
        }
    }
    $seconds = [Math]::Round(((Get-Date) - $started).TotalSeconds, 1)

    $clientLog = Join-Path $out 'client.log'
    $gameLog = Join-Path $GameDir 'logs\latest.log'
    if (Test-Path $gameLog) { Copy-Item $gameLog $clientLog -Force }

    $allowed = $AlwaysAllowed
    if ($ScenarioAllowed.Contains($Name)) { $allowed = $allowed + $ScenarioAllowed[$Name] }
    $logProblems = @()
    if (Test-Path $clientLog) {
        $logProblems = @(Get-Content $clientLog | Where-Object { Test-LogLine $_ $allowed } | Select-Object -First 20)
    }

    $probe = Read-Probe $probeFile
    $artifact = $null
    if ($Obfuscated) { $artifact = Get-ArtifactInfo }
    $checks = New-Object System.Collections.Generic.List[object]
    $checks.Add([ordered]@{ name = 'finished-in-time'; passed = -not $timedOut; detail = "$seconds s" })
    $checks.Add([ordered]@{ name = 'no-stall'; passed = -not $stalled
            detail = $(if ($stalled) { "client stopped responding, thread dump: $threadDump" } else { 'kept responding' }) })
    $checks.Add([ordered]@{ name = 'client-exited-cleanly'; passed = ($gradle.HasExited -and $gradle.ExitCode -eq 0)
            detail = "Gradle exit code $(if ($gradle.HasExited) { $gradle.ExitCode } else { 'none' })" })
    $checks.Add([ordered]@{ name = 'gradle-exited-by-itself'; passed = ($treeStopped -eq 0)
            detail = $(if ($treeStopped -gt 0) { "had to stop $treeStopped processes this scenario started" } else { 'no processes had to be stopped' }) })
    $checks.Add([ordered]@{ name = 'probe-report'; passed = ($null -ne $probe -and $probe.status -eq 'finished')
            detail = $(if ($probe) { "status $($probe.status)" } else { 'probe.json missing' }) })
    $checks.Add([ordered]@{ name = 'log-clean'; passed = ($logProblems.Count -eq 0)
            detail = "$($logProblems.Count) unexpected warning, error or exception lines" })

    $failedProbeChecks = @()
    $screenshots = @()
    if ($probe) {
        $failedProbeChecks = @($probe.checks | Where-Object { -not $_.passed } | ForEach-Object { "$($_.name): $($_.detail)" })
        $screenshots = @($probe.screenshots | ForEach-Object { "$Name/$($_.file)" })
    }
    $passed = ($null -ne $probe -and $probe.result -eq 'pass')
    foreach ($check in $checks) { $passed = $passed -and $check.passed }

    $result = [ordered]@{
        name              = $Name
        result            = $(if ($passed) { 'pass' } else { 'fail' })
        durationSeconds   = $seconds
        probeResult       = $(if ($probe) { $probe.result } else { $null })
        probe             = "$Name/probe.json"
        clientLog         = $(if (Test-Path $clientLog) { "$Name/client.log" } else { $null })
        gradleLog         = "$Name/gradle.log"
        threadDump        = $threadDump
        runnerChecks      = $checks
        failedProbeChecks = $failedProbeChecks
        unexpectedLogLines = $logProblems
        screenshots       = $screenshots
        numbers           = $(if ($probe) {
                $phases = $probe.worldPhases
                [ordered]@{
                    worldFrames      = $probe.frames.world
                    worldStarts      = $phases.starts
                    worldEnds        = $phases.ends
                    startEndPairs    = $phases.pairs
                    phaseProblems    = $phases.repeatedStarts + $phases.endsWithoutStart + $phases.passesOutsideEnd + $phases.repeatedPasses
                    stagePairs       = ($probe.renderStages.stages.PSObject.Properties | ForEach-Object { $_.Value.pairs } | Measure-Object -Sum).Sum
                    stageProblems    = $probe.renderStages.unmatchedStarts + $probe.renderStages.endsWithoutStart + $probe.renderStages.badNesting + $probe.renderStages.repeatedStarts + $probe.renderStages.outsideWorld + $probe.renderStages.outsideFrame + $probe.renderStages.handBeforeWorldEnd + $probe.renderStages.kindMismatches
                    renderedFrames   = $probe.postPass.renderedFrames
                    skips            = $probe.postPass.skips
                    captures         = @($probe.postPass.captures).Count
                    stateMismatches  = $probe.boundary.mismatchFrames
                    worldTargetPasses = $probe.worldTarget.redirectedPasses
                    programScopes    = $probe.worldPrograms.scopes
                    glErrorsInFocalis = $probe.glErrors.duringFocalis
                    averageFrameMs   = [Math]::Round($probe.frames.averageFrameMs, 2)
                    averageFocalisWorldEndMs = [Math]::Round($probe.frames.averageFocalisWorldEndMs, 3)
                }
            } else { $null })
    }
    if ($Obfuscated) { $result.artifact = $artifact }
    $color = $(if ($passed) { 'Green' } else { 'Red' })
    Write-Host "   $($result.result.ToUpper()) in $seconds s" -ForegroundColor $color
    foreach ($line in $failedProbeChecks) { Write-Host "   failed: $line" -ForegroundColor Red }
    foreach ($check in $checks) {
        if (-not $check.passed) { Write-Host "   failed: $($check.name): $($check.detail)" -ForegroundColor Red }
    }
    return $result
}

# Entry point

if ($List) {
    $Scenarios.Keys | ForEach-Object { Write-Host $_ }
    exit 0
}
if (-not $Scenario) {
    Write-Host 'Pass -Scenario <name>[,<name>] or -Scenario all. -List shows the names.'
    exit 2
}
$names = @()
foreach ($entry in $Scenario) {
    foreach ($name in ($entry -split ',')) {
        $name = $name.Trim()
        if ($name -eq 'all') { $names += $Scenarios.Keys } elseif ($name) { $names += $name }
    }
}
foreach ($name in $names) {
    if (-not $Scenarios.Contains($name)) {
        Write-Error "Unknown scenario '$name'. Known: $($Scenarios.Keys -join ', ')"
        exit 2
    }
}
$javaHomeOk = $env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))
if (-not $javaHomeOk -and -not (Get-Command java -ErrorAction SilentlyContinue)) {
    Write-Error 'The Gradle wrapper needs Java 17 or newer. Set JAVA_HOME or put java on PATH.'
    exit 2
}
if (Get-QaClient) {
    Write-Error 'A QA client is already running. Close it first.'
    exit 2
}

if (Test-Path $LatestDir) { Remove-Item -Recurse -Force $LatestDir }
New-Item -ItemType Directory -Force $LatestDir | Out-Null
$startedAt = (Get-Date).ToUniversalTime().ToString('o')
$source = Get-SourceInfo
Write-Host "Source: $(if ($source.head) { $source.head } else { 'unknown' }) on $(if ($source.branch) { $source.branch } else { 'no branch' }), dirty: $(if ($null -eq $source.dirty) { 'unknown' } else { $source.dirty })"
$results = @()
foreach ($name in $names) {
    $results += Invoke-Scenario $name
}
$allPassed = @($results | Where-Object { $_.result -ne 'pass' }).Count -eq 0

$report = [ordered]@{
    schema      = 1
    startedAt   = $startedAt
    finishedAt  = (Get-Date).ToUniversalTime().ToString('o')
    result      = $(if ($allPassed) { 'pass' } else { 'fail' })
    task        = $(if ($Obfuscated) { 'runObfClient' } else { 'runClient' })
    worldTarget = [bool]$WorldTarget
    worldPrograms = [bool]$WorldPrograms
    source      = $source
}
# Only runObfClient tests a release jar.
if ($Obfuscated) {
    $report.artifact = Get-RunArtifact $results
}
$report.host = [ordered]@{
    os         = [Environment]::OSVersion.VersionString
    powershell = $PSVersionTable.PSVersion.ToString()
}
$report.scenarios = $results
Write-Utf8 (Join-Path $LatestDir 'report.json') ($report | ConvertTo-Json -Depth 8)

$summary = New-Object System.Collections.Generic.List[string]
$summary.Add('# Focalis QA summary')
$summary.Add('')
$summary.Add("Result: **$($report.result)** ($($report.task), $($results.Count) scenarios, $startedAt)")
if ($WorldTarget) {
    $summary.Add('')
    $summary.Add('World target on in every scenario.')
}
if ($WorldPrograms) {
    $summary.Add('')
    $summary.Add('World programs on in every scenario.')
}
$summary.Add('')
$dirtyText = $(if ($null -eq $source.dirty) { 'unknown' } elseif ($source.dirty) { 'uncommitted changes' } else { 'clean' })
$summary.Add("Source: $(if ($source.head) { $source.head } else { 'unknown' }) on $(if ($source.branch) { $source.branch } else { 'no branch' }), $dirtyText")
if ($Obfuscated) {
    $summary.Add("Release jar SHA-256: $(if ($report.artifact) { $report.artifact.sha256 } else { 'unknown, a scenario found no jar or the jars differed' })")
}
$summary.Add('')
$summary.Add('| Scenario | Result | World passes | Start/end pairs | Phase problems | Stage pairs | Stage problems | Rendered | Redirected | Program scopes | Mismatches | GL errors | Captures | Seconds |')
$summary.Add('| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |')
foreach ($result in $results) {
    $n = $result.numbers
    if ($n) {
        $summary.Add("| $($result.name) | $($result.result) | $($n.worldFrames) | $($n.startEndPairs) | $($n.phaseProblems) | $($n.stagePairs) | $($n.stageProblems) | $($n.renderedFrames) | $($n.worldTargetPasses) | $($n.programScopes) | $($n.stateMismatches) | $($n.glErrorsInFocalis) | $($n.captures) | $($result.durationSeconds) |")
    } else {
        $summary.Add("| $($result.name) | $($result.result) | - | - | - | - | - | - | - | - | - | - | - | $($result.durationSeconds) |")
    }
}
foreach ($result in $results) {
    $problems = @($result.failedProbeChecks) + @($result.runnerChecks | Where-Object { -not $_.passed } | ForEach-Object { "$($_.name): $($_.detail)" }) + @($result.unexpectedLogLines)
    if ($problems.Count -gt 0) {
        $summary.Add('')
        $summary.Add("## $($result.name)")
        foreach ($problem in $problems) { $summary.Add("- $problem") }
    }
}
$summary.Add('')
$summary.Add('Details per scenario are in report.json and each scenario folder.')
Write-Utf8 (Join-Path $LatestDir 'summary.md') ($summary -join "`n")

Write-Host ''
Write-Host "Report: $(Get-RelativePath $RepoRoot (Join-Path $LatestDir 'report.json'))"
if ($allPassed) { exit 0 }
exit 1
