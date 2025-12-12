#!/bin/bash

# Download JRE script for all supported platforms
# Using Eclipse Temurin (Adoptium) JRE 21 LTS
# For Windows ARM64, using Microsoft OpenJDK 21

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
JRE_DIR="$SCRIPT_DIR/jre"

mkdir -p "$JRE_DIR"

echo "Downloading JRE for all supported platforms..."

# Function to download with retry
download_with_retry() {
    local url=$1
    local output=$2
    local max_retries=3
    local retry=0

    # Skip if file already exists
    if [ -f "$output" ]; then
        echo "File already exists: $output, skipping..."
        return 0
    fi

    while [ $retry -lt $max_retries ]; do
        echo "Downloading $output (attempt $((retry+1))/$max_retries)..."
        if curl -L --connect-timeout 30 --max-time 600 -o "$output" "$url"; then
            echo "Download successful: $output"
            return 0
        fi
        retry=$((retry+1))
        echo "Retry in 5 seconds..."
        sleep 5
    done

    echo "Failed to download $output after $max_retries attempts"
    return 1
}

# Convert zip to tar.gz
convert_zip_to_targz() {
    local zip_file=$1
    local targz_file=$2
    local temp_dir="$JRE_DIR/temp_extract_$$"

    if [ -f "$targz_file" ]; then
        echo "Target already exists: $targz_file"
        rm -f "$zip_file"
        return 0
    fi

    echo "Converting $zip_file to tar.gz format..."
    mkdir -p "$temp_dir"
    unzip -q "$zip_file" -d "$temp_dir"
    cd "$temp_dir"
    tar czf "$targz_file" *
    cd "$JRE_DIR"
    rm -rf "$temp_dir" "$zip_file"
    echo "Converted: $targz_file"
}

echo ""
echo "=========================================="
echo "Downloading JREs for all platforms..."
echo "=========================================="

# ==========================================
# Linux AMD64 - Temurin 21
# ==========================================
echo ""
echo "==> [1/6] Downloading Linux AMD64 JRE..."
download_with_retry \
    "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.5%2B11/OpenJDK21U-jre_x64_linux_hotspot_21.0.5_11.tar.gz" \
    "$JRE_DIR/jre-linux-amd64.tar.gz"

# ==========================================
# Linux ARM64 - Temurin 21
# ==========================================
echo ""
echo "==> [2/6] Downloading Linux ARM64 JRE..."
download_with_retry \
    "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.5%2B11/OpenJDK21U-jre_aarch64_linux_hotspot_21.0.5_11.tar.gz" \
    "$JRE_DIR/jre-linux-arm64.tar.gz"

# ==========================================
# Windows AMD64 - Temurin 21
# ==========================================
echo ""
echo "==> [3/6] Downloading Windows AMD64 JRE..."
download_with_retry \
    "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.5%2B11/OpenJDK21U-jre_x64_windows_hotspot_21.0.5_11.zip" \
    "$JRE_DIR/jre-windows-amd64.zip"

if [ -f "$JRE_DIR/jre-windows-amd64.zip" ]; then
    convert_zip_to_targz "$JRE_DIR/jre-windows-amd64.zip" "$JRE_DIR/jre-windows-amd64.tar.gz"
fi

# ==========================================
# Windows ARM64 - Microsoft OpenJDK 21
# (Temurin doesn't provide Windows ARM64 JRE)
# ==========================================
echo ""
echo "==> [4/6] Downloading Windows ARM64 JRE (Microsoft OpenJDK)..."
download_with_retry \
    "https://aka.ms/download-jdk/microsoft-jdk-21.0.5-windows-aarch64.zip" \
    "$JRE_DIR/jre-windows-arm64.zip"

if [ -f "$JRE_DIR/jre-windows-arm64.zip" ]; then
    convert_zip_to_targz "$JRE_DIR/jre-windows-arm64.zip" "$JRE_DIR/jre-windows-arm64.tar.gz"
fi

# ==========================================
# macOS AMD64 (Intel) - Temurin 21
# ==========================================
echo ""
echo "==> [5/6] Downloading macOS AMD64 JRE..."
download_with_retry \
    "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.5%2B11/OpenJDK21U-jre_x64_mac_hotspot_21.0.5_11.tar.gz" \
    "$JRE_DIR/jre-macos-amd64.tar.gz"

# ==========================================
# macOS ARM64 (Apple Silicon) - Temurin 21
# ==========================================
echo ""
echo "==> [6/6] Downloading macOS ARM64 JRE..."
download_with_retry \
    "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.5%2B11/OpenJDK21U-jre_aarch64_mac_hotspot_21.0.5_11.tar.gz" \
    "$JRE_DIR/jre-macos-arm64.tar.gz"

echo ""
echo "=========================================="
echo "All JRE downloads complete!"
echo "=========================================="
echo "Files saved to: $JRE_DIR"
ls -lh "$JRE_DIR"
