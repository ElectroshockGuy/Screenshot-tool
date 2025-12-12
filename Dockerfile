# Multi-Architecture JAR Launcher Builder
# This Dockerfile creates a build environment for cross-compiling
# Go-based JAR launchers with embedded JRE for multiple platforms

FROM golang:1.21-alpine AS builder

# Install required tools
RUN apk add --no-cache \
    bash \
    curl \
    unzip \
    tar \
    gzip \
    git \
    ca-certificates

# Set working directory
WORKDIR /app

# Copy source files
COPY launcher/ ./launcher/
COPY *.jar ./

# Download JREs and build all platforms
RUN cd launcher && chmod +x *.sh && ./download_jre.sh && ./build.sh

# Final stage - just the built artifacts
FROM alpine:latest AS artifacts

COPY --from=builder /app/launcher/dist/ /dist/

CMD ["ls", "-la", "/dist/"]
