@echo off
setlocal
set "KEY_DIR=%~dp0keys"

if not exist "%KEY_DIR%" mkdir "%KEY_DIR%"
if exist "%KEY_DIR%\desktop-jwt-private.pem" (
  echo Private key already exists. Refusing to overwrite.
  exit /b 1
)
if exist "%KEY_DIR%\desktop-jwt-public.pem" (
  echo Public key already exists. Refusing to overwrite.
  exit /b 1
)

where openssl.exe >nul 2>nul
if errorlevel 1 (
  echo openssl.exe was not found on PATH.
  exit /b 1
)

openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "%KEY_DIR%\desktop-jwt-private.pem"
if errorlevel 1 exit /b 1
openssl pkey -in "%KEY_DIR%\desktop-jwt-private.pem" -pubout -out "%KEY_DIR%\desktop-jwt-public.pem"
if errorlevel 1 exit /b 1

icacls "%KEY_DIR%\desktop-jwt-private.pem" /inheritance:r /grant:r "%USERNAME%:R" >nul
icacls "%KEY_DIR%\desktop-jwt-public.pem" /inheritance:r /grant:r "%USERNAME%:R" >nul

echo Generated local keys in:
echo %KEY_DIR%
echo Keep the private file outside Git and do not share it.
endlocal
