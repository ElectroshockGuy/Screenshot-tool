#!/bin/bash

# Build script for cross-platform Java launcher
# This script builds native executables for each platform with embedded JRE
# Supports: Linux (amd64, arm64), Windows (amd64, arm64), macOS (amd64, arm64)
#
# Usage:
#   ./build.sh                          # Build all platforms
#   ./build.sh linux amd64              # Build single platform
#   ./build.sh --version v1.2.3         # Set version for all platforms
#   ./build.sh linux amd64 --version v1.2.3  # Single platform with version

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
OUTPUT_DIR="$SCRIPT_DIR/dist"
EMBED_DIR="$SCRIPT_DIR/embed"
JRE_DIR="$SCRIPT_DIR/jre"

APP_NAME="springboot-swing"
VERSION="dev"
SINGLE_GOOS=""
SINGLE_GOARCH=""

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --version|-v)
            VERSION="$2"
            shift 2
            ;;
        linux|windows|darwin)
            SINGLE_GOOS="$1"
            shift
            ;;
        amd64|arm64)
            SINGLE_GOARCH="$1"
            shift
            ;;
        *)
            echo "Unknown argument: $1"
            exit 1
            ;;
    esac
done

# Find JAR file
JAR_FILE=""
if [ -f "$EMBED_DIR/app.jar" ]; then
    JAR_FILE="$EMBED_DIR/app.jar"
elif [ -f "$PROJECT_ROOT/springboot-swing-1.0.0.jar" ]; then
    JAR_FILE="$PROJECT_ROOT/springboot-swing-1.0.0.jar"
elif [ -f "$PROJECT_ROOT/$APP_NAME-1.0.0.jar" ]; then
    JAR_FILE="$PROJECT_ROOT/$APP_NAME-1.0.0.jar"
fi

# Check if JAR file exists
if [ -z "$JAR_FILE" ] || [ ! -f "$JAR_FILE" ]; then
    echo "Error: JAR file not found"
    echo "Searched: $EMBED_DIR/app.jar, $PROJECT_ROOT/springboot-swing-1.0.0.jar"
    exit 1
fi

echo "Using JAR: $JAR_FILE"
echo "Version: $VERSION"

# Check if JRE files exist (skip in single platform mode if embed/jre.tar.gz exists)
if [ -n "$SINGLE_GOOS" ] && [ -f "$EMBED_DIR/jre.tar.gz" ]; then
    echo "Using pre-configured JRE from embed directory"
elif [ ! -d "$JRE_DIR" ]; then
    echo "Error: JRE directory not found. Run ./download_jre.sh first"
    exit 1
fi

# Create output directory
mkdir -p "$OUTPUT_DIR"
mkdir -p "$EMBED_DIR"

# Copy JAR to embed directory if not already there
if [ "$JAR_FILE" != "$EMBED_DIR/app.jar" ]; then
    cp "$JAR_FILE" "$EMBED_DIR/app.jar"
fi

# Build for each platform
build_platform() {
    local GOOS=$1
    local GOARCH=$2
    local JRE_FILE=$3
    local EXT=$4

    echo ""
    echo "=========================================="
    echo "Building for $GOOS/$GOARCH (version: $VERSION)..."
    echo "=========================================="

    # Check if JRE is already in embed dir (CI mode)
    if [ ! -f "$EMBED_DIR/jre.tar.gz" ]; then
        # Check if JRE file exists
        if [ ! -f "$JRE_DIR/$JRE_FILE" ]; then
            echo "Warning: JRE file not found: $JRE_DIR/$JRE_FILE, skipping..."
            return 0
        fi
        # Copy JRE to embed directory
        cp "$JRE_DIR/$JRE_FILE" "$EMBED_DIR/jre.tar.gz"
        local CLEANUP_JRE=1
    else
        local CLEANUP_JRE=0
    fi

    # Build the executable
    local OUTPUT_NAME="${APP_NAME}-${GOOS}-${GOARCH}${EXT}"
    cd "$SCRIPT_DIR"

    CGO_ENABLED=0 GOOS=$GOOS GOARCH=$GOARCH go build \
        -ldflags="-s -w -X main.Version=$VERSION" \
        -o "$OUTPUT_DIR/$OUTPUT_NAME" .

    # Clean up JRE if we copied it
    if [ "$CLEANUP_JRE" = "1" ]; then
        rm "$EMBED_DIR/jre.tar.gz"
    fi

    echo "Built: $OUTPUT_DIR/$OUTPUT_NAME"
    ls -lh "$OUTPUT_DIR/$OUTPUT_NAME"
}

