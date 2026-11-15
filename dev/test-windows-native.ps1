# Runs the packaged Windows executable and the PowerShell installer in the shell that runs this script.
# The Linux job of CI builds the Package folder. It holds the integration test binary, the unit test binaries
# with the files that they read, and the packaged archives of versions 9.8.7 and 9.8.8, each folder with its
# SHA256SUMS. See "Continuous integration" in docs/development.md.
#   powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File dev\test-windows-native.ps1 -Package <dir> -Installer docs\site\install.ps1 -PowerShellMajor 5
param(
    [Parameter(Mandatory = $true)][string]$Package,
    [Parameter(Mandatory = $true)][string]$Installer,
    [Parameter(Mandatory = $true)][ValidateSet(5, 7)][int]$PowerShellMajor,
    # A hosted CI runner signs in as an administrator. Without this switch, the script needs an ordinary user.
    [switch]$AllowAdministrator
)
$ErrorActionPreference = 'Stop'
# Progress output slows Expand-Archive in Windows PowerShell and fills the log.
$ProgressPreference = 'SilentlyContinue'
$Package = (Resolve-Path -LiteralPath $Package).ProviderPath
$Installer = (Resolve-Path -LiteralPath $Installer).ProviderPath
$acceptance = Join-Path $PSScriptRoot 'test-install-windows.ps1'
function Assert($condition, $message) {
    if (-not $condition) { throw $message }
}
Assert ($PSVersionTable.PSVersion.Major -eq $PowerShellMajor) "Expected PowerShell $PowerShellMajor but this is $($PSVersionTable.PSVersion)"
$identity = [Security.Principal.WindowsIdentity]::GetCurrent()
$administrator = ([Security.Principal.WindowsPrincipal]$identity).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
Assert ($AllowAdministrator -or -not $administrator) 'This account is an administrator. Run as an ordinary user, or pass -AllowAdministrator and accept that the run does not show ordinary-user behavior.'

# The runner image puts Git's Unix tools on the PATH. They must not decide whether the executable and the
# installer pass, so this process drops those folders and checks that no such tool remains. A folder of
# the Windows directory stays; the check below reports what it holds.
$unixTools = 'sh', 'bash', 'id', 'vi', 'vim', 'ls', 'cat', 'sed', 'awk', 'grep', 'uname', 'zip', 'unzip', 'sha256sum'
$windows = [Environment]::GetFolderPath('Windows')
$kept = @()
foreach ($folder in ($env:Path -split ';')) {
    if (-not $folder) { continue }
    $unix = $false
    if (-not $folder.StartsWith($windows, [StringComparison]::OrdinalIgnoreCase)) {
        foreach ($tool in $unixTools) {
            try { if ([IO.File]::Exists([IO.Path]::Combine($folder.Trim('"'), "$tool.exe"))) { $unix = $true } } catch { }
        }
    }
    if ($unix) { Write-Output "Dropped from PATH: $folder" } else { $kept += $folder }
}
$env:Path = $kept -join ';'
foreach ($tool in $unixTools) {
    $found = Get-Command $tool -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
    # The WSL launcher in the Windows directory is part of Windows; it is not a Unix tool of the runner.
    if ($found -and $tool -eq 'bash' -and $found.Source.StartsWith($windows, [StringComparison]::OrdinalIgnoreCase)) {
        Write-Output "bash.exe is the Windows launcher: $($found.Source)"
        continue
    }
    Assert (-not $found) "$tool is on the PATH: $($found.Source)"
}
foreach ($shell in 'powershell.exe', 'pwsh.exe') {
    $found = Get-Command $shell -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
    Assert $found "$shell is not on the PATH"
    Write-Output "$shell is $($found.Source)"
}
$rights = if ($administrator) { 'ADMINISTRATOR: this run does not show ordinary-user behavior' } else { 'ordinary user' }
Write-Output "Shell $($PSVersionTable.PSVersion); account $($identity.Name); $rights; no Unix tool on the PATH"
Write-Output (Get-ExecutionPolicy -List | Out-String)

