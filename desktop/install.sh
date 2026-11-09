#!/usr/bin/env bash
# ══════════════════════════════════════════════════════
#  MadGammon Desktop Installer — Linux / KDE / GNOME
#  Creates .desktop entry, installs icon, registers app
# ══════════════════════════════════════════════════════
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ICON_SRC="${SCRIPT_DIR}/icons/madgammon.png"
DESKTOP_SRC="${SCRIPT_DIR}/madgammon.desktop"
APP_MAIN="${SCRIPT_DIR}/madgammon_desktop.py"
LAUNCHER="${SCRIPT_DIR}/run_madgammon.sh"

ICON_DEST_DIR="$HOME/.local/share/icons/hicolor/256x256/apps"
DESKTOP_DEST_DIR="$HOME/.local/share/applications"

echo "╔══════════════════════════════════════════╗"
echo "║  MadGammon Desktop Installer             ║"
echo "║  Шеш-Беш / BG-AG                        ║"
echo "╚══════════════════════════════════════════╝"
echo ""

# Validate files
for f in "$ICON_SRC" "$DESKTOP_SRC" "$APP_MAIN" "$LAUNCHER"; do
    if [[ ! -f "$f" ]]; then
        echo "❌ Missing file: $f"
        exit 1
    fi
done

# Create directories
echo "📁 Creating directories..."
mkdir -p "$ICON_DEST_DIR"
mkdir -p "$DESKTOP_DEST_DIR"
mkdir -p "$HOME/.local/share/madgammon/logs"

# Install icon (multiple sizes for HiDPI)
echo "🎨 Installing icon..."
cp "$ICON_SRC" "$ICON_DEST_DIR/madgammon.png"

# Also install to 128x128
ICON_128_DIR="$HOME/.local/share/icons/hicolor/128x128/apps"
mkdir -p "$ICON_128_DIR"
if command -v convert &>/dev/null; then
    convert "$ICON_SRC" -resize 128x128 "$ICON_128_DIR/madgammon.png"
    echo "   ✓ 128x128 icon created"
else
    cp "$ICON_SRC" "$ICON_128_DIR/madgammon.png"
fi

# Also install to 48x48
ICON_48_DIR="$HOME/.local/share/icons/hicolor/48x48/apps"
mkdir -p "$ICON_48_DIR"
if command -v convert &>/dev/null; then
    convert "$ICON_SRC" -resize 48x48 "$ICON_48_DIR/madgammon.png"
    echo "   ✓ 48x48 icon created"
else
    cp "$ICON_SRC" "$ICON_48_DIR/madgammon.png"
fi

# Install .desktop file
echo "📋 Installing .desktop entry..."
cp "$DESKTOP_SRC" "$DESKTOP_DEST_DIR/madgammon.desktop"
chmod +x "$DESKTOP_DEST_DIR/madgammon.desktop"

# Make scripts executable
chmod +x "$APP_MAIN"
chmod +x "$LAUNCHER"

# Update desktop database
echo "🔄 Updating desktop database..."
if command -v update-desktop-database &>/dev/null; then
    update-desktop-database "$DESKTOP_DEST_DIR" 2>/dev/null || true
fi

# Update icon cache
echo "🔄 Updating icon cache..."
if command -v gtk-update-icon-cache &>/dev/null; then
    gtk-update-icon-cache "$HOME/.local/share/icons/hicolor/" 2>/dev/null || true
fi

# KDE-specific: notify KDE to refresh
if command -v kbuildsycoca6 &>/dev/null; then
    echo "🔄 Refreshing KDE cache..."
    kbuildsycoca6 --noincremental 2>/dev/null || true
elif command -v kbuildsycoca5 &>/dev/null; then
    kbuildsycoca5 --noincremental 2>/dev/null || true
fi

echo ""
echo "✅ Installation complete!"
echo ""
echo "   🎲 Launch from: Application Menu → Games → MadGammon"
echo "   🖥️  Or run:      bash ${LAUNCHER}"
echo "   📄 Log file:    tail -f ~/.local/share/madgammon/logs/madgammon.log"
echo "   🗑️  Uninstall:   bash ${SCRIPT_DIR}/uninstall.sh"
echo ""
