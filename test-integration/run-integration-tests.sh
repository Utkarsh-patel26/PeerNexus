#!/bin/bash
# JTorrent Integration Test Runner
# Phase 1.3.2: Integration Test Infrastructure

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "========================================="
echo "JTorrent Integration Test Suite"
echo "========================================="

# Check Docker is available
if ! command -v docker &> /dev/null; then
    echo "ERROR: Docker is not installed or not in PATH"
    exit 1
fi

if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null; then
    echo "ERROR: Docker Compose is not installed"
    exit 1
fi

# Create test data directories
echo "Creating test directories..."
mkdir -p test-data/seed-data
mkdir -p test-data/seeder-config
mkdir -p test-data/torrents
mkdir -p test-data/download
mkdir -p test-output

# Create test file (10 MB random data)
echo "Creating test data..."
if [ ! -f test-data/seed-data/test-file.bin ]; then
    dd if=/dev/urandom of=test-data/seed-data/test-file.bin bs=1M count=10 2>/dev/null
    echo "Created 10 MB test file"
fi

# Calculate SHA-256 checksum
TEST_FILE_HASH=$(sha256sum test-data/seed-data/test-file.bin | cut -d' ' -f1)
echo "Test file SHA-256: $TEST_FILE_HASH"
echo "$TEST_FILE_HASH" > test-data/test-file.sha256

# Start Docker containers
echo ""
echo "Starting test environment..."
docker-compose up -d

# Wait for services to be ready
echo "Waiting for services to be healthy..."
sleep 10

# Check tracker is running
if ! curl -s http://localhost:6969/announce &> /dev/null; then
    echo "WARNING: Tracker may not be responding on HTTP"
fi

# Create torrent file using transmission-create (if available in container)
echo ""
echo "Creating test torrent..."
docker exec jtorrent-test-seeder transmission-create \
    -o /watch/test.torrent \
    -t http://tracker:6969/announce \
    -s 256 \
    /downloads/test-file.bin 2>/dev/null || {
    echo "Note: Using pre-created torrent or manual creation needed"
}

echo ""
echo "========================================="
echo "Test Environment Ready"
echo "========================================="
echo "Tracker: http://localhost:6969"
echo "Seeder Web UI: http://localhost:9091 (admin/admin)"
echo "DHT Node: localhost:6881 (UDP)"
echo ""
echo "Test data: test-data/seed-data/test-file.bin"
echo "Test torrent: test-data/torrents/test.torrent"
echo ""
echo "Run tests with: mvn test -Dtest.integration=true"
echo "Stop environment: ./run-integration-tests.sh stop"
echo ""

# Parse arguments
if [ "$1" == "stop" ]; then
    echo "Stopping test environment..."
    docker-compose down
    echo "Done."
    exit 0
fi

if [ "$1" == "clean" ]; then
    echo "Cleaning up test environment..."
    docker-compose down -v
    rm -rf test-data test-output
    echo "Done."
    exit 0
fi

if [ "$1" == "test" ]; then
    echo "Running integration tests..."
    cd ..
    mvn test -Dtest.integration=true -Dtest=*IntegrationTest
    TEST_RESULT=$?
    
    cd "$SCRIPT_DIR"
    echo ""
    if [ $TEST_RESULT -eq 0 ]; then
        echo "========================================="
        echo "Integration Tests: PASSED"
        echo "========================================="
    else
        echo "========================================="
        echo "Integration Tests: FAILED"
        echo "========================================="
    fi
    
    exit $TEST_RESULT
fi

echo "Usage: $0 [start|stop|clean|test]"
echo "  start - Start test environment (default)"
echo "  stop  - Stop test environment"
echo "  clean - Stop and remove all test data"
echo "  test  - Run integration tests"
