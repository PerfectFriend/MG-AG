"""
MadGammon Sound Effects Engine (SFX)
High-fidelity procedural acoustic synthesis and low-latency playback
for dice tumbling, dice bounce, checker sliding, and checker landing.
"""
import os
import sys
import math
import wave
import struct
import random
import threading
import subprocess
from typing import Optional
from logger import get_logger

log = get_logger("madgammon.sfx")

class SoundManager:
    """Manages sound effects generation, caching, and low-latency playback."""
    _instance: Optional["SoundManager"] = None

    @classmethod
    def get_instance(cls) -> "SoundManager":
        if cls._instance is None:
            cls._instance = SoundManager()
        return cls._instance

    def __init__(self):
        self.enabled = True
        self.volume = 0.85
        self.sound_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)), "assets", "sounds")
        os.makedirs(self.sound_dir, exist_ok=True)

        self.path_dice_tumble = os.path.join(self.sound_dir, "dice_tumble.wav")
        self.path_dice_bounce = os.path.join(self.sound_dir, "dice_bounce.wav")
        self.path_checker_slide = os.path.join(self.sound_dir, "checker_slide.wav")
        self.path_checker_land = os.path.join(self.sound_dir, "checker_land.wav")

        self._ensure_sounds_generated()
        self._detect_player()

    def _detect_player(self):
        """Detect available system audio player CLI (pw-play, paplay, aplay, ffplay)."""
        self.player_cmd = None
        for cmd in ["pw-play", "paplay", "aplay", "ffplay"]:
            try:
                subprocess.run(["which", cmd], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
                self.player_cmd = cmd
                log.info("Detected system audio player: %s", cmd)
                break
            except Exception:
                continue

    def _ensure_sounds_generated(self):
        """Synthesize realistic wooden backgammon acoustic WAVs if not present."""
        if not os.path.exists(self.path_dice_tumble):
            self._synth_dice_tumble(self.path_dice_tumble)
        if not os.path.exists(self.path_dice_bounce):
            self._synth_dice_bounce(self.path_dice_bounce)
        if not os.path.exists(self.path_checker_slide):
            self._synth_checker_slide(self.path_checker_slide)
        if not os.path.exists(self.path_checker_land):
            self._synth_checker_land(self.path_checker_land)

    def _write_wav(self, path: str, samples: list, samplerate: int = 44100):
        with wave.open(path, "wb") as wav:
            wav.setnchannels(1)
            wav.setsampwidth(2)
            wav.setframerate(samplerate)
            raw = bytearray()
            for s in samples:
                val = max(-32767, min(32767, int(s * 32767.0 * self.volume)))
                raw.extend(struct.pack("<h", val))
            wav.writeframesraw(raw)
        log.debug("WAV generated: %s (%d samples)", path, len(samples))

    def _synth_dice_bounce(self, path: str):
        """
        Sharp cracking / rattling wooden-bone dice bounce.
        High-energy dry crackle with fast-decaying wooden body, dry rattles and clicks.
        """
        rate = 44100
        dur = 0.18
        n = int(rate * dur)
        samples = [0.0] * n

        # Dry impact crack + mini secondary micro-rattles (2-3 dice facets micro-clashing)
        sub_clicks = [(0.0, 1.0), (0.012, 0.55), (0.028, 0.28)]
        for offset_s, amp in sub_clicks:
            start = int(offset_s * rate)
            for j in range(int(rate * 0.05)):
                idx = start + j
                if idx >= n:
                    break
                tj = j / rate
                # High frequency dry rattle / crack (snappy wood transient)
                crackle = (random.random() * 2.0 - 1.0) * math.exp(-180.0 * tj) * 0.65
                # High wood body resonance (dry bone/boxwood slap, higher formant, less bell-like)
                body = (
                    math.sin(2 * math.pi * 480 * tj) * 0.4 +
                    math.sin(2 * math.pi * 1120 * tj) * 0.35 +
                    math.sin(2 * math.pi * 2350 * tj) * 0.25
                ) * math.exp(-75.0 * tj)
                samples[idx] += (crackle + body) * amp

        # Normalize
        max_v = max(abs(s) for s in samples) if samples else 1.0
        if max_v > 0.95:
            samples = [s / max_v * 0.95 for s in samples]

        self._write_wav(path, samples, rate)

    def _synth_dice_tumble(self, path: str):
        """
        Cracking, rattling series of tumbling bone/wood dice across the board.
        Focuses on dry crackles, chatter, and rapid mechanical wooden micro-impacts rather than hollow ringing.
        """
        rate = 44100
        dur = 0.48
        n = int(rate * dur)
        samples = [0.0] * n

        # Rapid succession of sharp wooden cracks and rattles
        taps = [0.015, 0.045, 0.082, 0.125, 0.170, 0.220, 0.275, 0.335, 0.400]
        for offset in taps:
            start_idx = int(offset * rate)
            amp = random.uniform(0.45, 0.85)
            tap_len = int(rate * 0.045)
            # Higher crackle frequency with dry envelope
            for j in range(tap_len):
                idx = start_idx + j
                if idx >= n:
                    break
                tj = j / rate
                # Snappy dry crackle (burst of filtered white noise + dry wood slap)
                noise_crackle = (random.random() * 2.0 - 1.0) * math.exp(-220.0 * tj) * 0.6
                wood_snap = (
                    math.sin(2 * math.pi * random.uniform(520, 780) * tj) * 0.35 +
                    math.sin(2 * math.pi * random.uniform(1400, 1950) * tj) * 0.25
                ) * math.exp(-110.0 * tj)
                samples[idx] += (noise_crackle + wood_snap) * amp

        # Underlying felt rattle rumble
        for i in range(n):
            t = i / rate
            felt_friction = (random.random() * 2.0 - 1.0) * 0.06 * math.sin(math.pi * (t / dur))
            samples[i] += felt_friction

        # Normalize
        max_v = max(abs(s) for s in samples) if samples else 1.0
        if max_v > 0.95:
            samples = [s / max_v * 0.95 for s in samples]

        self._write_wav(path, samples, rate)

    def _synth_checker_slide(self, path: str):
        """Smooth sliding friction of wooden checker on felt."""
        rate = 44100
        dur = 0.26
        n = int(rate * dur)
        samples = []
        for i in range(n):
            t = i / rate
            # Swell envelope
            env = math.sin(math.pi * (t / dur))
            # Filtered noise + low friction resonance
            noise = (random.random() * 2.0 - 1.0) * 0.25
            hum = math.sin(2 * math.pi * 180 * t) * 0.3 + math.sin(2 * math.pi * 240 * t) * 0.15
            samples.append((noise + hum) * env * 0.6)
        self._write_wav(path, samples, rate)

    def _synth_checker_land(self, path: str):
        """Solid wooden click when checker lands and settles on a stack or point."""
        rate = 44100
        dur = 0.18
        n = int(rate * dur)
        samples = []
        for i in range(n):
            t = i / rate
            env = math.exp(-28.0 * t)
            snap = (random.random() * 2.0 - 1.0) * math.exp(-110.0 * t) * 0.45
            wood = (
                math.sin(2 * math.pi * 290 * t) * 0.55 +
                math.sin(2 * math.pi * 580 * t) * 0.3 +
                math.sin(2 * math.pi * 940 * t) * 0.15
            ) * env
            samples.append(snap + wood)
        self._write_wav(path, samples, rate)

    def _play_async(self, file_path: str):
        """Non-blocking background playback."""
        if not self.enabled or not os.path.exists(file_path):
            return

        def worker():
            # Try detected player
            players = []
            if self.player_cmd:
                players.append(self.player_cmd)
            for p in ["pw-play", "paplay", "aplay"]:
                if p not in players:
                    players.append(p)
            # Add ffplay as fallback
            if "ffplay" not in players:
                players.append("ffplay")

            for p in players:
                try:
                    if p == "ffplay":
                        cmd = ["ffplay", "-nodisp", "-nostats", "-autoexit", "-loglevel", "quiet", file_path]
                    else:
                        cmd = [p, file_path]
                    subprocess.run(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=1.5)
                    break
                except Exception:
                    continue

        t = threading.Thread(target=worker, daemon=True)
        t.start()

    def play_dice_tumble(self):
        self._play_async(self.path_dice_tumble)

    def play_dice_bounce(self):
        self._play_async(self.path_dice_bounce)

    def play_checker_slide(self):
        self._play_async(self.path_checker_slide)

    def play_checker_land(self):
        self._play_async(self.path_checker_land)
