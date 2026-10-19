@echo off
setlocal enabledelayedexpansion
title Aegis ITAM - System and Live URL Status

:STATUS_LOOP
cls
echo ==============================================================================
echo              AEGIS IT ASSET MANAGEMENT - LIVE SERVER STATUS
echo ==============================================================================
echo.

set SERVER_STATUS=OFFLINE
set SERVER_PID=N/A
set TUNNEL_STATUS=OFFLINE
set TUNNEL_PID=N/A
set PUBLIC_URL=N/A

:: Check Port 8085
for /f "tokens=5" %%a in ('netstat -aon ^| findstr /r ":8085.*LISTENING"') do (
    set SERVER_STATUS=ONLINE
    set SERVER_PID=%%a
)

:: Check Cloudflared Process
for /f "tokens=2" %%a in ('tasklist /nh /fi "imagename eq cloudflared.exe" 2^>nul ^| findstr /i "cloudflared.exe"') do (
    set TUNNEL_STATUS=ONLINE
    set TUNNEL_PID=%%a
)

:: Read Public URL if available
if exist "logs\public_url.txt" (
    set /p PUBLIC_URL=<"logs\public_url.txt"
)

:: Get Local IP
for /f "tokens=2 delims=:" %%a in ('ipconfig ^| findstr /i "IPv4" ^| findstr /v "127.0.0.1"') do (
    set LOCAL_IP=%%a
    set LOCAL_IP=!LOCAL_IP: =!
    goto :IP_FOUND
)
:IP_FOUND

echo  [BACKEND SERVER]
if "%SERVER_STATUS%"=="ONLINE" (
    echo   Status:       [ACTIVE - LISTENING]
    echo   Process PID:  %SERVER_PID%
    echo   Local URL:    http://localhost:8085
    echo   LAN/Wi-Fi:    http://%COMPUTERNAME%:8085  [or http://!LOCAL_IP!:8085]
) else (
    echo   Status:       [STOPPED / OFFLINE]
)
echo.

echo  [PUBLIC INTERNET TUNNEL]
if "%TUNNEL_STATUS%"=="ONLINE" (
    echo   Status:       [ACTIVE - ENCRYPTED TUNNEL]
    echo   Process PID:  %TUNNEL_PID%
    echo   Public URL:   %PUBLIC_URL%
) else (
    echo   Status:       [STOPPED / OFFLINE]
)
echo.
echo ==============================================================================
echo  ACTIONS:
echo   [1] Open Local Dashboard in Browser (http://localhost:8085)
echo   [2] Open Public Internet URL in Browser
echo   [3] View Application Logs (logs\itam.log)
echo   [4] View Tunnel Logs (logs\tunnel.log)
echo   [5] Start / Restart Services in Background
echo   [6] Stop Services
echo   [R] Refresh Status
echo   [Q] Quit
echo ==============================================================================
echo.

choice /c 123456RQ /n /m "Select an option [1, 2, 3, 4, 5, 6, R, Q]: "
set CHOICE=%errorlevel%

if "%CHOICE%"=="1" (
    start http://localhost:8085
    goto :STATUS_LOOP
)
if "%CHOICE%"=="2" (
    if not "%PUBLIC_URL%"=="N/A" (
        start %PUBLIC_URL%
    ) else (
        echo.
        echo  [INFO] Public URL is not available or tunnel is offline.
        pause
    )
    goto :STATUS_LOOP
)
if "%CHOICE%"=="3" (
    if exist "logs\itam.log" (
        start notepad.exe "logs\itam.log"
    ) else (
        echo.
        echo  [INFO] logs\itam.log does not exist yet.
        pause
    )
    goto :STATUS_LOOP
)
if "%CHOICE%"=="4" (
    if exist "logs\tunnel.log" (
        start notepad.exe "logs\tunnel.log"
    ) else (
        echo.
        echo  [INFO] logs\tunnel.log does not exist yet.
        pause
    )
    goto :STATUS_LOOP
)
if "%CHOICE%"=="5" (
    echo.
    echo  Restarting Aegis ITAM services in background...
    wscript.exe "scripts\Launch-ITAM-Silently.vbs"
    timeout /t 3 >nul
    goto :STATUS_LOOP
)
if "%CHOICE%"=="6" (
    call stop-itam.bat
    timeout /t 2 >nul
    goto :STATUS_LOOP
)
if "%CHOICE%"=="7" (
    goto :STATUS_LOOP
)
if "%CHOICE%"=="8" (
    exit /b
)
