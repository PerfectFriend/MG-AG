#!/usr/bin/env python3
"""
MadGammon APK Uploader to Telegram
Uploads built APK files to a Telegram group or channel.
Usage:
    export TELEGRAM_BOT_TOKEN="your_bot_token"
    python3 desktop/upload_to_telegram.py [/path/to/app.apk]
"""
import sys
import os
import requests

DEFAULT_CHAT_ID = -1004431090317  # Private supergroup 4431090317
DEFAULT_THREAD_ID = 23616          # Topic / Thread ID 23616
DEFAULT_APK_PATH = os.path.expanduser("~/apkexport/app-debug.apk")


def upload_apk(token: str, apk_path: str, chat_id: int = DEFAULT_CHAT_ID, thread_id: int = DEFAULT_THREAD_ID):
    if not os.path.exists(apk_path):
        print(f"[-] Error: APK file not found at {apk_path}")
        return False

    file_size_mb = os.path.getsize(apk_path) / (1024 * 1024)
    print(f"[*] Preparing to upload: {apk_path} ({file_size_mb:.2f} MB)")
    print(f"[*] Destination: chat_id={chat_id}, thread_id={thread_id}")

    url = f"https://api.telegram.org/bot{token}/sendDocument"

    caption = (
        "🎲 <b>MadGammon v0.2.0-sovereign-alpha</b>\n\n"
        "• ⚡ Rust Native Core (UniFFI / libmadgammon_core.so)\n"
        "• 🛡️ Signal Double Ratchet + 16KB constant-size padding\n"
        "• 🎲 Mental Dice provably fair commit-reveal consensus\n"
        "• 🧩 Reed-Solomon GF(2^8) Erasure Coded Sharding\n"
        "• 🔐 BIP-39 12/24 word seed derivation\n"
        "• 🌐 P2P Game Protocol & Tor/Direct Networking\n\n"
        "<i>Собрано для Android 8.0+ (ARM64 / x86_64)</i>"
    )

    data = {
        "chat_id": chat_id,
        "message_thread_id": thread_id,
        "caption": caption,
        "parse_mode": "HTML"
    }

    try:
        with open(apk_path, "rb") as f:
            files = {"document": (os.path.basename(apk_path), f, "application/vnd.android.package-archive")}
            print("[*] Uploading document to Telegram servers...")
            resp = requests.post(url, data=data, files=files, timeout=120)

        result = resp.json()
        if result.get("ok"):
            msg_id = result["result"]["message_id"]
            print(f"[+] SUCCESS! APK successfully uploaded to Telegram. Message ID: {msg_id}")
            return True
        else:
            print(f"[-] Telegram API Error: {result.get('description')} (Code: {result.get('error_code')})")
            return False
    except Exception as e:
        print(f"[-] Upload exception: {e}")
        return False


if __name__ == "__main__":
    token = os.environ.get("TELEGRAM_BOT_TOKEN")
    if not token and len(sys.argv) > 2:
        token = sys.argv[2]

    if not token:
        print("[-] Error: TELEGRAM_BOT_TOKEN environment variable is not set.")
        print("Usage:")
        print("  export TELEGRAM_BOT_TOKEN=\"123456789:ABCdef...\"")
        print("  python3 desktop/upload_to_telegram.py [path_to_apk]")
        sys.exit(1)

    apk_file = sys.argv[1] if len(sys.argv) > 1 and not sys.argv[1].startswith("--") else DEFAULT_APK_PATH
    success = upload_apk(token, apk_file)
    sys.exit(0 if success else 1)
