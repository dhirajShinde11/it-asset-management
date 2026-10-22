@echo off
setlocal enabledelayedexpansion
title BMC AssetHub - Windows Auto-Start Installer

cls
echo ==============================================================================
echo        BMC ASSETHUB - WINDOWS AUTO-START INSTALLER
echo ==============================================================================
echo.
echo  This installer will configure BMC AssetHub to start automatically in the
echo  background whenever you turn on or log in to this laptop.
echo.

set "PROJECT_DIR=%~dp0"
:: Remove trailing backslash if present
if "%PROJECT_DIR:~-1%"=="\" set "PROJECT_DIR=%PROJECT_DIR:~0,-1%"

set "STARTUP_DIR=%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup"
set "DESKTOP_DIR=%USERPROFILE%\Desktop"
set "STARTUP_VBS=%STARTUP_DIR%\Launch-ITAM-On-Startup.vbs"
set "RUNNER_PS1=%PROJECT_DIR%\scripts\run-itam-background.ps1"
set "STATUS_BAT=%PROJECT_DIR%\status-itam.bat"
set "JAR_PATH=%PROJECT_DIR%\target\it-asset-management-1.0.0.jar"

:: 1. Verify / Build JAR
echo  [1/5] Checking application binary...
if not exist "%JAR_PATH%" (
    echo   Building production JAR package (this takes ~10 seconds)...
    call "%PROJECT_DIR%\mvnw.cmd" package -DskipTests
    if not exist "%JAR_PATH%" (
        echo.
        echo  [ERROR] Failed to compile it-asset-management-1.0.0.jar!
        echo  Please verify Java and Maven installation.
        pause
        exit /b 1
    )
)
echo   [OK] Production JAR is ready.

:: 2. Create the Silent Startup Launcher in Windows Startup Folder
echo.
echo  [2/5] Registering auto-start launcher in Windows Startup...
echo Set objShell = CreateObject("WScript.Shell") > "%STARTUP_VBS%"
echo objShell.CurrentDirectory = "%PROJECT_DIR%" >> "%STARTUP_VBS%"
echo strCommand = "powershell.exe -ExecutionPolicy Bypass -WindowStyle Hidden -NoProfile -File ""%RUNNER_PS1%""" >> "%STARTUP_VBS%"
echo objShell.Run strCommand, 0, False >> "%STARTUP_VBS%"

if exist "%STARTUP_VBS%" (
    echo   [OK] Registered: %STARTUP_VBS%
) else (
    echo   [ERROR] Failed to write startup file.
    pause
    exit /b 1
)

:: 3. Create Desktop Status Shortcut
echo.
echo  [3/5] Creating Desktop shortcut for 1-click status & URLs...
powershell -Command "$ws = New-Object -ComObject WScript.Shell; $s = $ws.CreateShortcut('%DESKTOP_DIR%\BMC AssetHub Status.lnk'); $s.TargetPath = '%STATUS_BAT%'; $s.WorkingDirectory = '%PROJECT_DIR%'; $s.IconLocation = 'shell32.dll,14'; $s.Description = 'BMC AssetHub Status and Control Console'; $s.Save()" >nul 2>&1
if exist "%DESKTOP_DIR%\BMC AssetHub Status.lnk" (
    echo   [OK] Desktop Shortcut created: BMC AssetHub Status.lnk
)

:: 4. Verify Windows Defender Firewall Rule for Port 8085
echo.
echo  [4/5] Checking Windows Defender Firewall rule for Port 8085...
netsh advfirewall firewall show rule name="ITAM Server Port 8085" >nul 2>&1
if %errorlevel% neq 0 (
    echo   Rule not yet active. Adding inbound firewall rule for Port 8085...
    netsh advfirewall firewall add rule name="ITAM Server Port 8085" dir=in action=allow protocol=TCP localport=8085 profile=any >nul 2>&1
) else (
    echo   [OK] Port 8085 firewall rule is already active.
)

:: 5. Launch Background Services Now
echo.
echo  [5/5] Launching background services now...
wscript.exe "%STARTUP_VBS%"

echo.
echo ==============================================================================
echo                     INSTALLATION COMPLETE & ACTIVE!
echo ==============================================================================
echo.
echo   * Whenever you start this laptop, BMC AssetHub and Cloudflare Tunnel
echo     will now automatically launch in the background.
echo.
echo   * Double-click the "BMC AssetHub Status" shortcut on your Desktop anytime
echo     to view live URLs, logs, or open the dashboard.
echo.
echo   * Opening live status console in 4 seconds...
echo.
timeout /t 4 >nul
call "%STATUS_BAT%"
