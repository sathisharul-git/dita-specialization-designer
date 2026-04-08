@echo off
:: =============================================================================
::  DITA Specialization Designer — Quick Run (Windows)
::  Builds and launches the application in development mode.
::  Usage: scripts\run.bat
:: =============================================================================

set SCRIPT_DIR=%~dp0
set PROJECT_DIR=%SCRIPT_DIR%..

echo.
echo Launching DITA Specialization Designer...
call "%PROJECT_DIR%\gradlew.bat" --project-dir "%PROJECT_DIR%" run
