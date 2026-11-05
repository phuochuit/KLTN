@echo off
start "Auto Parking - DB Failover" /min powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0db-failover\watch-db.ps1" %*
exit /b 0
