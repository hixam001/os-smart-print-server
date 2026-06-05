@echo off
setlocal enabledelayedexpansion

set SCRIPT_DIR=%~dp0
set FRONTEND_DIR=%SCRIPT_DIR%frontend
set BACKEND_DIR=%SCRIPT_DIR%backend
set STATIC_DIR=%BACKEND_DIR%\src\main\resources\static
set MVN=%SCRIPT_DIR%apache-maven-3.9.16\bin\mvn.cmd

echo.
echo ==============================================
echo    OS Smart Print Server - Production Build   
echo ==============================================
echo.

:: ── Step 1: Frontend ─────────────────────────────────────────────────────────
echo [1/3] Building React frontend...
cd /d "%FRONTEND_DIR%"
call npm install --silent
call npm run build -- --logLevel warn

:: ── Step 2: Copy dist -> static resources ────────────────────────────────────
echo [2/3] Embedding frontend into Spring Boot static resources...
if exist "%STATIC_DIR%" rmdir /s /q "%STATIC_DIR%"
mkdir "%STATIC_DIR%"
xcopy /E /I /Q "%FRONTEND_DIR%\dist\*" "%STATIC_DIR%\" >nul

:: ── Step 3: Package fat JAR ──────────────────────────────────────────────────
echo [3/3] Packaging Spring Boot fat JAR...
cd /d "%BACKEND_DIR%"
call "%MVN%" package -DskipTests -q

cd /d "%SCRIPT_DIR%"
for %%F in ("%BACKEND_DIR%\target\smart-print-server-*.jar") do (
    echo "%%F" | findstr /i /v "sources javadoc tests" >nul
    if not errorlevel 1 (
        copy /y "%%F" "%SCRIPT_DIR%smart-print-server.jar" >nul
        goto :done_copy
    )
)
:done_copy

echo.
echo ======================================================
echo   SUCCESS! Build complete.
echo.
echo   Run:  run.bat
echo   URL:  http://localhost:3000
echo   (browser opens automatically on startup)
echo ======================================================
echo.
