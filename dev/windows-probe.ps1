# Prints the state of a Windows test computer in one call. It changes nothing.
# Run it as the account that you test with, in Windows PowerShell 5.1 or 7. See docs/windows-test-vm.md.
#   win-ps dev/windows-probe.ps1
$ErrorActionPreference = 'Continue'
$ProgressPreference = 'SilentlyContinue'

# Each section stands alone, so a failure in one does not hide the others.
function Section($title, [scriptblock]$body) {
    Write-Output "--- $title"
    try { & $body 2>&1 | Out-String -Stream | Where-Object { $_ -ne '' } } catch { Write-Output "failed: $($_.Exception.Message)" }
}

# CIM (Get-CimInstance, Get-NetIPAddress, Get-NetFirewallProfile) answers "Access denied" to an ordinary user in an
# SSH session. This probe uses .NET and the netsh and netstat tools, which an ordinary user can run.
Section 'account' {
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
    $administrator = ([Security.Principal.WindowsPrincipal]$identity).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
    "$($identity.Name); administrator: $administrator; PowerShell $($PSVersionTable.PSVersion)"
    $version = Get-ItemProperty 'HKLM:\SOFTWARE\Microsoft\Windows NT\CurrentVersion'
    # ProductName says Windows 10 on Windows 11. A build number of 22000 or more is Windows 11.
    "Windows build $($version.CurrentBuildNumber)"
}

Section 'addresses (IPv4)' {
    foreach ($adapter in [Net.NetworkInformation.NetworkInterface]::GetAllNetworkInterfaces()) {
        foreach ($address in $adapter.GetIPProperties().UnicastAddresses) {
            if ($address.Address.AddressFamily -eq 'InterNetwork') { "$($adapter.Name) $($address.Address)/$($address.PrefixLength)" }
        }
    }
}

Section 'firewall' {
    netsh.exe advfirewall show allprofiles firewallpolicy
}

Section 'docker' {
    $docker = Get-Command docker.exe -ErrorAction SilentlyContinue
    if (-not $docker) { 'docker.exe is not on the PATH'; return }
    "context: $(docker.exe context show)"
    docker.exe info --format 'engine {{.ServerVersion}}; CPUs {{.NCPU}}; memory {{.MemTotal}} bytes' 2>&1
    'containers:'
    docker.exe ps -a --format '  {{.Names}} {{.Status}}'
    'images:'
    docker.exe images --format '  {{.Repository}}:{{.Tag}}'
}

Section 'docker desktop settings' {
    $file = Join-Path $env:APPDATA 'Docker\settings-store.json'
    if (-not (Test-Path $file)) { "no $file"; return }
    (Get-Content $file -Raw | ConvertFrom-Json).PSObject.Properties | ForEach-Object { "$($_.Name)=$($_.Value)" }
    'Enhanced Container Isolation is off when no key names it.'
}

Section 'wsl' {
    $file = Join-Path $env:USERPROFILE '.wslconfig'
    if (Test-Path $file) { Get-Content $file } else { 'no .wslconfig' }
    # wsl.exe writes UTF-16. Read it as such, and put the encoding back.
    $before = [Console]::OutputEncoding
    [Console]::OutputEncoding = [Text.Encoding]::Unicode
    try { wsl.exe -l -v } finally { [Console]::OutputEncoding = $before }
}

Section 'listening TCP ports from 1024 up' {
    netstat.exe -ano -p TCP | ForEach-Object {
        if ($_ -match '^\s*TCP\s+(\S+):(\d+)\s+\S+\s+LISTENING\s+(\d+)') {
            if ([int]$Matches[2] -ge 1024) { [pscustomobject]@{ Port = [int]$Matches[2]; Line = "$($Matches[1]):$($Matches[2]) $((Get-Process -Id $Matches[3] -ErrorAction SilentlyContinue).ProcessName)" } }
        }
    } | Sort-Object Port | ForEach-Object { $_.Line }
}

Section 'lvh-yard' {
    $yard = Get-Command lvh-yard.exe -ErrorAction SilentlyContinue
    if ($yard) { "$($yard.Source)"; & $yard.Source --version } else { 'lvh-yard.exe is not on the PATH' }
    $dev = Join-Path $env:USERPROFILE 'lvh-dev'
    if (Test-Path $dev) { "folders in ${dev}:"; Get-ChildItem $dev | ForEach-Object { "  $($_.Name)" } }
}
