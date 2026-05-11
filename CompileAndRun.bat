@echo off
setlocal

echo =========================
echo Compiling project...
echo =========================

if not exist out mkdir out

javac -d out server\*.java client\*.java

if %errorlevel% neq 0 (
    echo Compilation failed.
    pause
    exit /b
)

echo.
echo =========================
echo Starting server...
echo =========================

start "Server" cmd /k java -cp out server.ServerMain

timeout /t 2 >nul

echo =========================
echo Starting client...
echo =========================

start "Client" cmd /k java -cp out client.ClientCLI

echo.
echo Done.
