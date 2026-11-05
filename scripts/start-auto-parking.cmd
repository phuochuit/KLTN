@echo off
setlocal EnableExtensions EnableDelayedExpansion
title Auto Parking - Khoi dong he thong

REM ============================================================
REM  Auto Parking - one-click startup
REM  - Starts MySQL local (if installed) for DB fallback
REM  - Starts Docker Desktop and the web container
REM  - Starts ANPR on port 8001 if it is not already running
REM  - Starts two-way Aiven <-> local sync every 5 minutes
REM  - Starts the Aiven/Laragon failover watchdog
REM  - Ensures Tailscale Funnel points to 127.0.0.1:8080
REM  - Optionally opens Desktop and the local web page
REM
REM  Usage:
REM    scripts\start-auto-parking.cmd
REM    scripts\start-auto-parking.cmd --no-desktop --no-browser --no-pause
REM    scripts\start-auto-parking.cmd --no-sync --no-failover
REM
REM  Do not run scripts\run-web.cmd or scripts\run-all-laragon.cmd
REM  while the Docker web container is running.
REM ============================================================

set "ROOT=D:\Code\KhoaLuan"
set "DEPLOY=%ROOT%\deploy"
set "LOCAL_URL=http://localhost:8080"
set "PUBLIC_URL=https://autoparking.tail57e32c.ts.net"
set "START_DESKTOP=1"
set "START_BROWSER=1"
set "START_SYNC=1"
set "START_FAILOVER=1"
set "NO_PAUSE=0"
set "ONLINE_OK=0"

:parse_args
if "%~1"=="" goto :args_done
if /I "%~1"=="--no-desktop" set "START_DESKTOP=0"
if /I "%~1"=="--no-browser" set "START_BROWSER=0"
if /I "%~1"=="--no-sync" set "START_SYNC=0"
if /I "%~1"=="--no-failover" set "START_FAILOVER=0"
if /I "%~1"=="--no-pause" set "NO_PAUSE=1"
shift
goto :parse_args
:args_done

echo ============================================================
echo   AUTO PARKING - KHOI DONG HE THONG
echo ============================================================
echo.

echo [1/6] MySQL local (du phong)...
powershell -NoProfile -Command "$s = Get-Service MySQL80 -ErrorAction SilentlyContinue; if ($s -and $s.Status -eq 'Running') { exit 0 } else { exit 1 }"
if errorlevel 1 (
    net start MySQL80 >nul 2>&1
    if errorlevel 1 (
        echo   - Khong khoi dong duoc MySQL80. Bo qua, web se dung Aiven.
    ) else (
        echo   - Da khoi dong MySQL80.
    )
) else (
    echo   - MySQL80 dang chay.
)

echo [2/6] Docker Desktop...
where docker >nul 2>&1
if errorlevel 1 (
    echo   - Khong tim thay lenh docker. Hay cai Docker Desktop.
    goto :fail
)
docker info >nul 2>&1
if errorlevel 1 (
    echo   - Docker chua chay. Dang mo Docker Desktop...
    if exist "%ProgramFiles%\Docker\Docker\Docker Desktop.exe" (
        start "" "%ProgramFiles%\Docker\Docker\Docker Desktop.exe"
    ) else if exist "%LocalAppData%\Docker\Docker Desktop.exe" (
        start "" "%LocalAppData%\Docker\Docker Desktop.exe"
    ) else (
        echo   - Khong tim thay Docker Desktop.exe. Hay mo Docker Desktop thu cong.
        goto :fail
    )
    call :wait_docker
    if errorlevel 1 (
        echo   - Docker chua san sang sau 120 giay.
        goto :fail
    )
)
echo   - Docker da san sang.

echo [3/6] Web Docker...
pushd "%DEPLOY%"
docker compose up -d
if errorlevel 1 (
    popd
    echo   - Khong khoi dong duoc web.
    goto :fail
)
popd
call :wait_web
if errorlevel 1 (
    echo   - Web chua san sang. 40 dong log cuoi:
    pushd "%DEPLOY%"
    docker compose logs --tail=40 web
    popd
    goto :fail
)
echo   - Web da san sang.

echo [4/6] ANPR (nhan dang bien so)...
call :check_anpr
if errorlevel 1 (
    powershell -NoProfile -Command "if (Get-NetTCPConnection -LocalPort 8001 -State Listen -ErrorAction SilentlyContinue) { exit 0 } else { exit 1 }"
    if not errorlevel 1 (
        echo   - Cong 8001 dang bi chiem nhung ANPR chua san sang.
        echo   - Khong tu mo them de tranh trung cong.
        goto :fail
    )
    echo   - Dang khoi dong ANPR...
    start "Auto Parking - ANPR" /min powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%ROOT%\scripts\run-anpr.ps1"
    call :wait_anpr
    if errorlevel 1 (
        echo   - ANPR chua san sang sau 240 giay.
        echo   - Xem cua so "Auto Parking - ANPR" de biet loi.
        goto :fail
    )
)
echo   - ANPR da san sang.

