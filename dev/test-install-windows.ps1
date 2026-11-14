param(
    [Parameter(Mandatory = $true)][string]$Installer,
    [Parameter(Mandatory = $true)][string]$Artifacts,
    [Parameter(Mandatory = $true)][string]$Root,
    # CI only: a hosted runner signs in as an administrator. The default is to require an ordinary user.
    [switch]$AllowAdministrator
)
$ErrorActionPreference = 'Stop'
$originalPath = [Environment]::GetEnvironmentVariable('Path', 'User')
$machinePath = [Environment]::GetEnvironmentVariable('Path', 'Machine')
$policy = Get-ExecutionPolicy -List | Out-String
$originalVersion = $env:LVH_YARD_VERSION
$originalDir = $env:LVH_YARD_INSTALL_DIR
$originalLocal = $env:LOCALAPPDATA
$script:mode = 'good'
$script:version = '9.8.7'
$script:urls = @()
function Assert($condition, $message) {
    if (-not $condition) { throw $message }
}
# Only the network boundary is controlled. Files, hashes, registry and replacement are native.
function Invoke-WebRequest {
    param($Uri, $OutFile, [switch]$UseBasicParsing)
    $script:urls += $Uri
    if ($script:mode -eq 'manifest-download') { throw 'Controlled manifest download failure' }
    if ($Uri.EndsWith('/SHA256SUMS')) {
        $manifest = Join-Path (Join-Path $Artifacts $script:version) 'SHA256SUMS'
        if ($script:mode -eq 'checksum') {
            $lines = Get-Content -LiteralPath $manifest | ForEach-Object {
                if ($_ -match '  lvh-yard-.+-windows-amd64\.zip$') {
                    ('0' * 64) + $_.Substring(64)
                } else { $_ }
            }
            [IO.File]::WriteAllLines($OutFile, [string[]]$lines)
        } else {
            Copy-Item -LiteralPath $manifest -Destination $OutFile
        }
    } else {
        if ($script:mode -eq 'archive-download') { throw 'Controlled archive download failure' }
        Copy-Item -LiteralPath (Join-Path (Join-Path $Artifacts $script:version) ([IO.Path]::GetFileName($Uri))) -Destination $OutFile
    }
}
function Install {
    Invoke-Expression ([IO.File]::ReadAllText($Installer))
}
function Version($exe, $expected) {
    Assert ((& $exe --version) -eq "lvh-yard $expected") "Wrong version at $exe"
}
try {
    Assert (-not (Test-Path -LiteralPath $Root)) 'Use a new acceptance root'
    [IO.Directory]::CreateDirectory($Root) | Out-Null
    $identity = [Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()
    $administrator = $identity.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
    Assert ($AllowAdministrator -or -not $administrator) 'Acceptance requires an ordinary user'
    $rights = if ($administrator) { 'ADMINISTRATOR: this run does not show ordinary-user behavior' } else { 'ordinary user' }
    Write-Output "Shell $($PSVersionTable.PSVersion); account $env:USERNAME; $rights"
    $env:LOCALAPPDATA = Join-Path $Root 'Local AppData'
    $env:LVH_YARD_INSTALL_DIR = $null
    $env:LVH_YARD_VERSION = $null
    Install
    $default = Join-Path $env:LOCALAPPDATA 'lvh-yard\bin\lvh-yard.exe'
    Version $default '9.8.7'
    Assert ($script:urls[0] -eq 'https://github.com/ppalazon/lvh-yard/releases/latest/download/SHA256SUMS') 'Wrong latest URL'
    Write-Output 'PASS default LocalAppData/bin and latest release'

    $archive = Join-Path (Join-Path $Artifacts '9.8.7') 'lvh-yard-9.8.7-windows-amd64.zip'
    $line = Get-Content -LiteralPath (Join-Path (Join-Path $Artifacts '9.8.7') 'SHA256SUMS') |
        Where-Object { $_ -match '  lvh-yard-9\.8\.7-windows-amd64\.zip$' }
    Assert ((Get-FileHash -Algorithm SHA256 -LiteralPath $archive).Hash -ieq ($line -split '  ', 2)[0]) 'Manual ZIP checksum failed'
    $manual = Join-Path $Root 'manual'
    Expand-Archive -LiteralPath $archive -DestinationPath $manual
    Version (Join-Path $manual 'lvh-yard.exe') '9.8.7'
    Assert ((Get-Content -LiteralPath (Join-Path $manual 'LICENSE') -Raw) -match 'Apache License') 'ZIP license is missing'
    Write-Output 'PASS manual ZIP checksum, extraction, executable and LICENSE'
    Remove-Item -LiteralPath $manual -Recurse -Force
    Remove-Item -LiteralPath $default -Force

    $env:LVH_YARD_INSTALL_DIR = Join-Path $Root "O'Brien space caf$([char]0xE9)\bin"
    $env:LVH_YARD_VERSION = '9.8.7'
    Install
    $exe = Join-Path $env:LVH_YARD_INSTALL_DIR 'lvh-yard.exe'
    Version $exe '9.8.7'
    Assert ($script:urls[-2] -eq 'https://github.com/ppalazon/lvh-yard/releases/download/v9.8.7/SHA256SUMS') 'Wrong unprefixed version URL'
    $userPath = [Environment]::GetEnvironmentVariable('Path', 'User')
    Install
    Assert ([Environment]::GetEnvironmentVariable('Path', 'User') -ceq $userPath) 'Reinstall duplicated PATH'
    [Environment]::SetEnvironmentVariable('Path', "$userPath;$($env:LVH_YARD_INSTALL_DIR.ToUpperInvariant())\", 'User')
    Install
    Assert ([Environment]::GetEnvironmentVariable('Path', 'User') -ceq $userPath) 'Case-insensitive PATH deduplication failed'
    Write-Output 'PASS spaces, apostrophe, Unicode, reinstall and case-insensitive user PATH'

    $script:version = '9.8.8'
    $env:LVH_YARD_VERSION = 'v9.8.8'
    Install
    Version $exe '9.8.8'
    Assert ($script:urls[-2] -eq 'https://github.com/ppalazon/lvh-yard/releases/download/v9.8.8/SHA256SUMS') 'Wrong prefixed version URL'
    Write-Output 'PASS upgrade and v-prefixed version'
    $before = (Get-FileHash -LiteralPath $exe).Hash
    foreach ($failure in @('manifest-download', 'archive-download', 'checksum', 'locked', 'replacement')) {
        $script:mode = $failure
        $lockProcess = $null
        $ready = Join-Path $Root 'lock-ready'
        $pathBeforeFailure = [Environment]::GetEnvironmentVariable('Path', 'User')
        $directoryDestination = $false
        try {
            if ($failure -eq 'locked') {
                $lockScript = @"
`$ErrorActionPreference = 'Stop'
`$handle = [IO.File]::Open('$($exe.Replace("'", "''"))', 'Open', 'Read', 'Read')
try {
    [IO.File]::WriteAllText('$($ready.Replace("'", "''"))', 'ready')
    while (`$true) { Start-Sleep -Seconds 1 }
} finally { `$handle.Dispose() }
"@
                $encoded = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($lockScript))
                $lockProcess = Start-Process -FilePath (Get-Process -Id $PID).Path -ArgumentList @('-NoLogo', '-NoProfile', '-NonInteractive', '-EncodedCommand', $encoded) -PassThru
                $deadline = [DateTime]::UtcNow.AddSeconds(15)
                while (-not (Test-Path -LiteralPath $ready)) {
                    if ($lockProcess.HasExited -or [DateTime]::UtcNow -gt $deadline) { throw 'Lock process did not become ready' }
                    Start-Sleep -Milliseconds 100
                }
            }
            if ($failure -eq 'replacement') {
                # A real directory collision must not be deleted to make room for the executable.
                Move-Item -LiteralPath $exe -Destination "$exe.saved"
                [IO.Directory]::CreateDirectory($exe) | Out-Null
                [IO.File]::WriteAllText((Join-Path $exe 'owned.txt'), 'preserve')
                $directoryDestination = $true
            }
            $failed = $false
            try { Install } catch { $failed = $true; Write-Output "Expected $failure failure: $($_.Exception.Message)" }
            Assert $failed "Installer accepted $failure"
            if ($directoryDestination) {
                Assert ([IO.File]::ReadAllText((Join-Path $exe 'owned.txt')) -eq 'preserve') 'Replacement removed destination directory'
            } else {
                Assert ((Get-FileHash -LiteralPath $exe).Hash -eq $before) "$failure changed installed executable"
            }
            Assert ([Environment]::GetEnvironmentVariable('Path', 'User') -ceq $pathBeforeFailure) "$failure changed user PATH"
            Assert (@(Get-ChildItem -LiteralPath $env:LVH_YARD_INSTALL_DIR -Directory -Filter '.lvh-yard-*').Count -eq 0) "$failure left staging files"
        } finally {
            if ($null -ne $lockProcess) {
                Stop-Process -Id $lockProcess.Id -Force
                $lockProcess.WaitForExit()
                Remove-Item -LiteralPath $ready -Force
            }
            if ($directoryDestination) {
                Remove-Item -LiteralPath $exe -Recurse -Force
                Move-Item -LiteralPath "$exe.saved" -Destination $exe
            }
        }
        Version $exe '9.8.8'
        Write-Output "PASS $failure preserves working executable"
    }
    $script:mode = 'good'
    $shell = (Get-Process -Id $PID).Path
    $fresh = Join-Path $Root 'fresh.ps1'
    [IO.File]::WriteAllText($fresh, @'
$ErrorActionPreference = 'Stop'
$env:Path = [Environment]::GetEnvironmentVariable('Path', 'Machine') + ';' + [Environment]::GetEnvironmentVariable('Path', 'User')
$actual = (Get-Command lvh-yard.exe).Source
if ($actual -cne (Join-Path $env:LVH_YARD_INSTALL_DIR 'lvh-yard.exe')) { throw "Wrong executable: $actual" }
if ((lvh-yard.exe --version) -ne 'lvh-yard 9.8.8') { throw 'Wrong fresh-shell version' }
Write-Output "PASS fresh shell resolves $actual : $(lvh-yard.exe --version)"
'@)
    # Rebuild the environment from the registry because the SSH service predates the PATH change.
    $encoded = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes([IO.File]::ReadAllText($fresh)))
    & $shell -NoLogo -NoProfile -NonInteractive -EncodedCommand $encoded
    Assert ($LASTEXITCODE -eq 0) 'Fresh shell failed'
    Assert ([Environment]::GetEnvironmentVariable('Path', 'Machine') -ceq $machinePath) 'Machine PATH changed'
    Assert ((Get-ExecutionPolicy -List | Out-String) -ceq $policy) 'Execution policy changed'
    Write-Output 'PASS machine PATH and execution policy unchanged'
} finally {
    [Environment]::SetEnvironmentVariable('Path', $originalPath, 'User')
    $env:LVH_YARD_VERSION = $originalVersion
    $env:LVH_YARD_INSTALL_DIR = $originalDir
    $env:LOCALAPPDATA = $originalLocal
    if (Test-Path -LiteralPath $Root) { Remove-Item -LiteralPath $Root -Recurse -Force }
    Assert ([Environment]::GetEnvironmentVariable('Path', 'User') -ceq $originalPath) 'User PATH restoration failed'
    Write-Output 'Restored original user PATH and removed owned acceptance files'
}