# ==========================================
# Build platforms
# ==========================================

# Function to get JRE filename for a platform
get_jre_file() {
    local GOOS=$1
    local GOARCH=$2
    case "$GOOS-$GOARCH" in
        linux-amd64) echo "jre-linux-amd64.tar.gz" ;;
        linux-arm64) echo "jre-linux-arm64.tar.gz" ;;
        windows-amd64) echo "jre-windows-amd64.tar.gz" ;;
        windows-arm64) echo "jre-windows-arm64.tar.gz" ;;
        darwin-amd64) echo "jre-macos-amd64.tar.gz" ;;
        darwin-arm64) echo "jre-macos-arm64.tar.gz" ;;
        *) echo "" ;;
    esac
}

# Function to get file extension for a platform
get_ext() {
    local GOOS=$1
    case "$GOOS" in
        windows) echo ".exe" ;;
        *) echo "" ;;
    esac
}

# Single platform or all platforms?
if [ -n "$SINGLE_GOOS" ] && [ -n "$SINGLE_GOARCH" ]; then
    # Single platform build (for CI matrix)
    echo ""
    echo "=========================================="
    echo "Building single platform: $SINGLE_GOOS/$SINGLE_GOARCH"
    echo "=========================================="
    
    JRE_FILE=$(get_jre_file "$SINGLE_GOOS" "$SINGLE_GOARCH")
    EXT=$(get_ext "$SINGLE_GOOS")
    
    if [ -z "$JRE_FILE" ]; then
        echo "Error: Unknown platform $SINGLE_GOOS/$SINGLE_GOARCH"
        exit 1
    fi
    
    build_platform "$SINGLE_GOOS" "$SINGLE_GOARCH" "$JRE_FILE" "$EXT"
else
    # Build all platforms
    echo ""
    echo "=========================================="
    echo "Starting multi-platform build..."
    echo "=========================================="

    # Linux
    build_platform "linux" "amd64" "jre-linux-amd64.tar.gz" ""
    build_platform "linux" "arm64" "jre-linux-arm64.tar.gz" ""

    # Windows
    build_platform "windows" "amd64" "jre-windows-amd64.tar.gz" ".exe"
    build_platform "windows" "arm64" "jre-windows-arm64.tar.gz" ".exe"

    # macOS
    build_platform "darwin" "amd64" "jre-macos-amd64.tar.gz" ""
    build_platform "darwin" "arm64" "jre-macos-arm64.tar.gz" ""

    # Clean up embed directory
    rm -f "$EMBED_DIR/app.jar"

    # Generate checksums
    echo ""
    echo "Generating checksums..."
    cd "$OUTPUT_DIR"
    if command -v sha256sum &> /dev/null; then
        sha256sum * > checksums.txt
    elif command -v shasum &> /dev/null; then
        shasum -a 256 * > checksums.txt
    fi

    if [ -f checksums.txt ]; then
        echo "Checksums saved to: $OUTPUT_DIR/checksums.txt"
        cat checksums.txt
    fi
fi

echo ""
echo "=========================================="
echo "Build complete!"
echo "=========================================="
echo "Output files:"
ls -lh "$OUTPUT_DIR"

