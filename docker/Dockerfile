# =============================================================================
# PeerNexus / JTorrent — Production Multi-Stage Dockerfile
# =============================================================================
# Stage 1: Maven build with dependency caching
# Stage 2: Slim JRE 21 runtime (minimal attack surface)
# =============================================================================

# ---------------------------------------------------------------------------
# Stage 1 — Build
# ---------------------------------------------------------------------------
FROM maven:3.9.9-eclipse-temurin-21 AS builder

WORKDIR /build

# -- Dependency caching layer ------------------------------------------------
# Copy only POM first so dependencies are cached independently of source code.
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn dependency:go-offline -B -q

# -- Source compilation ------------------------------------------------------
COPY src ./src
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn clean package -B -q -DskipTests \
    && mv target/jtorrent-gui.jar /build/app.jar

# ---------------------------------------------------------------------------
# Stage 2 — Runtime
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-jammy AS runtime

# Security: run as non-root
RUN groupadd --gid 1000 appuser \
    && useradd --uid 1000 --gid appuser --shell /bin/false --create-home appuser

# Install curl for container health checks.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy only the fat JAR from the build stage
COPY --from=builder /build/app.jar app.jar

# Create directories the application expects
RUN mkdir -p downloads state logs \
    && chown -R appuser:appuser /app

USER appuser

# Ports:
#   6881  — BitTorrent protocol (TCP + UDP)
#   8080  — REST API
#   8081  — WebSocket stats
EXPOSE 6881 6881/udp 8080 8081

# Health check against the REST API
HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
    CMD curl -sf http://localhost:8080/api/stats || exit 1

# Launch HeadlessServer directly — avoids Application.launch() and JavaFX Glass init.
ENTRYPOINT ["java", \
    "-XX:+UseG1GC", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.awt.headless=true", \
    "-cp", "app.jar", \
    "com.example.jtorrent.web.HeadlessServer"]

CMD ["--port", "8080", "--ws-port", "8081"]
