@echo off
setlocal
title BMC AssetHub - Network Sharing Setup

:: Check for Administrator privileges
net session >nul 2>&1
if %errorlevel% neq 0 (
    echo ========================================================
    echo  Requesting Administrator Privileges...
    echo ========================================================
    echo  Windows requires Administrator access to allow Port 8085
    echo  through Windows Defender Firewall.
    echo.
    powershell -Command "Start-Process '%~f0' -Verb RunAs"
    exit /b
)

cls
echo ========================================================
echo   BMC ASSETHUB - ZERO-CONFIG NETWORK SETUP
echo ========================================================
echo.
echo  Configuring Windows Defender Firewall for Port 8085...
echo.

:: Remove any existing old rule to prevent duplicates
netsh advfirewall firewall delete rule name="ITAM Server Port 8085" >nul 2>&1

:: Add permanent inbound rule for all profiles (Domain, Private, Public)
netsh advfirewall firewall add rule name="ITAM Server Port 8085" dir=in action=allow protocol=TCP localport=8085 profile=any >nul 2>&1

if %errorlevel% equ 0 (
    echo  [SUCCESS] Port 8085 is now permanently open in Windows Firewall!
    echo  Anyone connected to your Wi-Fi or LAN can access this website.
) else (
    echo  [WARNING] Could not update firewall automatically.
)

echo.
echo ========================================================
echo   YOUR PERMANENT ZERO-CONFIG ACCESS ADDRESSES
echo ========================================================
echo.
echo  1. Computer Name (Never changes across any Wi-Fi network):
echo     http://%COMPUTERNAME%:8085
echo.
echo  2. Apple/Mobile Hostname (iPhones, Android, Macs, iPads):
echo     http://%COMPUTERNAME%.local:8085
echo.
echo  3. To discover your current Wi-Fi IP anytime, open the dashboard
echo     and click the green "LAN" badge in the top bar.
echo.
echo ========================================================
echo  Setup complete! Press any key to exit.
echo ========================================================
pause >nul
