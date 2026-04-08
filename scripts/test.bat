@echo off
:: =============================================================================
::  DITA Specialization Designer — Test Runner (Windows)
::  Runs all JUnit tests and opens the HTML report.
::  Usage: scripts\test.bat
:: =============================================================================

set SCRIPT_DIR=%~dp0
set PROJECT_DIR=%SCRIPT_DIR%..

echo.
echo Running tests...
call "%PROJECT_DIR%\gradlew.bat" --project-dir "%PROJECT_DIR%" test jacocoTestReport

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo Tests FAILED. See report:
) else (
    echo.
    echo Tests PASSED. Report:
)

set REPORT=%PROJECT_DIR%\build\reports\tests\test\index.html
echo   %REPORT%

set /p OPEN="Open test report in browser? (Y/N): "
if /i "%OPEN%"=="Y" start "" "%REPORT%"
