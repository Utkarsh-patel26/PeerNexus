@echo off
REM JTorrent Integration Test Runner - Windows
REM Phase 1.3.2: Integration Test Infrastructure

setlocal EnableDelayedExpansion

cd /d "%~dp0"

echo =========================================
echo JTorrent Integration Test Suite
echo =========================================

REM Check Docker is available
where docker >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo ERROR: Docker is not installed or not in PATH
    exit /b 1
)

REM Create test data directories
echo Creating test directories...
if not exist "test-data\seed-data" mkdir "test-data\seed-data"
if not exist "test-data\seeder-config" mkdir "test-data\seeder-config"
if not exist "test-data\torrents" mkdir "test-data\torrents"
if not exist "test-data\download" mkdir "test-data\download"
if not exist "test-output" mkdir "test-output"

REM Create test file (10 MB random data)
echo Creating test data...
if not exist "test-data\seed-data\test-file.bin" (
    powershell -Command "$bytes = New-Object byte[] 10MB; (New-Object Random).NextBytes($bytes); [IO.File]::WriteAllBytes('test-data\seed-data\test-file.bin', $bytes)"
    echo Created 10 MB test file
)

REM Calculate checksum
for /f %%i in ('powershell -Command "(Get-FileHash -Path 'test-data\seed-data\test-file.bin' -Algorithm SHA256).Hash"') do set TEST_FILE_HASH=%%i
echo Test file SHA-256: %TEST_FILE_HASH%
echo %TEST_FILE_HASH% > test-data\test-file.sha256

REM Handle arguments
if "%1"=="stop" (
    echo Stopping test environment...
    docker-compose down
    echo Done.
    exit /b 0
)

if "%1"=="clean" (
    echo Cleaning up test environment...
    docker-compose down -v
    rmdir /s /q test-data 2>nul
    rmdir /s /q test-output 2>nul
    echo Done.
    exit /b 0
)

REM Start Docker containers
echo.
echo Starting test environment...
docker-compose up -d

REM Wait for services to be ready
echo Waiting for services to be healthy...
timeout /t 10 /nobreak >nul

echo.
echo =========================================
echo Test Environment Ready
echo =========================================
echo Tracker: http://localhost:6969
echo Seeder Web UI: http://localhost:9091 (admin/admin)
echo DHT Node: localhost:6881 (UDP)
echo.
echo Test data: test-data\seed-data\test-file.bin
echo Test torrent: test-data\torrents\test.torrent
echo.
echo Run tests with: mvn test -Dtest.integration=true
echo Stop environment: run-integration-tests.bat stop
echo.

if "%1"=="test" (
    echo Running integration tests...
    cd ..
    call mvn test -Dtest.integration=true "-Dtest=*IntegrationTest"
    set TEST_RESULT=%ERRORLEVEL%
    
    cd test-integration
    echo.
    if !TEST_RESULT! equ 0 (
        echo =========================================
        echo Integration Tests: PASSED
        echo =========================================
    ) else (
        echo =========================================
        echo Integration Tests: FAILED
        echo =========================================
    )
    
    exit /b !TEST_RESULT!
)

echo Usage: %0 [start^|stop^|clean^|test]
echo   start - Start test environment (default)
echo   stop  - Stop test environment
echo   clean - Stop and remove all test data
echo   test  - Run integration tests

endlocal
