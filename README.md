 ```
                                  ██████╗ ███████╗███████╗██████╗ ███╗   ██╗███████╗██╗  ██╗██╗   ██╗███████╗
                                  ██╔══██╗██╔════╝██╔════╝██╔══██╗████╗  ██║██╔════╝╚██╗██╔╝██║   ██║██╔════╝
                                  ██████╔╝█████╗  █████╗  ██████╔╝██╔██╗ ██║█████╗   ╚███╔╝ ██║   ██║███████╗
                                  ██╔═══╝ ██╔══╝  ██╔══╝  ██╔══██╗██║╚██╗██║██╔══╝   ██╔██╗ ██║   ██║╚════██║
                                  ██║     ███████╗███████╗██║  ██║██║ ╚████║███████╗██╔╝ ██╗╚██████╔╝███████║
                                  ╚═╝     ╚══════╝╚══════╝╚═╝  ╚═╝╚═╝  ╚═══╝╚══════╝╚═╝  ╚═╝ ╚═════╝ ╚══════╝

                  A Modern BitTorrent Client — Java 21 · JavaFX · Docker
```

<div align="center">

[![CI](https://github.com/Utkarsh-patel26/PeerNexus/actions/workflows/ci.yml/badge.svg)](https://github.com/Utkarsh-patel26/PeerNexus/actions/workflows/ci.yml)
[![CodeQL](https://github.com/Utkarsh-patel26/PeerNexus/actions/workflows/codeql.yml/badge.svg)](https://github.com/Utkarsh-patel26/PeerNexus/actions/workflows/codeql.yml)
[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)](https://openjdk.org/projects/jdk/21/)
[![JavaFX](https://img.shields.io/badge/JavaFX-21.0.2-blue?logo=java)](https://openjfx.io/)
[![Maven](https://img.shields.io/badge/Maven-3.8+-red?logo=apachemaven)](https://maven.apache.org/)
[![Docker](https://img.shields.io/badge/Docker-ready-2496ED?logo=docker)](https://hub.docker.com/)
[![License: MIT](https://img.shields.io/badge/License-MIT-green)](LICENSE)

</div>

---

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Architecture](#architecture)
- [Requirements](#requirements)
- [Quick Start](#quick-start)
- [Building the Project](#building-the-project)
- [Running the Application](#running-the-application)
  - [GUI Mode](#1-gui-mode-desktop)
  - [Headless / Server Mode](#2-headless--server-mode)
  - [CLI Commands](#3-cli-commands)
  - [Docker](#4-docker)
  - [Docker Compose (Staging)](#5-docker-compose-staging)
- [CLI Reference](#cli-reference)
- [Configuration](#configuration)
- [Testing](#testing)
- [CI/CD Pipeline](#cicd-pipeline)
- [Protocol Support](#protocol-support)
- [Project Structure](#project-structure)
- [Contributing](#contributing)

---

## Overview

**PeerNexus** is a production-grade BitTorrent client built from scratch in Java 21. It ships with a full JavaFX desktop GUI, a headless REST/WebSocket server mode, and a rich command-line interface — all in a single fat JAR. The same binary runs interactively on a developer's laptop or silently inside a Docker container.

Under the hood, PeerNexus implements eight BitTorrent Enhancement Proposals (BEPs), a distributed hash table for trackerless peer discovery, peer exchange, UPnP port mapping, and an optional message-stream encryption layer. Downloads are managed through a pipeline of pluggable schedulers, rate limiters, and connection managers, with session state persisted to disk so downloads survive restarts.

---

## Features

### Core Protocol
| Feature | Description |
|---|---|
| Full BEP 3 | Piece selection, choking/unchoking, endgame mode |
| DHT (BEP 5) | Trackerless peer discovery via Kademlia DHT |
| Metadata Exchange (BEP 9/10) | Fetch `.torrent` metadata from peers via magnet links |
| PEX (BEP 11) | Decentralised peer exchange |
| Multi-tracker (BEP 12) | Announce to multiple trackers per torrent |
| UDP Tracker (BEP 15) | Lightweight tracker protocol |
| Compact Peers (BEP 23) | Compact peer list parsing |
| Magnet Links | Full magnet URI support without a `.torrent` file |
| UPnP | Automatic NAT port mapping |
| Encryption | Optional message stream encryption |

### Download Engine
- **Rarest-first piece selection** with random first-piece strategy to help seeding health
- **Endgame mode** — broadcasts requests for the final missing blocks to all peers simultaneously
- **Token-bucket rate limiter** — precise per-torrent and global upload/download caps
- **Stalled download detector** — automatically re-queues stuck torrents
- **Persistent state** — resumes incomplete downloads across restarts
- **Batch operations** — start, pause, and remove multiple torrents at once

### Interfaces
- **JavaFX GUI** — live speed graph, piece map, peer list, file browser, log viewer, system tray
- **REST API** (port 8080) — programmatic control over downloads
- **WebSocket stats** (port 8081) — real-time metrics stream
- **Terminal UI** — lightweight TUI for headless environments
- **CLI** — scriptable command-line tools for automation

### Ops & Observability
- **Multi-stage Docker build** — Maven builder → minimal JRE 21 image, non-root user
- **Health endpoint** — `GET /api/stats` for container health checks
- **Structured logging** — configurable log level and file output
- **SBOM generation** — CycloneDX software bill of materials on every release
- **Security scanning** — CodeQL, OWASP Dependency-Check, Trivy, Gitleaks

---

## Architecture

```
+--------------------------------------------------------------------------+
|                            PeerNexus Runtime                             |
|                                                                          |
|  +--------------+   +--------------+   +----------------------------+    |
|  |  JavaFX GUI  |   |   REST API   |   |     CLI / Terminal UI      |    |
|  |  (GUI mode)  |   |  (port 8080) |   |     (stdin / stdout)       |    |
|  +------+-------+   +------+-------+   +-------------+--------------+    |
|         |                  |                          |                  |
|         +------------------+--------------------------+                  |
|                                    |                                     |
|                         +----------v----------+                          |
|                         |  TorrentSessionMgr  |                          |
|                         |  (multi-torrent hub)|                          |
|                         +----------+----------+                          |
|                                    |                                     |
|          +-------------------------+---------------------+               |
|          |                         |                     |               |
|  +-------v--------+    +-----------v------+   +---------v----------+     |
|  | TorrentSession |    | DownloadCoord.   |   |  PieceManager      |     |
|  | (per-torrent)  |    | RequestScheduler |   |  DiskManager       |     |
|  | ChokingMgr     |    | RateLimiter      |   |  StorageVerifier   |     |
|  | PeerScorer     |    | ConnectionLimiter|   |  BlockIndex        |     |
|  +-------+--------+    +-----------+------+   +---------+----------+     |
|          |                         |                     |               |
|          +-------------------------+---------------------+               |
|                                    |                                     |
|                         +----------v----------+                          |
|                         |   Peer Wire Layer   |                          |
|                         | Handshake  Bitfield |                          |
|                         | Request    Piece    |                          |
|                         | Extended Protocol   |                          |
|                         +----------+----------+                          |
|                                    |                                     |
|       +----------------------------+-------------------------+           |
|       |                            |                         |           |
|  +----v------+          +----------v-----+        +---------v-----+      |
|  |  Tracker  |          |   DHT Node     |        |  PEX Manager  |      |
|  | HTTP/UDP  |          | (BEP 5 / Kad.) |        |   (BEP 11)    |      |
|  +-----------+          +----------------+        +---------------+      |
|                                                                          |
|  +-----------------------------------------------------------------+     |
|  |                       Support Services                          |     |
|  |  EventBus  Persistence  Automation  Proxy  RSS  Search          |     |
|  |  Logging   Plugin System  UPnP  Encryption  Streaming           |     |
|  +-----------------------------------------------------------------+     |
+--------------------------------------------------------------------------+

Network Ports:
  TCP 6881  -->  Peer wire protocol (upload / download)
  UDP 6881  -->  DHT queries + UDP tracker announces
  TCP 8080  -->  REST API
  TCP 8081  -->  WebSocket stats
```

### Component Summary

| Package | Responsibility |
|---|---|
| `core` | Session lifecycle, choking, peer scoring, endgame, queue management |
| `peer` | Peer wire protocol — handshake, messages, connection state |
| `storage` | Piece validation, disk I/O, file mapping, block index |
| `tracker` | HTTP and UDP tracker announces and responses |
| `dht` | Kademlia DHT node, routing table, peer lookup |
| `scheduler` | Bandwidth scheduling, request batching |
| `gui` | JavaFX controllers, speed graph, piece map, tray |
| `web` | REST endpoints, WebSocket broadcaster |
| `cli` | Argument parsing, command dispatch, handler per sub-command |
| `automation` | Rule-based download automation and scheduling |
| `persistence` | Torrent state serialisation / deserialisation |
| `events` | Internal event bus (decoupled pub/sub) |
| `encryption` | Optional MSE/PE message stream encryption |
| `proxy` | SOCKS / HTTP proxy support |
| `rss` | RSS feed subscription and auto-download |
| `search` | Torrent search integration |
| `streaming` | Progressive playback via sequential piece selection |
| `plugin` | Extension point API for third-party plugins |
| `metadata` | Torrent file creation, info-hash extraction |
| `parser` | Bencode encoder/decoder |

---

## Requirements

| Dependency | Minimum Version | Notes |
|---|---|---|
| JDK | 21 | OpenJDK or Eclipse Temurin recommended |
| Maven | 3.8 | Build tool |
| Docker | 20.10+ | Optional — only needed for Docker/integration tests |
| Docker Compose | v2 | Optional — staging environment |

> **GUI mode** additionally requires a display server. On headless Linux (CI, server), use `--headless` or set `-Djava.awt.headless=true`.

---

## Quick Start

```bash
# 1. Clone
git clone https://github.com/Utkarsh-patel26/PeerNexus.git
cd PeerNexus

# 2. Build
mvn clean package -DskipTests

# 3a. Launch GUI
java -jar target/jtorrent-gui.jar

# 3b. Or run headless with REST API
java -Djava.awt.headless=true -jar target/jtorrent-gui.jar

# 3c. Or run in Docker
docker build -f docker/Dockerfile -t peernexus:latest .
docker run -d -p 8080:8080 -p 8081:8081 -p 6881:6881/udp peernexus:latest
```

---

## Building the Project

```bash
# Full build with tests
mvn clean verify

# Build without running tests (faster)
mvn clean package -DskipTests

# Build without integration tests only
mvn clean verify -DskipITs

# Build and generate coverage report
mvn clean verify jacoco:report

# Build and generate SBOM (CycloneDX)
mvn clean package cyclonedx:makeAggregateBom
```

The build produces:

```
target/
├── jtorrent-gui.jar        # Fat JAR — all dependencies bundled, ~50-80 MB
├── bom.xml                 # CycloneDX SBOM
└── site/jacoco/            # Coverage HTML report (after jacoco:report)
```

---

## Running the Application

### 1. GUI Mode (Desktop)

The default mode launches the full JavaFX desktop application.

```bash
java -jar target/jtorrent-gui.jar
```

You can also double-click `jtorrent-gui.jar` from your file manager if your OS associates `.jar` files with a JRE.

**With a custom config file:**
```bash
java -jar target/jtorrent-gui.jar --config /path/to/my-config.json
```

**With custom download and state directories:**
```bash
java -jar target/jtorrent-gui.jar \
  --config config.json \
  -o /mnt/data/downloads
```

**With bandwidth limits:**
```bash
# Limit upload to 500 KB/s, download to 2000 KB/s
java -jar target/jtorrent-gui.jar \
  --max-upload 500 \
  --max-download 2000
```

---

### 2. Headless / Server Mode

Run without a display — suitable for servers, Docker containers, and CI.

```bash
java -Djava.awt.headless=true -jar target/jtorrent-gui.jar
```

The REST API listens on port **8080** and WebSocket stats on port **8081** automatically in headless mode.

**Custom ports:**
```bash
java -Djava.awt.headless=true \
  -jar target/jtorrent-gui.jar \
  --port 9090 \
  --ws-port 9091
```

**With JVM memory tuning (recommended for containers):**
```bash
java -Djava.awt.headless=true \
     -XX:MaxRAMPercentage=75.0 \
     -XX:+UseG1GC \
     -jar target/jtorrent-gui.jar
```

**Check the REST API:**
```bash
curl http://localhost:8080/api/stats
```

---

### 3. CLI Commands

All CLI sub-commands follow the pattern:

```
java -jar target/jtorrent-gui.jar <command> [options]
```

#### Download a torrent file

```bash
java -jar target/jtorrent-gui.jar download <torrent-file> [output-dir]

# Examples
java -jar target/jtorrent-gui.jar download ubuntu.torrent
java -jar target/jtorrent-gui.jar download ubuntu.torrent /mnt/data/downloads
java -jar target/jtorrent-gui.jar download ubuntu.torrent ./out --max-download 1000
```

#### Download from a magnet link

```bash
java -jar target/jtorrent-gui.jar magnet <magnet-uri> [output-dir]

# Example
java -jar target/jtorrent-gui.jar magnet \
  "magnet:?xt=urn:btih:HASH&dn=Name&tr=udp%3A%2F%2Ftracker.example.com" \
  ./downloads
```

#### Create a torrent file

```bash
java -jar target/jtorrent-gui.jar create <source-dir> <output.torrent> [announce-url]

# Minimal
java -jar target/jtorrent-gui.jar create ./my-files output.torrent

# With tracker and options
java -jar target/jtorrent-gui.jar create ./my-files output.torrent \
  http://tracker.example.com/announce \
  --add-tracker udp://tracker2.example.com:1337/announce \
  --comment "My release" \
  --piece-size 524288 \
  --private

# Force overwrite existing output file
java -jar target/jtorrent-gui.jar create ./my-files output.torrent -f
```

#### Validate a torrent file

```bash
java -jar target/jtorrent-gui.jar validate-torrent <torrent-file>

# Example
java -jar target/jtorrent-gui.jar validate-torrent ubuntu.torrent
```

#### Diagnostic commands

```bash
# Test tracker announcement
java -jar target/jtorrent-gui.jar announce-test <torrent-file>

# Test peer discovery
java -jar target/jtorrent-gui.jar peer-test

# Test storage system
java -jar target/jtorrent-gui.jar storage-test <torrent-file>

# Test scheduler
java -jar target/jtorrent-gui.jar run-scheduler-test
```

---

### 4. Docker

#### Build the image

```bash
# Standard build
docker build -f docker/Dockerfile -t peernexus:latest .

# With a specific version tag
docker build -f docker/Dockerfile -t peernexus:1.0.0 .

# Rebuild without cache
docker build --no-cache -f docker/Dockerfile -t peernexus:latest .
```

#### Run the container

**Minimal (ephemeral, no persistence):**
```bash
docker run -d \
  -p 8080:8080 \
  -p 8081:8081 \
  -p 6881:6881 \
  -p 6881:6881/udp \
  peernexus:latest
```

**With persistent volumes (recommended):**
```bash
docker run -d \
  --name peernexus \
  -p 8080:8080 \
  -p 8081:8081 \
  -p 6881:6881 \
  -p 6881:6881/udp \
  -v peernexus-downloads:/app/downloads \
  -v peernexus-state:/app/state \
  -v peernexus-logs:/app/logs \
  peernexus:latest
```

**With a custom config file:**
```bash
docker run -d \
  --name peernexus \
  -p 8080:8080 \
  -p 6881:6881/udp \
  -v $(pwd)/config.json:/app/config.json:ro \
  -v peernexus-downloads:/app/downloads \
  -v peernexus-state:/app/state \
  peernexus:latest
```

**With resource limits:**
```bash
docker run -d \
  --name peernexus \
  --memory=512m \
  --cpus=1.0 \
  -p 8080:8080 \
  -p 6881:6881/udp \
  -e JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -Djava.awt.headless=true" \
  -v peernexus-downloads:/app/downloads \
  -v peernexus-state:/app/state \
  peernexus:latest
```

#### Useful Docker commands

```bash
# Check health
docker inspect --format='{{.State.Health.Status}}' peernexus
curl http://localhost:8080/api/stats

# View logs
docker logs peernexus
docker logs -f peernexus          # follow
docker logs --tail 100 peernexus  # last 100 lines

# Open a shell inside the container
docker exec -it peernexus /bin/sh

# Stop / start / restart
docker stop peernexus
docker start peernexus
docker restart peernexus

# Remove container (preserves named volumes)
docker rm -f peernexus

# Remove container AND volumes
docker rm -f peernexus
docker volume rm peernexus-downloads peernexus-state peernexus-logs
```

#### Environment variables

| Variable | Default | Description |
|---|---|---|
| `JAVA_OPTS` | `-XX:MaxRAMPercentage=75.0 -Djava.awt.headless=true` | JVM flags passed to the process |

#### Exposed ports

| Port | Protocol | Purpose |
|---|---|---|
| `6881` | TCP | BitTorrent peer wire protocol |
| `6881` | UDP | DHT queries + UDP tracker |
| `8080` | TCP | REST API |
| `8081` | TCP | WebSocket stats |

---

### 5. Docker Compose (Staging)

A production-like staging environment is provided via `docker-compose.staging.yml`.

```bash
# Start staging
IMAGE_TAG=peernexus:latest docker compose -f docker-compose.staging.yml up -d

# Check status
docker compose -f docker-compose.staging.yml ps

# View logs
docker compose -f docker-compose.staging.yml logs -f

# Stop
docker compose -f docker-compose.staging.yml down

# Stop and remove volumes
docker compose -f docker-compose.staging.yml down -v
```

The staging compose file pre-configures:
- JSON log driver with rotation (10 MB max, 3 files)
- Health check every 15 s (30 s grace period, 5 retries)
- CPU limit: 1.0 core, memory limit: 512 MB
- Named volumes: `staging-downloads`, `staging-state`, `staging-logs`

---

## CLI Reference

Full list of flags and options:

```
Usage: java -jar jtorrent-gui.jar [command] [options]

COMMANDS
  download <torrent-file> [output-dir]        Download from a .torrent file
  magnet   <magnet-uri>   [output-dir]        Download from a magnet link
  create   <source-dir>   <output.torrent>    Create a new .torrent file
  validate-torrent <torrent-file>             Validate a .torrent file
  announce-test    <torrent-file>             Test tracker announcement
  peer-test                                   Test peer discovery
  storage-test     <torrent-file>             Test storage system
  run-scheduler-test                          Test the scheduler

GENERAL OPTIONS
  -h, --help                    Show this help message and exit
  --version                     Print application version and exit
  -c, --config  <path>          Path to config JSON file (default: config.json)
  -v, --verbose                 Enable verbose / debug output

BANDWIDTH
  --max-upload   <kbps>         Upload rate cap in KB/s   (0 = unlimited)
  --max-download <kbps>         Download rate cap in KB/s (0 = unlimited)

INPUT / OUTPUT
  -i, --input  <path>           Input file or directory
  -o, --output <path>           Output file or directory

TORRENT CREATION (used with `create`)
  --add-tracker <url>           Add an announce URL (repeatable)
  --comment     <text>          Human-readable comment embedded in the torrent
  --piece-size  <bytes>         Piece size: 16384–16777216, 0 = auto-detect
  --private                     Set the private flag (disables DHT/PEX)
  -f, --force                   Overwrite existing output file

SERVER OPTIONS (headless mode)
  --port    <number>            BitTorrent listen port (default: 6881)
  --ws-port <number>            WebSocket stats port   (default: 8081)
```

---

## Configuration

Copy the example and edit to taste:

```bash
cp config.example.json config.json
```

`config.json` reference:

```jsonc
{
  // Network
  "port": 6881,                      // TCP+UDP BitTorrent port
  "listenAddress": "0.0.0.0",        // Bind address (use 0.0.0.0 for all interfaces)
  "maxPeers": 50,                    // Maximum simultaneous peer connections

  // Bandwidth (0 = unlimited)
  "uploadSlots": 4,                  // Concurrent upload slots per torrent
  "maxUploadKBps": 0,
  "maxDownloadKBps": 0,

  // Paths
  "downloadDirectory": "./downloads",
  "stateDirectory": "./state",       // Saved sessions survive restarts

  // Peer discovery
  "dhtEnabled": true,
  "pexEnabled": true,

  // Security
  "encryptionEnabled": false,        // MSE/PE message stream encryption

  // Logging
  "logLevel": "INFO",                // DEBUG | INFO | WARN | ERROR
  "logFile": "./logs/jtorrent.log"
}
```

Then pass it at runtime:

```bash
java -jar target/jtorrent-gui.jar --config config.json
```

---

## Testing

### Unit Tests

```bash
# Run all unit tests
mvn test

# Run a specific test class
mvn test -Dtest=PieceManagerTest

# Run tests matching a pattern
mvn test -Dtest="*Scheduler*"

# Skip unit tests
mvn package -DskipTests
```

### Coverage Report

```bash
mvn clean test jacoco:report
open target/site/jacoco/index.html    # macOS
xdg-open target/site/jacoco/index.html  # Linux
start target/site/jacoco/index.html   # Windows
```

Minimum coverage gates enforced by CI: **18% line coverage**, **16% branch coverage**.

### Integration Tests

Integration tests spin up a real Transmission seeder via Docker and run full download/upload round-trips.

**Linux / macOS:**
```bash
cd test-integration

# Start the Docker seeder environment
./run-integration-tests.sh start

# Run the test suite
./run-integration-tests.sh test

# Stop and clean up
./run-integration-tests.sh stop
./run-integration-tests.sh clean
```

**Windows (PowerShell / CMD):**
```bat
cd test-integration

run-integration-tests.bat start
run-integration-tests.bat test
run-integration-tests.bat stop
run-integration-tests.bat clean
```

**Via Maven (requires Docker running):**
```bash
mvn clean verify            # unit + integration tests
mvn verify -DskipTests=false
```

The test environment exposes:
- Port **6969** — integration tracker
- Port **9091** — Transmission web UI
- Port **51413** — Transmission peer port

### Stress / Performance Tests

Run locally:
```bash
mvn test -Dtest="*Stress*,*Load*,*Performance*" -Dsurefire.timeout=600
```

These also run nightly in CI.

---

## CI/CD Pipeline

```
Push / PR
    │
    ├─► Quality        Maven validate · Checkstyle (Google style)
    ├─► Build & Test   Compile · Unit tests · JaCoCo coverage gate
    ├─► Integration    Docker Transmission seeder · full integration suite
    ├─► Artifact       Fat JAR · CycloneDX SBOM
    └─► Docker*        Multi-stage build · GHCR push · Trivy scan · health verify
                       (* main branch only)

Tag push (v*)
    └─► Release        GitHub Release · versioned Docker tags · SBOM attachment

Nightly (02:00 UTC)
    ├─► Full regression suite
    ├─► Stress & performance tests
    └─► OWASP Dependency-Check (NVD)

Weekly (Sunday 04:00 UTC)
    └─► CodeQL SAST analysis

On failure
    └─► Slack notification or GitHub Issue (label: ci-failure)
```

### Workflow Files

| File | Trigger | Purpose |
|---|---|---|
| `ci.yml` | Push to `main`/`develop`, all PRs | Build, test, Docker image |
| `deploy.yml` | Release event / manual dispatch | Staging deploy + smoke tests |
| `release.yml` | Tag push `v*` | GitHub Release + Docker tags |
| `preview.yml` | PR open/sync | Ephemeral per-PR preview env |
| `nightly.yml` | Cron 02:00 UTC | Regression + stress + OWASP |
| `codeql.yml` | Push, PR, weekly | SAST static analysis |
| `auto-version.yml` | Push to `main` | Semantic versioning from commits |
| `notify.yml` | Nightly / CodeQL failure | Slack / GitHub Issue alert |

### Conventional Commits → Versioning

The `auto-version` workflow derives the next semver from commit message prefixes:

| Prefix | Version bump |
|---|---|
| `feat:` | Minor (`1.0.0` → `1.1.0`) |
| `fix:` | Patch (`1.0.0` → `1.0.1`) |
| `breaking:` | Major (`1.0.0` → `2.0.0`) |
| `[skip-version]` in message | No bump |

### Required Secrets

| Secret | Required | Purpose |
|---|---|---|
| `GITHUB_TOKEN` | Auto-provided | Packages, releases, PR comments |
| `NVD_API_KEY` | Optional | OWASP Dependency-Check rate-limit avoidance |
| `SLACK_WEBHOOK_URL` | Optional | Failure Slack notifications |

---

## Protocol Support

| BEP | Title |
|---|---|
| BEP 3 | The BitTorrent Protocol Specification |
| BEP 5 | DHT Protocol |
| BEP 9 | Extension for Peers to Send Metadata Files |
| BEP 10 | Extension Protocol |
| BEP 11 | Peer Exchange (PEX) |
| BEP 12 | Multitracker Metadata Extension |
| BEP 15 | UDP Tracker Protocol |
| BEP 23 | Tracker Returns Compact Peer Lists |

### Supported Message Types

```
Handshake · KeepAlive · Choke · Unchoke · Interested · NotInterested
Have · Bitfield · Request · Piece · Cancel
Extended handshake · Metadata exchange (ut_metadata)
Port (DHT)
```

---

## Project Structure

```
PeerNexus/
├── .github/
│   ├── workflows/              CI/CD pipeline definitions
│   └── dependabot.yml          Automated dependency updates
│
├── docker/
│   └── Dockerfile              Multi-stage production build
│
├── src/
│   ├── main/java/com/example/jtorrent/
│   │   ├── Main.java                   Application entry point
│   │   ├── automation/                 Download automation and scheduling rules
│   │   ├── cli/                        CLI argument parsing and command dispatch
│   │   │   └── handlers/               One handler class per sub-command
│   │   ├── config/                     Configuration loading and validation
│   │   ├── core/                       Session management, choking, peer scoring
│   │   ├── dht/                        Kademlia DHT implementation
│   │   ├── encryption/                 Message stream encryption (MSE/PE)
│   │   ├── events/                     Internal event bus
│   │   ├── gui/                        JavaFX controllers and components
│   │   ├── logging/                    Structured logging and metrics
│   │   ├── metadata/                   Torrent file creation and info parsing
│   │   ├── network/                    Connection utilities
│   │   ├── parser/                     Bencode encoder/decoder
│   │   ├── peer/                       Peer wire protocol state machine
│   │   ├── persistence/                Session and torrent state serialisation
│   │   ├── plugin/                     Plugin extension points
│   │   ├── proxy/                      SOCKS/HTTP proxy support
│   │   ├── rss/                        RSS feed subscriptions
│   │   ├── scheduler/                  Bandwidth and request scheduling
│   │   ├── search/                     Torrent search integration
│   │   ├── storage/                    Piece management and disk I/O
│   │   ├── streaming/                  Sequential piece selection for playback
│   │   ├── tracker/                    HTTP and UDP tracker clients
│   │   ├── ui/                         Terminal UI
│   │   ├── util/                       Shared utilities
│   │   └── web/                        REST API and WebSocket server
│   │
│   └── test/java/com/example/jtorrent/ Unit tests (64 files)
│
├── test-integration/
│   ├── docker-compose.yml              Transmission seeder for integration tests
│   ├── run-integration-tests.sh        Linux/macOS test runner
│   ├── run-integration-tests.bat       Windows test runner
│   └── test-data/                      Sample torrents and payloads
│
├── terraform/                          Infrastructure-as-code
├── downloads/                          Default download output directory
├── state/                              Persisted session data
├── config.example.json                 Annotated configuration template
├── docker-compose.staging.yml          Staging deployment compose file
└── pom.xml                             Maven project descriptor
```

---

## Contributing

1. Fork the repository and create a feature branch from `main`
2. Write your changes with tests — `mvn clean verify` must pass
3. Follow [Conventional Commits](https://www.conventionalcommits.org/) for your commit messages (the auto-versioner reads them)
4. Open a pull request — CI will run automatically and a preview environment will be deployed

### Development Commands

```bash
# Verify everything (build + unit + integration)
mvn clean verify

# Run only unit tests during active development
mvn test

# Check code style (non-blocking but reported)
mvn checkstyle:check

# Regenerate SBOM
mvn cyclonedx:makeAggregateBom
```

---

## License

MIT License — see [LICENSE](LICENSE) for details.
