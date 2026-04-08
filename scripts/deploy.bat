@echo off
:: =============================================================================
::  DITA Specialization Designer — Windows Deploy Script
::  Usage: scripts\deploy.bat [target-dir]
::
::  Builds the project and copies the distribution to the specified directory
::  (default: deploy\). Then optionally launches the application.
:: =============================================================================

setlocal enabledelayedexpansion

set SCRIPT_DIR=%~dp0
set PROJECT_DIR=%SCRIPT_DIR%..
set TARGET_DIR=%~1
if "%TARGET_DIR%"=="" set TARGET_DIR=%PROJECT_DIR%\deploy

echo.
echo ========================================================
echo   DITA Specialization Designer — Deploy
echo ========================================================
echo   Project : %PROJECT_DIR%
echo   Target  : %TARGET_DIR%
echo ========================================================

:: ── Step 1: Build ──────────────────────────────────────────────────────────
echo.
echo [1/3] Building project...
call "%PROJECT_DIR%\gradlew.bat" --project-dir "%PROJECT_DIR%" ci
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ERROR: Build failed. Fix errors above and retry.
    exit /b 1
)

:: ── Step 2: Install distribution ──────────────────────────────────────────
echo.
echo [2/3] Installing distribution to %TARGET_DIR%...
call "%PROJECT_DIR%\gradlew.bat" --project-dir "%PROJECT_DIR%" deploy ^
     -PdeployDir="%TARGET_DIR%"
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ERROR: Deploy task failed.
    exit /b 1
)

:: ── Step 3: Summary ───────────────────────────────────────────────────────
echo.
echo ========================================================
echo   Deployment complete!
echo   Fat JAR : %PROJECT_DIR%\build\libs\*-all.jar
echo   Dist ZIP: %PROJECT_DIR%\build\distributions\*.zip
echo   Install : %TARGET_DIR%
echo ========================================================
echo.
echo   To launch:
echo     %TARGET_DIR%\bin\dita-specialization-designer.bat
echo.

:: Ask user if they want to launch now
set /p LAUNCH="Launch application now? (Y/N): "
if /i "%LAUNCH%"=="Y" (
    start "" "%TARGET_DIR%\bin\dita-specialization-designer.bat"
)

endlocal
exit /b 0
