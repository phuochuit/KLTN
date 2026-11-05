@echo off
setlocal
set "SCRIPT_DIR=%~dp0"
set "PYTHON=%SCRIPT_DIR%.venv\Scripts\python.exe"
if not exist "%PYTHON%" (
    echo [sync] Chua co Python venv tai "%SCRIPT_DIR%.venv".
    echo [sync] Tao venv va cai dat:
    echo         py -3 -m venv "%SCRIPT_DIR%.venv"
    echo         "%SCRIPT_DIR%.venv\Scripts\python.exe" -m pip install -r "%SCRIPT_DIR%requirements.txt"
    exit /b 1
)
"%PYTHON%" "%SCRIPT_DIR%sync_two_way.py" %*
exit /b %ERRORLEVEL%
