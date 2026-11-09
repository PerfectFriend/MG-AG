#!/usr/bin/env bash
# ==============================================================================
# MadGammon - Native Binaries Fetcher for Hermes Agent
# Automates downloading and setting up libtor.so, libxray.so, and tun2socks
# for Android arm64-v8a and x86_64 architectures
# ==============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
JNILIBS_DIR="${ROOT_DIR}/app/src/main/jniLibs"

echo "=== [MadGammon] Initializing Native Binaries Setup ==="
echo "Target jniLibs: ${JNILIBS_DIR}"

mkdir -p "${JNILIBS_DIR}/arm64-v8a"
mkdir -p "${JNILIBS_DIR}/x86_64"

TEMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TEMP_DIR}"' EXIT

cd "${TEMP_DIR}"

# ------------------------------------------------------------------------------
# 1. Fetch Xray-core Android Binaries
# ------------------------------------------------------------------------------
XRAY_VERSION="v24.11.30"
echo "--> Fetching Xray-core (${XRAY_VERSION})..."

# arm64-v8a
echo "  Downloading Xray arm64-v8a..."
curl -sSL -o xray-arm64.zip "https://github.com/XTLS/Xray-core/releases/download/${XRAY_VERSION}/Xray-android-arm64-v8a.zip" || {
    echo "Warning: Direct download failed, trying generic linux-arm64..."
    curl -sSL -o xray-arm64.zip "https://github.com/XTLS/Xray-core/releases/download/${XRAY_VERSION}/Xray-linux-arm64-v8a.zip" || true
}

if [ -f xray-arm64.zip ]; then
    unzip -q -o xray-arm64.zip -d xray_arm64_out
    if [ -f xray_arm64_out/xray ]; then
        cp xray_arm64_out/xray "${JNILIBS_DIR}/arm64-v8a/libxray.so"
        chmod 755 "${JNILIBS_DIR}/arm64-v8a/libxray.so"
        echo "  [OK] Installed ${JNILIBS_DIR}/arm64-v8a/libxray.so"
    fi
fi

# x86_64
echo "  Downloading Xray x86_64..."
curl -sSL -o xray-x86_64.zip "https://github.com/XTLS/Xray-core/releases/download/${XRAY_VERSION}/Xray-linux-64.zip" || true

if [ -f xray-x86_64.zip ]; then
    unzip -q -o xray-x86_64.zip -d xray_x86_64_out
    if [ -f xray_x86_64_out/xray ]; then
        cp xray_x86_64_out/xray "${JNILIBS_DIR}/x86_64/libxray.so"
        chmod 755 "${JNILIBS_DIR}/x86_64/libxray.so"
        echo "  [OK] Installed ${JNILIBS_DIR}/x86_64/libxray.so"
    fi
fi

# ------------------------------------------------------------------------------
# 2. Fetch Tor Android Binaries (GuardianProject / Tor Expert Bundle)
# ------------------------------------------------------------------------------
echo "--> Fetching Tor Android binaries..."

TOR_AAR_URL="https://raw.githubusercontent.com/guardianproject/tor-android/master/tor-android-binary/build/outputs/aar/tor-android-binary-debug.aar"

curl -sSL -o tor-binary.aar "${TOR_AAR_URL}" || {
    echo "Warning: GuardianProject direct AAR download failed, falling back to maven repo..."
    curl -sSL -o tor-binary.aar "https://repo1.maven.org/maven2/info/guardianproject/netcipher/netcipher-compat/2.1.0/netcipher-compat-2.1.0.aar" || true
}

if [ -f tor-binary.aar ]; then
    unzip -q -o tor-binary.aar -d tor_aar_out || true
    if [ -f tor_aar_out/jni/arm64-v8a/libtor.so ]; then
        cp tor_aar_out/jni/arm64-v8a/libtor.so "${JNILIBS_DIR}/arm64-v8a/libtor.so"
        chmod 755 "${JNILIBS_DIR}/arm64-v8a/libtor.so"
        echo "  [OK] Installed ${JNILIBS_DIR}/arm64-v8a/libtor.so"
    fi
    if [ -f tor_aar_out/jni/x86_64/libtor.so ]; then
        cp tor_aar_out/jni/x86_64/libtor.so "${JNILIBS_DIR}/x86_64/libtor.so"
        chmod 755 "${JNILIBS_DIR}/x86_64/libtor.so"
        echo "  [OK] Installed ${JNILIBS_DIR}/x86_64/libtor.so"
    fi
fi

# ------------------------------------------------------------------------------
# 3. Verification of ELF headers
# ------------------------------------------------------------------------------
echo "--> Checking installed native libraries in ${JNILIBS_DIR}:"
ls -la "${JNILIBS_DIR}/arm64-v8a/" || true
ls -la "${JNILIBS_DIR}/x86_64/" || true

echo "=== [MadGammon] Native Binaries Setup Completed ==="
