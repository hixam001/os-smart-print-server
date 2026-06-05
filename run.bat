@echo off
:: run.bat - Start the OS Smart Print Server on Windows
:: Requires: Java 17+ on PATH

if not exist "smart-print-server.jar" (
    echo [ERROR] smart-print-server.jar not found.
    echo Please copy the .jar file here or build it first.
    pause
    exit /b 1
)

echo.
echo Starting OS Smart Print Server...
echo Dashboard -^> http://localhost:3000
echo Press Ctrl+C to stop.
echo.

java -jar smart-print-server.jar --server.port=3000
