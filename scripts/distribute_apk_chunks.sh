#!/usr/bin/env bash
set -euo pipefail

# ==============================================================================
# Script: distribute_apk_chunks.sh
# Purpose: Automatically package and split large APKs (>32MB) into 25MB chunks
#          to bypass Google Cloud Run HTTP response payload limitations (32MB).
# ==============================================================================

SOURCE_APK="app/build/outputs/apk/debug/app-debug.apk"
if [ ! -f "$SOURCE_APK" ]; then
    if [ -f "app-debug.apk" ]; then
        SOURCE_APK="app-debug.apk"
    elif [ -f ".build-outputs/app-debug.apk" ]; then
        SOURCE_APK=".build-outputs/app-debug.apk"
    else
        echo "Error: No APK file found to distribute!"
        exit 1
    fi
fi

echo "==> Preparing APK distribution from: $SOURCE_APK"
cp "$SOURCE_APK" app-debug.apk
cp "$SOURCE_APK" MadGammon.apk
mkdir -p .build-outputs
cp "$SOURCE_APK" .build-outputs/app-debug.apk

# Clean old parts
rm -f MadGammon.apk.part_*

# Split into 25MB chunks
echo "==> Splitting into 25MB parts for Cloud Run streaming..."
split -b 25M MadGammon.apk MadGammon.apk.part_

# Compute and verify MD5
ORIGINAL_MD5=$(md5sum MadGammon.apk | awk '{print $1}')
COMBINED_MD5=$(cat MadGammon.apk.part_* | md5sum | awk '{print $1}')

if [ "$ORIGINAL_MD5" != "$COMBINED_MD5" ]; then
    echo "Error: MD5 mismatch after chunking!"
    exit 2
fi

echo "==> Verification SUCCESSFUL!"
echo "    Original MD5: $ORIGINAL_MD5"
echo "    Combined MD5: $COMBINED_MD5"
ls -lh MadGammon.apk.part_* MadGammon.apk

# Ensure Python HTTP server is running on port 3000
if ! curl -s -I http://localhost:3000/MadGammon.apk.part_aa >/dev/null 2>&1; then
    echo "==> Starting background distribution server on port 3000..."
    nohup python3 -m http.server 3000 >/dev/null 2>&1 &
    sleep 1
fi

echo "==> All chunks ready and verified for Cloud Run distribution!"
