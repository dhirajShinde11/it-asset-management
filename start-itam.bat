@echo off
setlocal
title Aegis ITAM - Start Services

echo ==============================================================================
echo              AEGIS IT ASSET MANAGEMENT - STARTING SERVICES
echo ==============================================================================
echo.
echo  Launching Aegis ITAM and Cloudflare Tunnel silently in the background...

wscript.exe "scripts\Launch-ITAM-Silently.vbs"

echo.
echo  [SUCCESS] Background services launched!
echo.
echo  To check live status, port, and public URL, run: status-itam.bat
echo.
timeout /t 3 >nul
call status-itam.bat
