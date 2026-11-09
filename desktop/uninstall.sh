#!/usr/bin/env bash
# MadGammon Desktop Uninstaller
set -euo pipefail

echo "🗑️  Uninstalling MadGammon Desktop..."

rm -f "$HOME/.local/share/applications/madgammon.desktop"
rm -f "$HOME/.local/share/icons/hicolor/256x256/apps/madgammon.png"
rm -f "$HOME/.local/share/icons/hicolor/128x128/apps/madgammon.png"
rm -f "$HOME/.local/share/icons/hicolor/48x48/apps/madgammon.png"

if command -v update-desktop-database &>/dev/null; then
    update-desktop-database "$HOME/.local/share/applications" 2>/dev/null || true
fi
if command -v gtk-update-icon-cache &>/dev/null; then
    gtk-update-icon-cache "$HOME/.local/share/icons/hicolor/" 2>/dev/null || true
fi

echo "✅ MadGammon Desktop uninstalled."
