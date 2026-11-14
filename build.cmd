@echo off
setlocal
cd /d "%~dp0"

echo Building Aero...
echo.

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0build.ps1" %*
set "RESULT=%ERRORLEVEL%"

echo.
if not "%RESULT%"=="0" (
    echo Build failed. Read the messages above.
) else (
    echo Build finished:
    echo   output\Aero\Aero.exe
    echo   output\Aero-portable.zip
)
echo.
pause
exit /b %RESULT%
