@echo off
setlocal enabledelayedexpansion
title BMC AssetHub - Stop Services

echo ==============================================================================
echo              BMC ASSETHUB - STOPPING SERVICES
echo ==============================================================================
echo.

:: 1. Stop backend on Port 8085
echo  [1/2] Terminating BMC AssetHub Backend (Port 8085)...
for /f "tokens=5" %%a in ('netstat -aon ^| findstr /r ":8085.*LISTENING"') do (
    echo   Stopping PID %%a...
    taskkill /f /pid %%a >nul 2>&1
)

:: 2. Stop Cloudflare Tunnel
echo  [2/2] Terminating Cloudflare Tunnel...
taskkill /f /im cloudflared.exe >nul 2>&1

:: Clean up PID & temp files
if exist "logs\server.pid" del /f /q "logs\server.pid" >nul 2>&1
if exist "logs\tunnel.pid" del /f /q "logs\tunnel.pid" >nul 2>&1

echo.
echo ==============================================================================
echo  All BMC AssetHub background services have been stopped.
echo ==============================================================================
echo.
timeout /t 2 >nul
