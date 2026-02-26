# PeerNexus

A modern BitTorrent client implementation in Java with JavaFX GUI support.

## Features

- **Core Protocol**: Full BitTorrent protocol implementation with piece selection, choking algorithms, and endgame mode
- **DHT Support**: Distributed hash table for trackerless peer discovery
- **Magnet Links**: Direct support for magnet URIs without .torrent files
- **Tracker Support**: HTTP and UDP tracker protocols
- **PEX (Peer Exchange)**: Decentralized peer discovery
- **Bandwidth Management**: Upload/download rate limiting with token bucket algorithm
- **Smart Piece Selection**: Rarest-first with random first piece strategy
- **Resume Support**: Persistent download state across sessions
- **JavaFX GUI**: Modern graphical interface with real-time statistics
- **CLI Interface**: Command-line tools for automation and testing
- **UPnP**: Automatic port forwarding configuration

## Requirements

- Java 21 or higher
- Maven 3.8+

## Building

```bash
mvn clean package
```

This creates `target/jtorrent-gui.jar` - a standalone executable JAR with all dependencies.

## Running

### GUI Mode

```bash
java -jar target/jtorrent-gui.jar
```

Or simply double-click the JAR file.

### CLI Mode

```bash
# Download a torrent
java -jar target/jtorrent-gui.jar download <torrent-file> [output-dir]

# Create a torrent
java -jar target/jtorrent-gui.jar create <directory> <output-file> [announce-url]

# Download from magnet link
java -jar target/jtorrent-gui.jar magnet <magnet-uri> [output-dir]
```

## Configuration

Copy `config.example.json` to `config.json` and adjust settings:

```json
{
  "port": 6881,
  "maxPeers": 50,
  "maxUploadKBps": 0,
  "maxDownloadKBps": 0,
  "downloadDirectory": "./downloads",
  "dhtEnabled": true,
  "pexEnabled": true,
  "encryptionEnabled": false
}
```

Set bandwidth limits to `0` for unlimited.

## Testing

```bash
# Run unit tests
mvn test

# Run with coverage
mvn clean test jacoco:report

# Run integration tests (requires Docker)
cd test-integration
./run-integration-tests.sh  # Linux/macOS
run-integration-tests.bat   # Windows
```

Coverage reports: `target/site/jacoco/index.html`

## CI/CD

The project includes a comprehensive CI/CD system with:

- **Continuous Integration**: Build, test, coverage, integration tests, SBOM generation
- **Security Scanning**: CodeQL SAST, OWASP dependency check, Gitleaks, SpotBugs, Trivy container scanning
- **Release Automation**: Automatic semantic versioning from conventional commits, tag-triggered releases
- **Docker**: Multi-stage builds, GHCR publishing, health verification, vulnerability scanning
- **Preview Environments**: Ephemeral Docker deployments per Pull Request
- **Deployment**: Staging pipeline with smoke tests and automatic rollback
- **Notifications**: Slack or GitHub Issue alerts on pipeline failures
- **Nightly**: Full regression suite, stress tests, performance benchmarking
- **Observability**: Health checks, structured logging readiness, Prometheus metrics roadmap

See [CI-CD.md](CI-CD.md), [docker.md](docker.md), and [OBSERVABILITY.md](OBSERVABILITY.md) for full details.

## Project Structure

```
src/main/java/com/example/jtorrent/
├── cli/              # Command-line interface
├── config/           # Configuration handling
├── core/             # Core session management
├── dht/              # DHT implementation
├── gui/              # JavaFX interface
├── logging/          # Logging and metrics
├── metadata/         # Torrent creation and metadata fetching
├── parser/           # Bencode parser
├── peer/             # Peer wire protocol
├── persistence/      # State persistence
├── scheduler/        # Bandwidth and peer scheduling
├── storage/          # Piece and disk management
├── tracker/          # Tracker communication
├── ui/               # Terminal UI
└── util/             # Utilities

src/test/java/        # Unit tests
test-integration/     # Docker-based integration tests
```

## Protocol Support

### BitTorrent Protocol

- BEP 3: The BitTorrent Protocol Specification
- BEP 5: DHT Protocol
- BEP 9: Extension for Peers to Send Metadata Files
- BEP 10: Extension Protocol
- BEP 11: Peer Exchange (PEX)
- BEP 12: Multitracker Metadata Extension
- BEP 15: UDP Tracker Protocol
- BEP 23: Tracker Returns Compact Peer Lists

### Message Types

- Handshake, KeepAlive, Choke, Unchoke, Interested, NotInterested
- Have, Bitfield, Request, Piece, Cancel
- Extended handshake, metadata exchange
- Port (DHT)

## License

MIT License - see LICENSE file for details.

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes with tests
4. Ensure `mvn clean verify` passes
5. Submit a pull request

## Status

**Current Version**: 1.0.0-SNAPSHOT (Active Development)

Core protocol implementation is complete. GUI features and advanced optimizations are ongoing.
