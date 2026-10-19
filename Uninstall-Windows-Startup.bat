@echo off
setlocal
title Aegis ITAM - Remove Auto-Start

cls
echo ==============================================================================
echo        AEGIS IT ASSET MANAGEMENT - UNINSTALL AUTO-START
echo ==============================================================================
echo.

set "STARTUP_VBS=%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup\Launch-ITAM-On-Startup.vbs"
set "DESKTOP_LNK=%USERPROFILE%\Desktop\Aegis ITAM Status.lnk"

echo  Removing auto-start launcher from Windows Startup folder...
if exist "%STARTUP_VBS%" (
    del /f /q "%STARTUP_VBS%"
    echo   [OK] Deleted: %STARTUP_VBS%
) else (
    echo   [INFO] Auto-start launcher was not found in Startup folder.
)

echo.
echo  Removing Desktop shortcut...
if exist "%DESKTOP_LNK%" (
    del /f /q "%DESKTOP_LNK%"
    echo   [OK] Deleted: %DESKTOP_LNK%
)

echo.
echo  Do you also want to stop currently running background services?
choice /c YN /m "Stop services now? (Y/N): "
if %errorlevel% equ 1 (
    call "%~dp0stop-itam.bat"
)

echo.
echo ==============================================================================
echo  Aegis ITAM auto-start has been successfully removed!
echo ==============================================================================
echo.
pause
