#!/usr/bin/env bash
set -euo pipefail

# ==============================================================================
# Script: build_rust_core.sh
# Purpose: Build native Rust core library and generate UniFFI Kotlin bindings
# Target ABIs: arm64-v8a, x86_64
# ==============================================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"
RUST_DIR="$ROOT_DIR/native/madgammon-core"
JNI_LIBS_DIR="$ROOT_DIR/app/src/main/jniLibs"
KOTLIN_BINDINGS_DIR="$ROOT_DIR/app/src/main/java/uniffi/madgammon_core"

echo "==> [MadGammon Core] Starting Rust build pipeline..."

# Check cargo
if ! command -v cargo &> /dev/null; then
    echo "Error: cargo is not installed or not in PATH."
    echo "Install Rust via: curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh"
    exit 1
fi

# Ensure cargo-ndk is installed
if ! command -v cargo-ndk &> /dev/null; then
    echo "==> Installing cargo-ndk..."
    cargo install cargo-ndk
fi

cd "$RUST_DIR"

# 1. Run native tests first
echo "==> Running native host unit tests..."
cargo test

# 2. Build for target Android ABIs
echo "==> Compiling for aarch64-linux-android (arm64-v8a)..."
cargo ndk -t arm64-v8a -o "$JNI_LIBS_DIR" build --release

echo "==> Compiling for x86_64-linux-android (x86_64)..."
cargo ndk -t x86_64 -o "$JNI_LIBS_DIR" build --release

# 3. Generate UniFFI Kotlin Bindings
echo "==> Generating UniFFI Kotlin bindings..."
mkdir -p "$KOTLIN_BINDINGS_DIR"
cargo run --bin uniffi-bindgen generate src/madgammon_core.udl \
    --language kotlin \
    --out-dir "$KOTLIN_BINDINGS_DIR"

echo "==> Build successful! Native libraries deployed to $JNI_LIBS_DIR"
