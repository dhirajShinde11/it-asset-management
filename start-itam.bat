@echo off
setlocal
title BMC AssetHub - Start Services

echo ==============================================================================
echo              BMC ASSETHUB - STARTING SERVICES
echo ==============================================================================
echo.
echo  Launching BMC AssetHub and Cloudflare Tunnel silently in the background...

wscript.exe "scripts\Launch-ITAM-Silently.vbs"

echo.
echo  [SUCCESS] Background services launched!
echo.
echo  To check live status, port, and public URL, run: status-itam.bat
echo.
timeout /t 3 >nul
call status-itam.bat
