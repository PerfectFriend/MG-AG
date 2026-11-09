# Mandatory Skill & Procedure: Large APK Web Distribution via Cloud Run

## The Core Rule (CRITICAL)
In Google AI Studio Build, the development preview environment runs on **Google Cloud Run (Google Frontend / GFE)**.
* **Google Cloud Run has a strict 32 MB response payload limit.**
* Any single HTTP response greater than 32 MB causes Cloud Run to terminate the connection and return:
  `HTTP 500 Internal Server Error` (or `HTTP 502/504`).
* Our full-stack APK (`MadGammon.apk`) contains complete native binaries (Tor, Xray, tun2socks, OpenCV/MLKit) for multiple architectures, resulting in an APK size of **~78 MB**.

## The Permanent Standard Procedure
Whenever a new APK build is assembled, the agent **MUST ALWAYS**:
1. Copy the assembled APK to:
   - `/MadGammon.apk`
   - `/app-debug.apk`
   - `/.build-outputs/app-debug.apk`
2. Run `scripts/distribute_apk_chunks.sh` which:
   - Splits `MadGammon.apk` into parts no larger than 25 MB:
     `MadGammon.apk.part_aa` (25MB)
     `MadGammon.apk.part_ab` (25MB)
     `MadGammon.apk.part_ac` (25MB)
     `MadGammon.apk.part_ad` (~2.7MB)
   - Verifies MD5 checksum parity between original and concatenated parts.
   - Ensures the local Python HTTP server on port 3000 is running.
3. Maintain `/index.html` with the automated in-browser chunk downloader and blob assembler:
   - Downloads each chunk sequentially via `fetch('/MadGammon.apk.part_*')`.
   - Combines buffers into a single `Blob` with MIME `application/vnd.android.package-archive`.
   - Triggers native 1-click browser download of `MadGammon.apk`.
   - Shows progress bar (25% -> 50% -> 75% -> 100%) and direct links for manual downloading.

## Execution Command
```bash
./scripts/distribute_apk_chunks.sh
```
