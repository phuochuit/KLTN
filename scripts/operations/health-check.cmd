@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0health-check.ps1" %*
exit /b %errorlevel%
