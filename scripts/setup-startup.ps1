# ==============================================================================
# Aegis IT Asset Management - Setup Startup and Shortcuts
# ==============================================================================

$ProjectDir = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$StartupDir = [Environment]::GetFolderPath('Startup')
$DesktopDir = [Environment]::GetFolderPath('Desktop')
$StartupVbs = Join-Path $StartupDir 'Launch-ITAM-On-Startup.vbs'
$RunnerPs1  = Join-Path $ProjectDir 'scripts\run-itam-background.ps1'
$StatusBat  = Join-Path $ProjectDir 'status-itam.bat'

# 1. Create Startup VBS
$vbsContent = @"
Set objShell = CreateObject("WScript.Shell")
objShell.CurrentDirectory = "$ProjectDir"
strCommand = "powershell.exe -ExecutionPolicy Bypass -WindowStyle Hidden -NoProfile -File ""$RunnerPs1"""
objShell.Run strCommand, 0, False
"@
Set-Content -Path $StartupVbs -Value $vbsContent -Encoding Ascii
Write-Host "[OK] Windows Startup VBS registered at: $StartupVbs"

# 2. Create Desktop Shortcut
$ws = New-Object -ComObject WScript.Shell
$desktopLnk = Join-Path $DesktopDir "Aegis ITAM Status.lnk"
$shortcut = $ws.CreateShortcut($desktopLnk)
$shortcut.TargetPath = $StatusBat
$shortcut.WorkingDirectory = $ProjectDir
$shortcut.IconLocation = "shell32.dll,14"
$shortcut.Description = "Aegis ITAM Status and Control Console"
$shortcut.Save()
Write-Host "[OK] Desktop shortcut created at: $desktopLnk"

# 3. Trigger Launch-ITAM-Silently.vbs
Write-Host "[...] Launching Aegis ITAM and Cloudflare Tunnel in background..."
& wscript.exe "$ProjectDir\scripts\Launch-ITAM-Silently.vbs"
Write-Host "[OK] Background services triggered."
