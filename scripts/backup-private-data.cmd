@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0backup-private-data.ps1"
if errorlevel 1 (
    echo.
    echo Backup private-data failed.
    pause
    exit /b 1
)
echo.
echo Backup private-data completed.
pause
