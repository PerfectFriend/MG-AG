#!/usr/bin/env bash
# ══════════════════════════════════════════════════════
#  MadGammon Launcher with Environment Check & Logging
# ══════════════════════════════════════════════════════
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_DIR="$HOME/.local/share/madgammon/logs"
mkdir -p "$LOG_DIR"
LAUNCHER_LOG="$LOG_DIR/launcher.log"

echo "[$(date '+%Y-%m-%d %H:%M:%S')] Starting MadGammon Launcher..." | tee -a "$LAUNCHER_LOG"

# Pick Python interpreter that has PyQt6
PYTHON_BIN=""
if /usr/bin/python3 -c "import PyQt6" &>/dev/null; then
    PYTHON_BIN="/usr/bin/python3"
elif python3 -c "import PyQt6" &>/dev/null; then
    PYTHON_BIN="python3"
elif /home/thomas/build/bin/python3 -c "import sys; sys.path.append('/usr/lib/python3/dist-packages'); import PyQt6" &>/dev/null; then
    PYTHON_BIN="/home/thomas/build/bin/python3"
else
    echo "[ERROR] No Python interpreter with PyQt6 found!" | tee -a "$LAUNCHER_LOG"
    exit 1
fi

echo "[$(date '+%Y-%m-%d %H:%M:%S')] Using Python: $PYTHON_BIN" | tee -a "$LAUNCHER_LOG"
echo "[$(date '+%Y-%m-%d %H:%M:%S')] DISPLAY=$DISPLAY, WAYLAND_DISPLAY=$WAYLAND_DISPLAY" | tee -a "$LAUNCHER_LOG"

cd "$SCRIPT_DIR"
exec "$PYTHON_BIN" "$SCRIPT_DIR/madgammon_desktop.py" "$@" 2>&1 | tee -a "$LOG_DIR/madgammon.log"
