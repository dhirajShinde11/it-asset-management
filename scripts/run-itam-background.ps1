# ==============================================================================
# BMC AssetHub - Automated Background Launcher
# ==============================================================================

[CmdletBinding()]
param(
    [switch]$ForceRestart = $false
)

$ErrorActionPreference = "SilentlyContinue"

# Determine project root directory
$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Set-Location $ProjectRoot

# Ensure logs directory exists
$LogsDir = Join-Path $ProjectRoot "logs"
if (-not (Test-Path $LogsDir)) {
    New-Item -ItemType Directory -Path $LogsDir | Out-Null
}

$AppLog = Join-Path $LogsDir "itam.log"
$TunnelLog = Join-Path $LogsDir "tunnel.log"
$UrlFile = Join-Path $LogsDir "public_url.txt"
$ServerPidFile = Join-Path $LogsDir "server.pid"
$TunnelPidFile = Join-Path $LogsDir "tunnel.pid"

# ------------------------------------------------------------------------------
# 1. Ensure MySQL Database Service is Running
# ------------------------------------------------------------------------------
try {
    $mysqlService = Get-Service -Name mysql -ErrorAction SilentlyContinue
    if ($mysqlService -and $mysqlService.Status -ne 'Running') {
        Start-Service -Name mysql -ErrorAction SilentlyContinue
        Start-Sleep -Seconds 2
    }
} catch {
    # Non-critical if user runs custom mysql
}

# ------------------------------------------------------------------------------
# 2. Check & Stop existing processes if ForceRestart requested
# ------------------------------------------------------------------------------
if ($ForceRestart) {
    # Terminate process on port 8085
    $activeConns = Get-NetTCPConnection -LocalPort 8085 -State Listen -ErrorAction SilentlyContinue
    foreach ($conn in $activeConns) {
        Stop-Process -Id $conn.OwningProcess -Force -ErrorAction SilentlyContinue
    }
    # Terminate cloudflared
    Get-Process -Name cloudflared -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 1
}

# ------------------------------------------------------------------------------
# 3. Start Spring Boot Backend (if not already running)
# ------------------------------------------------------------------------------
$isPortListening = Get-NetTCPConnection -LocalPort 8085 -State Listen -ErrorAction SilentlyContinue

if (-not $isPortListening) {
    $JarPath = Join-Path $ProjectRoot "target\it-asset-management-1.0.0.jar"
    
    # If JAR doesn't exist, package it
    if (-not (Test-Path $JarPath)) {
        & "$ProjectRoot\mvnw.cmd" package -DskipTests
    }
    
    # Launch windowless Java (javaw.exe) in background with internal Spring Boot logfile
    $javaProc = Start-Process -FilePath "javaw.exe" `
        -ArgumentList "-Dfile.encoding=UTF-8", "-jar", "`"$JarPath`"", "--logging.file.name=`"$AppLog`"" `
        -WorkingDirectory $ProjectRoot `
        -WindowStyle Hidden `
        -PassThru
        
    if ($javaProc) {
        $javaProc.Id | Out-File -FilePath $ServerPidFile -Encoding utf8
    }

    # Wait up to 35 seconds for port 8085 to become active
    $maxWait = 35
    $waited = 0
    while ($waited -lt $maxWait) {
        Start-Sleep -Seconds 1
        $waited++
        $ready = Get-NetTCPConnection -LocalPort 8085 -State Listen -ErrorAction SilentlyContinue
        if ($ready) {
            break
        }
    }
}

# ------------------------------------------------------------------------------
# 4. Start Cloudflare Quick Tunnel (if not already running)
# ------------------------------------------------------------------------------
$Cloudflared = Join-Path $ProjectRoot "tools\cloudflared.exe"
$tunnelProc = Get-Process -Name cloudflared -ErrorAction SilentlyContinue

if ((Test-Path $Cloudflared) -and (-not $tunnelProc)) {
    # Clear previous tunnel log
    if (Test-Path $TunnelLog) {
        Remove-Item -Path $TunnelLog -Force -ErrorAction SilentlyContinue
    }

    # Launch cloudflared tunnel in background
    $tunnel = Start-Process -FilePath $Cloudflared `
        -ArgumentList "tunnel", "--url", "http://localhost:8085", "--logfile", "`"$TunnelLog`"" `
        -WorkingDirectory $ProjectRoot `
        -WindowStyle Hidden `
        -PassThru
        
    if ($tunnel) {
        $tunnel.Id | Out-File -FilePath $TunnelPidFile -Encoding utf8
    }

    # Poll tunnel log to capture the public https://*.trycloudflare.com URL
    $foundUrl = $null
    $maxTunnelWait = 25
    $tunnelWaited = 0
    while ($tunnelWaited -lt $maxTunnelWait) {
        Start-Sleep -Seconds 1
        $tunnelWaited++
        if (Test-Path $TunnelLog) {
            $logContent = Get-Content -Path $TunnelLog -Raw -ErrorAction SilentlyContinue
            $allMatches = [regex]::Matches($logContent, 'https://[a-zA-Z0-9-]+\.trycloudflare\.com')
            if ($allMatches.Count -gt 0) {
                $foundUrl = $allMatches[$allMatches.Count - 1].Value
                $foundUrl | Out-File -FilePath $UrlFile -Encoding utf8
                break
            }
        }
    }
} else {
    # If tunnel already running, read cached URL if available
    if (Test-Path $UrlFile) {
        $foundUrl = (Get-Content -Path $UrlFile -Raw -ErrorAction SilentlyContinue).Trim()
    }
}

# ------------------------------------------------------------------------------
# 5. Display Windows Notification
# ------------------------------------------------------------------------------
try {
    Add-Type -AssemblyName System.Windows.Forms
    $notify = New-Object System.Windows.Forms.NotifyIcon
    $notify.Icon = [System.Drawing.SystemIcons]::Information
    $notify.BalloonTipTitle = "BMC AssetHub is Live!"
    
    $tipMsg = "Local: http://localhost:8085"
    if ($foundUrl) {
        $tipMsg += "`nPublic: $foundUrl"
    }
    
    $notify.BalloonTipText = $tipMsg
    $notify.Visible = $true
    $notify.ShowBalloonTip(5000)
    
    Start-Sleep -Seconds 2
    $notify.Dispose()
} catch {
    # Ignore notification errors on headless or locked environments
}