$base = Join-Path ([IO.Path]::GetTempPath()) "lvh-yard-native-$([Guid]::NewGuid().ToString('N'))"
try {
    [IO.Directory]::CreateDirectory($base) | Out-Null
    # The executable under test is the one in the packaged ZIP, not a fresh build.
    $zip = Join-Path $Package '9.8.7\lvh-yard-9.8.7-windows-amd64.zip'
    $extracted = Join-Path $base 'zip'
    Expand-Archive -LiteralPath $zip -DestinationPath $extracted
    $executable = Join-Path $extracted 'lvh-yard.exe'
    $reported = & $executable --version 2>&1 | Out-String
    Assert ($reported.Trim() -eq 'lvh-yard 9.8.7') "The packaged executable reported: $reported"

    $env:YARD_TEST_EXECUTABLE = $executable
    $output = & (Join-Path $Package 'lvh-yard-integration.test.exe') '-test.v' '-test.count=1' 2>&1 | ForEach-Object { "$_" }
    $code = $LASTEXITCODE
    $output | ForEach-Object { Write-Output $_ }
    Assert ($code -eq 0) "The integration tests failed with exit code $code"
    $env:YARD_TEST_EXECUTABLE = $null
    # A test that cannot find a shell logs it and skips that shell, so check that each ran.
    foreach ($name in 'TestPowerShellCompletesCommandsAndValues', 'TestPrintedCommandsKeepTheirValuesInTheShell') {
        foreach ($shell in 'powershell.exe', 'pwsh.exe') {
            Assert (@($output | Where-Object { $_ -match "--- PASS: $name/$([regex]::Escape($shell))_" }).Count -gt 0) "$name did not pass in $shell"
        }
    }
    # The second-account test needs an account that a hosted runner does not have, so it may skip there. The unit test
    # TestWindowsProtectionDeniesAnotherUserByEffectiveAccess checks the same rule everywhere. With YARD_TEST_OTHER_USER
    # and YARD_TEST_OTHER_PASSWORD set, as on a test computer, the account test must pass.
    $accountTest = 'TestBuiltExecutableProtectsTheWorkstationFolderFromAnotherUser'
    if ($env:YARD_TEST_OTHER_USER) {
        Assert (@($output | Where-Object { $_ -match "--- PASS: $accountTest " }).Count -eq 1) "$accountTest did not pass"
    }
    Assert (@($output | Where-Object { $_ -match '--- SKIP' -and $_ -notmatch $accountTest }).Count -eq 0) 'An integration test was skipped'
    Write-Output 'PASS packaged executable: integration tests, including both PowerShells'

    # The unit tests run natively too. Windows-only tests never run on Linux, and the shared tests take their Windows
    # branches here. Each binary runs in the folder of its package, as `go test` does, so the files that a test
    # reads by a relative path (templates, config/) are the ones that CI packaged beside the binaries. A package
    # without such files has no folder in the artifact, so the script creates it.
    $unit = Join-Path $Package 'unit'
    $unitOutput = @()
    $binaries = @(Get-ChildItem -LiteralPath (Join-Path $unit 'bin') -Filter '*.test.exe' | Sort-Object Name)
    Assert ($binaries.Count -gt 0) 'The package holds no unit test binary'
    foreach ($binary in $binaries) {
        $name = $binary.Name -replace '\.test\.exe$', ''
        $where = Join-Path $unit "cli\internal\$name"
        [IO.Directory]::CreateDirectory($where) | Out-Null
        Push-Location -LiteralPath $where
        try {
            $lines = & $binary.FullName '-test.v' '-test.count=1' 2>&1 | ForEach-Object { "$_" }
            $code = $LASTEXITCODE
        } finally { Pop-Location }
        $lines | ForEach-Object { Write-Output $_ }
        Assert ($code -eq 0) "The unit tests of $name failed with exit code $code"
        $unitOutput += $lines
    }
    # A build constraint that drops a Windows-only test would hide it, so check that each ran and passed.
    foreach ($name in 'TestWindowsWorkstationUsesLocalAppData', 'TestWindowsWorkstationRejectsJunctionEscape', 'TestWindowsLANAddressIsOnAnAdapterWithAGateway', 'TestWindowsProtectionDeniesAnotherUserByEffectiveAccess') {
        Assert (@($unitOutput | Where-Object { $_ -match "^--- PASS: $name " }).Count -eq 1) "$name did not pass"
    }
    Write-Output "PASS unit tests: $(@($unitOutput | Where-Object { $_ -match '^--- PASS: ' }).Count) passed, $(@($unitOutput | Where-Object { $_ -match '^--- SKIP: ' }).Count) skipped"

    & $acceptance -Installer $Installer -Artifacts $Package -Root (Join-Path $base 'acceptance') -AllowAdministrator:$AllowAdministrator
    Write-Output 'PASS installer acceptance'
} finally {
    $env:YARD_TEST_EXECUTABLE = $null
    if (Test-Path -LiteralPath $base) { Remove-Item -LiteralPath $base -Recurse -Force }
}
