@echo off
setlocal
set "SCRIPT_DIR=%~dp0"
start "Auto Parking - Sync" /min "%~dp0sync\sync-two-way.cmd" --loop --interval 300
exit /b 0
