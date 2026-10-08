@echo off
cd /d "%~dp0.."
echo Dang khoi dong Web Spring Boot...
start "Parking Web" powershell.exe -NoExit -NoProfile -ExecutionPolicy Bypass -File "%~dp0run-web.ps1"
timeout /t 5 /nobreak >nul
echo Dang khoi dong YOLO va OCR...
start "Parking ANPR" powershell.exe -NoExit -NoProfile -ExecutionPolicy Bypass -File "%~dp0run-anpr.ps1"
timeout /t 8 /nobreak >nul
echo Dang mo Desktop WinForms...
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0run-desktop.ps1"
if errorlevel 1 pause
