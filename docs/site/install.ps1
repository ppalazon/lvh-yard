# Installs lvh-yard from a GitHub release. Requires Windows PowerShell 5.1 or PowerShell 7.
# LVH_YARD_VERSION selects a release with or without v. The default is the latest release.
# LVH_YARD_INSTALL_DIR selects the bin folder. The default is LocalAppData\lvh-yard\bin.
function Install-LvhYard {
    $ErrorActionPreference = 'Stop'
    $base = 'https://github.com/ppalazon/lvh-yard/releases'
    if ($env:LVH_YARD_VERSION) {
        $version = $env:LVH_YARD_VERSION -replace '^v', ''
        $base += "/download/v$version"
    } else {
        $base += '/latest/download'
    }
    $dir = $env:LVH_YARD_INSTALL_DIR
    if (-not $dir) { $dir = Join-Path $env:LOCALAPPDATA 'lvh-yard\bin' }
    $dir = [IO.Path]::GetFullPath($dir)
    $temp = Join-Path ([IO.Path]::GetTempPath()) ([Guid]::NewGuid().ToString('N'))
    $stage = $null
    try {
        [IO.Directory]::CreateDirectory($temp) | Out-Null
        $manifest = Join-Path $temp 'SHA256SUMS'
        Invoke-WebRequest -UseBasicParsing -Uri "$base/SHA256SUMS" -OutFile $manifest
        $matches = @(Get-Content -LiteralPath $manifest | Where-Object {
            $_ -cmatch '^[a-fA-F0-9]{64}  lvh-yard-[^/\\\s]+-windows-amd64\.zip$'
        })
        if ($matches.Count -ne 1) { throw 'The release must name one Windows x64 ZIP in SHA256SUMS.' }
        $checksum, $archive = $matches[0] -split '  ', 2
        $download = Join-Path $temp $archive
        Invoke-WebRequest -UseBasicParsing -Uri "$base/$archive" -OutFile $download
        if ((Get-FileHash -Algorithm SHA256 -LiteralPath $download).Hash -ine $checksum) {
            throw "The checksum of $archive does not match. The installed executable was not changed."
        }

        [IO.Directory]::CreateDirectory($dir) | Out-Null
        # Staging beside the destination keeps replacement on the same volume.
        $stage = Join-Path $dir ('.lvh-yard-' + [Guid]::NewGuid().ToString('N'))
        [IO.Directory]::CreateDirectory($stage) | Out-Null
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        $zip = [IO.Compression.ZipFile]::OpenRead($download)
        try {
            foreach ($name in @('lvh-yard.exe', 'LICENSE')) {
                $entries = @($zip.Entries | Where-Object { $_.FullName -ceq $name })
                if ($entries.Count -ne 1) { throw "The archive must contain one $name." }
                [IO.Compression.ZipFileExtensions]::ExtractToFile($entries[0], (Join-Path $stage $name))
            }
        } finally { $zip.Dispose() }
        $source = Join-Path $stage 'lvh-yard.exe'
        $destination = Join-Path $dir 'lvh-yard.exe'
        if ([IO.File]::Exists($destination)) {
            # PowerShell 5.1 converts a null backup name to an invalid empty path.
            [IO.File]::Replace($source, $destination, (Join-Path $stage 'previous.exe'))
        } else {
            [IO.File]::Move($source, $destination)
        }

        # Use the raw registry value. [Environment]::GetEnvironmentVariable expands %VAR% entries into literals, and
        # SetEnvironmentVariable writes the result as REG_SZ. The value keeps its text and kind.
        $environment = [Microsoft.Win32.Registry]::CurrentUser.CreateSubKey('Environment')
        try {
            $userPath = $environment.GetValue('Path', $null, [Microsoft.Win32.RegistryValueOptions]::DoNotExpandEnvironmentNames)
            $kind = [Microsoft.Win32.RegistryValueKind]::ExpandString
            if ($null -ne $userPath) { $kind = $environment.GetValueKind('Path') }
        } finally { $environment.Dispose() }
        $parts = @()
        $found = $false
        foreach ($part in ($userPath -split ';')) {
            $expanded = [Environment]::ExpandEnvironmentVariables($part.Trim().Trim('"')).TrimEnd('\', '/')
            if ($expanded -ieq $dir.TrimEnd('\', '/')) {
                if ($found) { continue }
                $found = $true
            }
            $parts += $part
        }
        if (-not $found) {
            if (-not $userPath) { $parts = @($dir) } else { $parts += $dir }
        }
        $newPath = $parts -join ';'
        if ($newPath -cne $userPath) {
            $environment = [Microsoft.Win32.Registry]::CurrentUser.CreateSubKey('Environment')
            try { $environment.SetValue('Path', $newPath, $kind) } finally { $environment.Dispose() }
            if (-not ('LvhYard.EnvironmentNotification' -as [type])) {
                Add-Type @'
using System;
using System.Runtime.InteropServices;
namespace LvhYard {
    public static class EnvironmentNotification {
        [DllImport("user32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        public static extern IntPtr SendMessageTimeout(IntPtr window, uint message,
            UIntPtr wParam, string lParam, uint flags, uint timeout, out UIntPtr result);
    }
}
'@
            }
            $result = [UIntPtr]::Zero
            [LvhYard.EnvironmentNotification]::SendMessageTimeout([IntPtr]0xffff, 0x1a,
                [UIntPtr]::Zero, 'Environment', 2, 5000, [ref]$result) | Out-Null
        }
        Write-Output "Installed $destination"
        Write-Output 'Open a new terminal, then run: lvh-yard --version'
        Write-Output 'Set up this Workstation: lvh-yard ws init'
    } finally {
        if ($stage -and [IO.Directory]::Exists($stage)) { [IO.Directory]::Delete($stage, $true) }
        if ([IO.Directory]::Exists($temp)) { [IO.Directory]::Delete($temp, $true) }
    }
}

# Read the whole script before installation starts, so a cut download runs nothing.
Install-LvhYard
