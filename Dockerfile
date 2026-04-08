# =============================================================================
#  DITA Specialization Designer — Dockerfile (headless build image)
#
#  This image is for CI/CD environments:
#    • Compiles the project
#    • Runs all tests
#    • Produces artefacts in /app/build/
#
#  NOTE: JavaFX requires a display for the UI. This image is NOT intended
#  for running the desktop application; use it only to build and test.
#  For desktop deployment use the Gradle distribution ZIP instead.
# =============================================================================

FROM eclipse-temurin:21-jdk-jammy AS builder

LABEL maintainer="ditadesigner"
LABEL description="DITA Specialization Designer — CI build image"

# Set working directory
WORKDIR /app

# Copy Gradle wrapper and build definitions first (layer cache)
COPY gradlew            ./gradlew
COPY gradlew.bat        ./gradlew.bat
COPY gradle/            ./gradle/
COPY build.gradle       ./build.gradle
COPY settings.gradle    ./settings.gradle

# Download dependencies (cached as long as build files don't change)
RUN chmod +x gradlew && \
    ./gradlew dependencies --no-daemon --quiet 2>/dev/null || true

# Copy source code
COPY src/ ./src/
COPY README.md ./README.md

# Compile
RUN ./gradlew compileJava compileTestJava --no-daemon

# Run tests (headless — no JavaFX UI)
RUN ./gradlew test --no-daemon \
    -Djava.awt.headless=true \
    -Dtestfx.robot=glass \
    -Dtestfx.headless=true \
    -Dprism.order=sw \
    -Dprism.verbose=false

# Build all artefacts
RUN ./gradlew jar shadowJar distZip distTar javadoc --no-daemon

# ── Output stage ─────────────────────────────────────────────────────────────
FROM scratch AS artefacts

# Export built artefacts
COPY --from=builder /app/build/libs/          /libs/
COPY --from=builder /app/build/distributions/ /distributions/
COPY --from=builder /app/build/docs/          /docs/
COPY --from=builder /app/build/reports/       /reports/

# =============================================================================
#  Build commands:
#
#    docker build -t dita-designer:build .
#
#  Extract artefacts after build:
#    docker create --name dita-tmp dita-designer:build
#    docker cp dita-tmp:/app/build/libs/ ./dist/
#    docker cp dita-tmp:/app/build/distributions/ ./dist/
#    docker rm dita-tmp
# =============================================================================