echo [Sync] Dong bo du lieu Aiven - MySQL local...
if "%START_SYNC%"=="1" (
    start "Auto Parking - Sync" /min "%ROOT%\scripts\sync\sync-two-way.cmd" --loop --interval 300
    echo   - Da bat dong bo 5 phut mot lan.
) else (
    echo   - Bo qua dong bo.
)

echo [Failover] Giam sat Aiven/Laragon...
if "%START_FAILOVER%"=="1" (
    start "Auto Parking - DB Failover" /min powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%ROOT%\scripts\db-failover\watch-db.ps1"
    echo   - Da bat tu chuyen DB khi Aiven mat ket noi.
) else (
    echo   - Bo qua giam sat DB.
)

echo [5/6] Tailscale Funnel...
set "TAILSCALE=tailscale"
where tailscale >nul 2>&1
if errorlevel 1 (
    if exist "%ProgramFiles%\Tailscale\tailscale.exe" (
        set "TAILSCALE=%ProgramFiles%\Tailscale\tailscale.exe"
    ) else (
        echo   - Khong tim thay tailscale.exe.
        set "ONLINE_OK=0"
        goto :after_funnel
    )
)
"%TAILSCALE%" funnel --bg 8080 >nul 2>&1
if errorlevel 1 (
    echo   - CANH BAO: chua bat duoc Funnel. Web local van chay.
    set "ONLINE_OK=0"
) else (
    echo   - Funnel da bat.
    set "ONLINE_OK=1"
)
:after_funnel

echo [6/6] Kiem tra...
echo.
powershell -NoProfile -Command "try { $w = Invoke-RestMethod 'http://127.0.0.1:8080/api/parking/health' -TimeoutSec 5; Write-Host ('Web local: ' + $w.status + ' | DB: ' + $w.database) } catch { Write-Host 'Web local: KHONG SAN SANG' }"
powershell -NoProfile -Command "try { $a = Invoke-RestMethod 'http://127.0.0.1:8001/health' -TimeoutSec 5; Write-Host ('ANPR: ' + $a.status) } catch { Write-Host 'ANPR: KHONG SAN SANG' }"
echo.
echo ============================================================
echo   AUTO PARKING DA SAN SANG
echo ============================================================
echo   Local:  %LOCAL_URL%
if "%ONLINE_OK%"=="1" (
    echo   Online: %PUBLIC_URL%
) else (
    echo   Online: CHUA SAN SANG - kiem tra Tailscale Funnel
)
echo.

if "%START_DESKTOP%"=="1" (
    echo Dang mo Desktop...
    start "Auto Parking - Desktop" /min powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%ROOT%\scripts\run-desktop.ps1"
)
if "%START_BROWSER%"=="1" start "" "%LOCAL_URL%"

if "%NO_PAUSE%"=="0" pause
exit /b 0


:wait_docker
set /a tries=0
:wait_docker_loop
docker info >nul 2>&1
if not errorlevel 1 exit /b 0
set /a tries+=1
if !tries! GEQ 60 exit /b 1
timeout /t 2 /nobreak >nul
goto :wait_docker_loop


:wait_web
set /a tries=0
:wait_web_loop
powershell -NoProfile -Command "try { $h = Invoke-RestMethod 'http://127.0.0.1:8080/api/parking/health' -TimeoutSec 3; if ($h.status -eq 'UP') { exit 0 } } catch { }; exit 1"
if not errorlevel 1 exit /b 0
set /a tries+=1
if !tries! GEQ 90 exit /b 1
timeout /t 2 /nobreak >nul
goto :wait_web_loop


:check_anpr
powershell -NoProfile -Command "try { $h = Invoke-RestMethod 'http://127.0.0.1:8001/health' -TimeoutSec 3; if ($h.status -eq 'UP' -and $h.service -eq 'parking-anpr') { exit 0 } } catch { }; exit 1"
exit /b %errorlevel%


:wait_anpr
set /a tries=0
:wait_anpr_loop
call :check_anpr
if not errorlevel 1 exit /b 0
set /a tries+=1
if !tries! GEQ 120 exit /b 1
timeout /t 2 /nobreak >nul
goto :wait_anpr_loop


:fail
echo.
echo ============================================================
echo   KHOI DONG THAT BAI
echo ============================================================
echo   Xem thong bao phia tren de xu ly.
if "%NO_PAUSE%"=="0" pause
exit /b 1
