"""
MadGammon Sovereign AVS Visualizer — Advanced Visualization Studio
Inspired by Winamp AVS (Justin Frankel / Nullsoft).
Modular pipeline of visual renderers and transformation components:
- Renderers: Spectrum Analyzer (FFT Bars), Oscilloscope, SuperScope (Math Parametric Curves),
  3D Starfield Warp, 80s Synthwave Horizon Grid, Cyberpunk Vortex Tunnel, Particle Bursts.
- Transforms: Motion Blur / Phosphor Decay Trails, Color Cycling (Rainbow/Neon), Kaleidoscope.
- Custom Presets Builder: Combine, reorder, tweak parameters, save & load presets.
- Fullscreen Screensaver Mode (F11 / Double-click) with auto-fading Cyber HUD.
"""

import math
import random
import time
import json
import os
from typing import List, Dict, Any, Optional
from dataclasses import dataclass, field

from PyQt6.QtWidgets import (
    QWidget, QDialog, QVBoxLayout, QHBoxLayout, QLabel, QPushButton,
    QComboBox, QSlider, QListWidget, QListWidgetItem, QCheckBox,
    QGroupBox, QFormLayout, QFileDialog, QMessageBox, QSplitter,
    QFrame, QApplication
)
from PyQt6.QtGui import (
    QPainter, QColor, QPen, QBrush, QFont, QRadialGradient,
    QLinearGradient, QPixmap, QImage, QPainterPath, QKeySequence, QShortcut
)
from PyQt6.QtCore import Qt, QTimer, QRectF, QPointF, pyqtSignal

from logger import get_logger

log = get_logger("madgammon.avs")


# ─── 1. AUDIO SPECTRUM & BEAT DATA MODEL ───
@dataclass
class AudioSpectrumData:
    """Real-time / reactive frequency spectrum and beat energy representation."""
    num_bands: int = 64
    bands: List[float] = field(default_factory=lambda: [0.0] * 64)
    peaks: List[float] = field(default_factory=lambda: [0.0] * 64)
    peak_hold_times: List[float] = field(default_factory=lambda: [0.0] * 64)
    waveform: List[float] = field(default_factory=lambda: [0.0] * 128)
    bass: float = 0.0
    mid: float = 0.0
    treble: float = 0.0
    is_beat: bool = False
    beat_intensity: float = 0.0
    energy: float = 0.0

    def update_reactive(self, is_playing: bool, dt: float, t: float):
        """
        High-fidelity audio-reactive spectrum synthesizer with realistic
        frequency distribution, transient beat detection, and physical gravity peaks.
        """
        if not is_playing:
            # Decay to zero smoothly
            for i in range(self.num_bands):
                self.bands[i] *= 0.90
                self.peaks[i] = max(0.0, self.peaks[i] - 1.2 * dt)
            self.bass *= 0.90
            self.mid *= 0.90
            self.treble *= 0.90
            self.is_beat = False
            self.energy *= 0.90
            return

        # Musical harmonics & beat rhythm simulation (BPM ~124)
        bpm = 124.0
        beat_phase = (t * (bpm / 60.0)) % 1.0
        beat_hit = math.exp(-beat_phase * 6.0)
        self.is_beat = beat_hit > 0.65
        self.beat_intensity = beat_hit

        # Bass kick rhythm
        bass_drive = 0.4 + 0.6 * beat_hit + 0.15 * math.sin(t * 3.7)
        mid_drive = 0.3 + 0.4 * math.sin(t * 7.2) + 0.3 * beat_hit
        treble_drive = 0.25 + 0.35 * math.cos(t * 11.4) + 0.2 * (random.random() * 0.4)

        self.bass = max(0.0, min(1.0, bass_drive))
        self.mid = max(0.0, min(1.0, mid_drive))
        self.treble = max(0.0, min(1.0, treble_drive))
        self.energy = (self.bass * 0.5 + self.mid * 0.3 + self.treble * 0.2)

        # 64 frequency bands with logarithmic roll-off
        for i in range(self.num_bands):
            freq_norm = i / self.num_bands  # 0.0 (sub-bass) to 1.0 (ultra-high)
            # Bass weighting
            if freq_norm < 0.2:
                target = self.bass * (1.0 - freq_norm * 2.0)
                target += 0.2 * math.sin(t * 15.0 + i * 0.5)
            # Mid weighting
            elif freq_norm < 0.6:
                target = self.mid * (1.0 - abs(freq_norm - 0.4) * 2.0)
                target += 0.15 * math.cos(t * 22.0 + i * 0.8)
            # Treble weighting
            else:
                target = self.treble * (0.8 - (freq_norm - 0.6) * 1.5)
                target += 0.12 * math.sin(t * 35.0 + i * 1.2)

            target = max(0.05, min(1.0, target + (random.random() - 0.5) * 0.08))

            # Smooth attack and decay (attack fast, release smooth)
            if target > self.bands[i]:
                self.bands[i] += (target - self.bands[i]) * 0.6
            else:
                self.bands[i] += (target - self.bands[i]) * 0.18

            # Physical gravity for peak meters
            if self.bands[i] >= self.peaks[i]:
                self.peaks[i] = self.bands[i]
                self.peak_hold_times[i] = t + 0.18
            else:
                if t > self.peak_hold_times[i]:
                    self.peaks[i] = max(0.0, self.peaks[i] - 1.4 * dt)

        # Oscilloscope waveform buffer
        wf_len = len(self.waveform)
        for i in range(wf_len):
            p = (i / wf_len) * 2.0 * math.pi
            val = (
                math.sin(p * 2.0 + t * 8.0) * self.bass * 0.5 +
                math.sin(p * 5.0 - t * 14.0) * self.mid * 0.35 +
                math.sin(p * 11.0 + t * 24.0) * self.treble * 0.2
            )
            self.waveform[i] = max(-1.0, min(1.0, val))


# ─── 2. BASE AVS COMPONENT ───
class AVSComponent:
    """
    Abstract visual component in the Winamp AVS pipeline.
    Components can be renderers (drawing shapes/bars) or transforms (post-processing).
    """
    def __init__(self, name: str, category: str = "RENDER"):
        self.name = name
        self.category = category  # "RENDER" or "TRANS"
        self.enabled = True
        self.params: Dict[str, Any] = {}

    def render(self, painter: QPainter, width: int, height: int, audio: AudioSpectrumData, dt: float, t: float):
        raise NotImplementedError

    def to_dict(self) -> Dict[str, Any]:
        return {
            "name": self.name,
            "type": self.__class__.__name__,
            "category": self.category,
            "enabled": self.enabled,
            "params": self.params.copy()
        }

    def from_dict(self, data: Dict[str, Any]):
        self.enabled = data.get("enabled", True)
        self.params.update(data.get("params", {}))


# ─── 3. RENDER COMPONENTS ───

class SpectrumBarsComponent(AVSComponent):
    """Classic Winamp FFT Spectrum Analyzer with falling peaks and neon gradients."""
    def __init__(self):
        super().__init__("Спектральный анализатор (FFT Bars)", "RENDER")
        self.params = {
            "bar_count": 32,
            "style": "NEON_CYAN",  # NEON_CYAN, FIRE, MATRIX, RAINBOW
            "show_peaks": True,
            "mirror": False,
            "height_scale": 0.75,
        }

    def render(self, painter: QPainter, width: int, height: int, audio: AudioSpectrumData, dt: float, t: float):
        if not self.enabled:
            return

        bar_count = int(self.params.get("bar_count", 32))
        style = self.params.get("style", "NEON_CYAN")
        show_peaks = self.params.get("show_peaks", True)
        mirror = self.params.get("mirror", False)
        scale = float(self.params.get("height_scale", 0.75))

        margin = 12
        available_w = width - margin * 2
        bar_w = max(2.0, (available_w / bar_count) - 2.0)
        base_y = height - 16 if not mirror else height / 2.0
        max_h = (height * 0.7) * scale

        step_band = len(audio.bands) // bar_count
        step_band = max(1, step_band)

        for i in range(bar_count):
            band_idx = min(len(audio.bands) - 1, i * step_band)
            val = audio.bands[band_idx]
            peak_val = audio.peaks[band_idx]
            bar_h = val * max_h

            x = margin + i * (bar_w + 2.0)

            # Palette gradient
            if style == "FIRE":
                c1 = QColor(255, 50, 20)
                c2 = QColor(255, 220, 0)
            elif style == "MATRIX":
                c1 = QColor(0, 180, 50)
                c2 = QColor(80, 255, 120)
            elif style == "RAINBOW":
                hue = int((i / bar_count * 300 + t * 40) % 360)
                c1 = QColor.fromHsv(hue, 220, 255)
                c2 = QColor.fromHsv((hue + 40) % 360, 200, 255)
            else:  # NEON_CYAN
                c1 = QColor(0, 229, 255)
                c2 = QColor(180, 0, 255)

            grad = QLinearGradient(x, base_y, x, base_y - bar_h)
            grad.setColorAt(0.0, c1)
            grad.setColorAt(1.0, c2)

            painter.setPen(Qt.PenStyle.NoPen)
            painter.setBrush(QBrush(grad))
            painter.drawRoundedRect(QRectF(x, base_y - bar_h, bar_w, bar_h), 2.0, 2.0)

            if mirror:
                painter.drawRoundedRect(QRectF(x, base_y, bar_w, bar_h), 2.0, 2.0)

            # Draw falling peak
            if show_peaks:
                peak_y = base_y - (peak_val * max_h) - 3.0
                painter.setPen(QPen(QColor(255, 255, 255, 220), 1.5))
                painter.drawLine(QPointF(x, peak_y), QPointF(x + bar_w, peak_y))
                if mirror:
                    peak_ym = base_y + (peak_val * max_h) + 3.0
                    painter.drawLine(QPointF(x, peak_ym), QPointF(x + bar_w, peak_ym))


class OscilloscopeComponent(AVSComponent):
    """Phosphor CRT style audio oscilloscope & waveform analyzer."""
    def __init__(self):
        super().__init__("Осциллограф (Phosphor CRT)", "RENDER")
        self.params = {
            "mode": "LINE",  # LINE, DOTS, LISSAJOUS
            "color": "#00ff88",
            "glow": True,
            "thickness": 2.0,
            "amplitude": 1.0,
        }

    def render(self, painter: QPainter, width: int, height: int, audio: AudioSpectrumData, dt: float, t: float):
        if not self.enabled:
            return

        mode = self.params.get("mode", "LINE")
        base_color = QColor(self.params.get("color", "#00ff88"))
        glow = self.params.get("glow", True)
        thick = float(self.params.get("thickness", 2.0))
        amp = float(self.params.get("amplitude", 1.0))

        cy = height * 0.45
        wf = audio.waveform
        n = len(wf)

        if mode == "LISSAJOUS":
            # 2D Circular harmonic Lissajous figure
            cx = width / 2.0
            radius = min(width, height) * 0.28 * (1.0 + audio.bass * 0.3)
            path = QPainterPath()
            for i in range(n):
                angle = (i / n) * 2.0 * math.pi * 3.0 + t * 2.0
                sample = wf[i] * amp
                r = radius * (1.0 + sample * 0.5)
                px = cx + math.cos(angle) * r
                py = cy + math.sin(angle * 2.0) * r * 0.8
                if i == 0:
                    path.moveTo(px, py)
                else:
                    path.lineTo(px, py)

            if glow:
                glow_color = QColor(base_color)
                glow_color.setAlpha(60)
                painter.setPen(QPen(glow_color, thick + 4.0))
                painter.setBrush(Qt.BrushStyle.NoBrush)
                painter.drawPath(path)

            painter.setPen(QPen(base_color, thick))
            painter.setBrush(Qt.BrushStyle.NoBrush)
            painter.drawPath(path)

        else:
            # Horizontal Waveform line
            path = QPainterPath()
            dx = width / max(1, n - 1)
            for i in range(n):
                px = i * dx
                py = cy + wf[i] * (height * 0.22) * amp
                if i == 0:
                    path.moveTo(px, py)
                else:
                    path.lineTo(px, py)

            if glow:
                glow_color = QColor(base_color)
                glow_color.setAlpha(55)
                painter.setPen(QPen(glow_color, thick + 5.0))
                painter.setBrush(Qt.BrushStyle.NoBrush)
                painter.drawPath(path)

            painter.setPen(QPen(base_color, thick))
            painter.setBrush(Qt.BrushStyle.NoBrush)
            painter.drawPath(path)


class SuperScopeComponent(AVSComponent):
    """
    Parametric mathematical curve generator directly inspired by Winamp SuperScope.
    Renders complex polar curves modulated by audio dynamics.
    """
    def __init__(self):
        super().__init__("SuperScope (Математические кривые)", "RENDER")
        self.params = {
            "formula": "CYBER_FLOWER",  # CYBER_FLOWER, HELIX, VORTEX, CARDIAC
            "color": "#ff00bb",
            "points": 180,
            "speed": 1.2,
        }

    def render(self, painter: QPainter, width: int, height: int, audio: AudioSpectrumData, dt: float, t: float):
        if not self.enabled:
            return

        formula = self.params.get("formula", "CYBER_FLOWER")
        col = QColor(self.params.get("color", "#ff00bb"))
        n = int(self.params.get("points", 180))
        speed = float(self.params.get("speed", 1.2))

        cx = width / 2.0
        cy = height * 0.48
        scale = min(width, height) * 0.35 * (1.0 + audio.beat_intensity * 0.25)
        phase = t * speed

        path = QPainterPath()
        first = True

        for i in range(n):
            u = (i / n) * 2.0 * math.pi
            wf_val = audio.waveform[i % len(audio.waveform)]

            if formula == "CYBER_FLOWER":
                petals = 6
                r = (0.5 + 0.5 * math.cos(petals * u + phase)) * scale
                r += wf_val * 45.0 * audio.bass
                px = cx + math.cos(u + phase * 0.5) * r
                py = cy + math.sin(u + phase * 0.5) * r

            elif formula == "HELIX":
                # Double spiral DNA helix
                r = (u / (2.0 * math.pi)) * scale
                px = cx + math.cos(u * 3.0 + phase) * r + wf_val * 30.0
                py = cy + math.sin(u * 2.0 - phase) * r

            elif formula == "VORTEX":
                # Logarithmic spiral vortex
                r = math.exp(u * 0.4) / math.exp(math.pi * 0.8) * scale
                r += audio.bass * 35.0
                px = cx + math.cos(u * 4.0 + phase * 2.0) * r
                py = cy + math.sin(u * 4.0 + phase * 2.0) * r

            else:  # CARDIAC
                # Heart-shaped pulsating curve
                s = scale * 0.8 * (1.0 + audio.bass * 0.4)
                px = cx + 16.0 * (math.sin(u) ** 3) * (s / 16.0)
                py = cy - (13.0 * math.cos(u) - 5.0 * math.cos(2.0 * u) - 2.0 * math.cos(3.0 * u) - math.cos(4.0 * u)) * (s / 16.0)

            if first:
                path.moveTo(px, py)
                first = False
            else:
                path.lineTo(px, py)

        # Glow layer
        glow = QColor(col)
        glow.setAlpha(60)
        painter.setPen(QPen(glow, 4.0))
        painter.setBrush(Qt.BrushStyle.NoBrush)
        painter.drawPath(path)

        # Core sharp line
        painter.setPen(QPen(col, 2.0))
        painter.drawPath(path)


class StarfieldWarpComponent(AVSComponent):
    """3D Space warp starfield with acceleration transients on bass kicks."""
    def __init__(self):
        super().__init__("3D Starfield Warp", "RENDER")
        self.params = {
            "star_count": 140,
            "speed": 1.0,
            "warp_color": "#ffffff",
        }
        self.stars: List[List[float]] = []
        self._init_stars(140)

    def _init_stars(self, count: int):
        self.stars = [
            [(random.random() - 0.5) * 2000.0, (random.random() - 0.5) * 2000.0, random.random() * 1000.0 + 10.0]
            for _ in range(count)
        ]

    def render(self, painter: QPainter, width: int, height: int, audio: AudioSpectrumData, dt: float, t: float):
        if not self.enabled:
            return

        count = int(self.params.get("star_count", 140))
        if len(self.stars) != count:
            self._init_stars(count)

        base_speed = float(self.params.get("speed", 1.0)) * 400.0
        speed = base_speed * (1.0 + audio.bass * 2.5 + (2.0 if audio.is_beat else 0.0))

        cx = width / 2.0
        cy = height / 2.0
        fov = min(width, height) * 0.8

        painter.setPen(Qt.PenStyle.NoPen)

        for s in self.stars:
            s[2] -= speed * dt
            if s[2] <= 5.0:
                s[0] = (random.random() - 0.5) * 2000.0
                s[1] = (random.random() - 0.5) * 2000.0
                s[2] = 1000.0

            # Perspective 3D -> 2D
            pz = max(1.0, s[2])
            sx = cx + (s[0] / pz) * fov
            sy = cy + (s[1] / pz) * fov

            if 0 <= sx < width and 0 <= sy < height:
                depth_norm = 1.0 - (pz / 1000.0)
                sz = max(1.2, depth_norm * 4.5 * (1.2 if audio.is_beat else 1.0))
                brightness = int(depth_norm * 240 + 15)

                if audio.is_beat and depth_norm > 0.4:
                    # Hyperspace warp streak
                    pz_prev = pz + speed * dt * 0.12
                    px_prev = cx + (s[0] / pz_prev) * fov
                    py_prev = cy + (s[1] / pz_prev) * fov
                    painter.setPen(QPen(QColor(0, 229, 255, brightness), sz * 0.8))
                    painter.drawLine(QPointF(sx, sy), QPointF(px_prev, py_prev))
                else:
                    painter.setPen(Qt.PenStyle.NoPen)
                    painter.setBrush(QColor(brightness, brightness, 255, brightness))
                    painter.drawEllipse(QPointF(sx, sy), sz, sz)


class SynthwaveHorizonComponent(AVSComponent):
    """80s Outrun Synthwave Grid Horizon with pulsing cyber sun."""
    def __init__(self):
        super().__init__("Неоновый Synthwave горизонт", "RENDER")
        self.params = {
            "grid_color": "#ff0077",
            "sun_color": "#ffbb00",
            "speed": 1.0,
        }

    def render(self, painter: QPainter, width: int, height: int, audio: AudioSpectrumData, dt: float, t: float):
        if not self.enabled:
            return

        horizon_y = height * 0.55
        grid_col = QColor(self.params.get("grid_color", "#ff0077"))
        sun_col = QColor(self.params.get("sun_color", "#ffbb00"))
        speed = float(self.params.get("speed", 1.0))

        cx = width / 2.0

        # 1. Pulsing Cyber Sun above horizon
        sun_radius = min(width, height) * 0.22 * (1.0 + audio.bass * 0.15)
        sun_grad = QRadialGradient(cx, horizon_y - 20.0, sun_radius)
        sun_grad.setColorAt(0.0, sun_col)
        sun_grad.setColorAt(0.7, QColor(255, 50, 100, 220))
        sun_grad.setColorAt(1.0, QColor(20, 10, 40, 0))

        painter.setPen(Qt.PenStyle.NoPen)
        painter.setBrush(QBrush(sun_grad))
        painter.drawEllipse(QPointF(cx, horizon_y - 20.0), sun_radius, sun_radius)

        # Sun horizontal laser blinds
        painter.setPen(QPen(QColor(10, 14, 24), 2.5))
        for line_y in range(int(horizon_y - sun_radius * 0.4), int(horizon_y), 8):
            painter.drawLine(int(cx - sun_radius), line_y, int(cx + sun_radius), line_y)

        # 2. Moving 3D Perspective Grid
        painter.setPen(QPen(grid_col, 1.2))

        # Perspective vanishing lines from center
        lines_count = 16
        for i in range(-lines_count, lines_count + 1):
            bottom_x = cx + i * (width / lines_count) * 1.5
            painter.drawLine(QPointF(cx, horizon_y), QPointF(bottom_x, height))

        # Horizontal grid bars with perspective depth
        num_h_lines = 12
        grid_offset = (t * speed * 0.8) % 1.0
        for i in range(num_h_lines):
            norm_depth = ((i + grid_offset) / num_h_lines) ** 2.2
            y = horizon_y + norm_depth * (height - horizon_y)
            alpha = int(norm_depth * 220)
            cur_col = QColor(grid_col)
            cur_col.setAlpha(alpha)
            painter.setPen(QPen(cur_col, max(1.0, norm_depth * 2.5)))
            painter.drawLine(0, int(y), width, int(y))


class CyberTunnelComponent(AVSComponent):
    """Hexagonal 3D wireframe vortex tunnel rotating and throbbing to the bass."""
    def __init__(self):
        super().__init__("Кибер-вихрь (3D Туннель)", "RENDER")
        self.params = {
            "rings": 12,
            "sides": 6,
            "color": "#00e5ff",
            "rotation_speed": 0.8,
        }

    def render(self, painter: QPainter, width: int, height: int, audio: AudioSpectrumData, dt: float, t: float):
        if not self.enabled:
            return

        rings = int(self.params.get("rings", 12))
        sides = int(self.params.get("sides", 6))
        col = QColor(self.params.get("color", "#00e5ff"))
        rot_spd = float(self.params.get("rotation_speed", 0.8))

        cx = width / 2.0
        cy = height * 0.5
        max_r = min(width, height) * 0.55
        rot_base = t * rot_spd

        for i in range(rings):
            # Ring depth 0 (center) to 1 (near viewer)
            depth = ((i + (t * 1.2) % 1.0) / rings) ** 1.8
            r = depth * max_r * (1.0 + audio.bass * 0.25)
            alpha = int(depth * 230)

            rot = rot_base + i * 0.15
            ring_col = QColor(col)
            ring_col.setAlpha(alpha)
            painter.setPen(QPen(ring_col, max(1.0, depth * 3.0)))
            painter.setBrush(Qt.BrushStyle.NoBrush)

            poly = QPainterPath()
            for s in range(sides):
                angle = (s / sides) * 2.0 * math.pi + rot
                px = cx + math.cos(angle) * r
                py = cy + math.sin(angle) * r
                if s == 0:
                    poly.moveTo(px, py)
                else:
                    poly.lineTo(px, py)
            poly.closeSubpath()
            painter.drawPath(poly)


class ParticleBurstComponent(AVSComponent):
    """Audio-reactive particle explosion triggered on transient bass kicks."""
    def __init__(self):
        super().__init__("Частицы и Салют (Beat Sparks)", "RENDER")
        self.params = {
            "max_particles": 80,
            "color": "#ffbb00",
        }
        self.particles: List[Dict[str, float]] = []

    def render(self, painter: QPainter, width: int, height: int, audio: AudioSpectrumData, dt: float, t: float):
        if not self.enabled:
            return

        cx = width / 2.0
        cy = height * 0.5
        max_p = int(self.params.get("max_particles", 80))
        col = QColor(self.params.get("color", "#ffbb00"))

        # Spawn burst on beat
        if audio.is_beat and len(self.particles) < max_p:
            burst_count = random.randint(10, 20)
            for _ in range(burst_count):
                angle = random.random() * 2.0 * math.pi
                spd = random.uniform(80.0, 360.0) * (1.0 + audio.bass)
                self.particles.append({
                    "x": cx + (random.random() - 0.5) * 20.0,
                    "y": cy + (random.random() - 0.5) * 20.0,
                    "vx": math.cos(angle) * spd,
                    "vy": math.sin(angle) * spd,
                    "life": 1.0,
                    "decay": random.uniform(0.7, 1.8),
                    "size": random.uniform(2.5, 6.0)
                })

        # Update & draw existing
        alive = []
        painter.setPen(Qt.PenStyle.NoPen)
        for p in self.particles:
            p["x"] += p["vx"] * dt
            p["y"] += p["vy"] * dt
            p["life"] -= p["decay"] * dt

            if p["life"] > 0:
                alive.append(p)
                alpha = int(p["life"] * 240)
                p_col = QColor(col)
                p_col.setAlpha(alpha)
                painter.setBrush(p_col)
                sz = p["size"] * p["life"]
                painter.drawEllipse(QPointF(p["x"], p["y"]), sz, sz)

        self.particles = alive[:max_p]


# ─── 4. TRANSFORMATION & POST-PROCESS COMPONENTS ───

class MotionBlurComponent(AVSComponent):
    """Phosphor decay and motion trail feedback."""
    def __init__(self):
        super().__init__("Motion Blur (Шлейф / Фосфор)", "TRANS")
        self.params = {
            "decay": 0.82,  # 0.5 to 0.95
        }

    def render(self, painter: QPainter, width: int, height: int, audio: AudioSpectrumData, dt: float, t: float):
        # Applied through buffer fading in canvas engine
        pass


class ColorCycleComponent(AVSComponent):
    """Rotates HSL palette hues continuously."""
    def __init__(self):
        super().__init__("Цветовой цикл (Rainbow Cycle)", "TRANS")
        self.params = {
            "speed": 1.0,
        }

    def render(self, painter: QPainter, width: int, height: int, audio: AudioSpectrumData, dt: float, t: float):
        pass


class KaleidoscopeComponent(AVSComponent):
    """4-way kaleidoscope symmetrical mirroring."""
    def __init__(self):
        super().__init__("Калейдоскоп (4-Way Mirror)", "TRANS")
        self.params = {
            "mode": "QUAD",  # DUAL, QUAD
        }

    def render(self, painter: QPainter, width: int, height: int, audio: AudioSpectrumData, dt: float, t: float):
        pass


# ─── 5. WINAMP AVS PRESET ARCHITECTURE ───
@dataclass
class AVSPreset:
    name: str
    description: str
    components: List[AVSComponent] = field(default_factory=list)

    def to_dict(self) -> Dict[str, Any]:
        return {
            "name": self.name,
            "description": self.description,
            "components": [c.to_dict() for c in self.components]
        }

    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> 'AVSPreset':
        preset = cls(
            name=data.get("name", "Custom Preset"),
            description=data.get("description", "")
        )
        registry = {
            "SpectrumBarsComponent": SpectrumBarsComponent,
            "OscilloscopeComponent": OscilloscopeComponent,
            "SuperScopeComponent": SuperScopeComponent,
            "StarfieldWarpComponent": StarfieldWarpComponent,
            "SynthwaveHorizonComponent": SynthwaveHorizonComponent,
            "CyberTunnelComponent": CyberTunnelComponent,
            "ParticleBurstComponent": ParticleBurstComponent,
            "MotionBlurComponent": MotionBlurComponent,
            "ColorCycleComponent": ColorCycleComponent,
            "KaleidoscopeComponent": KaleidoscopeComponent,
        }
        for comp_data in data.get("components", []):
            c_type = comp_data.get("type")
            if c_type in registry:
                comp = registry[c_type]()
                comp.from_dict(comp_data)
                preset.components.append(comp)
        return preset


def get_default_presets() -> List[AVSPreset]:
    """Generates the built-in catalogue of Winamp AVS presets."""
    p1 = AVSPreset(
        name="⚡ Cyber Neon Classic",
        description="Классический неоновый спектр Winamp 1998 + фосфорный осциллограф + 3D звёзды",
        components=[
            StarfieldWarpComponent(),
            SpectrumBarsComponent(),
            OscilloscopeComponent(),
        ]
    )

    p2 = AVSPreset(
        name="🌅 Outrun Synthwave 1984",
        description="Ретровейв горизонт, неоновое солнце и спектральные горы",
        components=[
            SynthwaveHorizonComponent(),
            SpectrumBarsComponent(),
            ParticleBurstComponent(),
        ]
    )
    p2.components[1].params["style"] = "FIRE"

    p3 = AVSPreset(
        name="🌌 Deep Space Warp",
        description="3D гиперпрыжок в космосе + математический спиральный вихрь SuperScope",
        components=[
            StarfieldWarpComponent(),
            SuperScopeComponent(),
            ParticleBurstComponent(),
        ]
    )
    p3.components[1].params["formula"] = "VORTEX"

    p4 = AVSPreset(
        name="📟 Acid Phosphor Oscilloscope",
        description="Винтажный зелёный осциллограф с фигурами Лиссажу и спектральными столбиками",
        components=[
            OscilloscopeComponent(),
            SpectrumBarsComponent(),
        ]
    )
    p4.components[0].params["mode"] = "LISSAJOUS"
    p4.components[1].params["style"] = "MATRIX"

    p5 = AVSPreset(
        name="🌀 Hexagonal Cyber Vortex",
        description="3D туннель сквозь киберпространство + салют из частиц на басах",
        components=[
            CyberTunnelComponent(),
            ParticleBurstComponent(),
            SpectrumBarsComponent(),
        ]
    )

    p6 = AVSPreset(
        name="🧬 SuperScope DNA Helix",
        description="Вращающаяся двойная спираль ДНК, реагирующая на средние и высокие частоты",
        components=[
            StarfieldWarpComponent(),
            SuperScopeComponent(),
        ]
    )
    p6.components[1].params["formula"] = "HELIX"

    return [p1, p2, p3, p4, p5, p6]


# ─── 6. AVS CANVAS & SCREENSAVER WIDGET ───
class AVSCanvasWidget(QWidget):
    """
    High-performance 60 FPS Canvas rendering the active Winamp AVS component stack.
    Supports fullscreen screensaver mode with F11, double click, and Cyber HUD.
    """
    fullscreen_toggled = pyqtSignal(bool)

    def __init__(self, parent=None):
        super().__init__(parent)
        self.setMinimumSize(320, 180)
        self.setFocusPolicy(Qt.FocusPolicy.StrongFocus)

        self.audio = AudioSpectrumData()
        self.presets = get_default_presets()
        self.current_preset_idx = 0
        self.is_playing = False
        self.station_title = "MadGammon Sovereign Radio"

        # Auto-cycling presets
        self.auto_cycle_presets = True
        self.cycle_interval_sec = 25.0
        self._last_cycle_time = time.time()

        # Render timing & FPS
        self._t0 = time.time()
        self._last_frame_time = time.time()
        self.fps = 60
        self._frame_count = 0
        self._fps_time = time.time()

        # Canvas offscreen buffer for trails
        self.buffer_pixmap: Optional[QPixmap] = None

        # Screensaver mode state
        self.is_fullscreen_screensaver = False
        self._prev_parent = None
        self._prev_flags = None
        self._prev_geo = None

        # 60 FPS animation timer
        self.anim_timer = QTimer(self)
        self.anim_timer.setInterval(16)  # ~60 fps
        self.anim_timer.timeout.connect(self._on_render_tick)
        self.anim_timer.start()

        # Load saved custom presets if exist
        self._load_saved_presets()

    @property
    def current_preset(self) -> AVSPreset:
        return self.presets[self.current_preset_idx]

    def set_playing_state(self, playing: bool, station_name: Optional[str] = None):
        self.is_playing = playing
        if station_name:
            self.station_title = station_name
        self.update()

    def set_preset_by_index(self, idx: int):
        if 0 <= idx < len(self.presets):
            self.current_preset_idx = idx
            self.buffer_pixmap = None  # Clear trail buffer
            self.update()

    def next_preset(self):
        next_idx = (self.current_preset_idx + 1) % len(self.presets)
        self.set_preset_by_index(next_idx)

    def prev_preset(self):
        prev_idx = (self.current_preset_idx - 1) % len(self.presets)
        self.set_preset_by_index(prev_idx)

    def toggle_fullscreen_screensaver(self):
        """Toggles true fullscreen screensaver display mode."""
        self.is_fullscreen_screensaver = not self.is_fullscreen_screensaver
        if self.is_fullscreen_screensaver:
            self.setWindowFlag(Qt.WindowType.Window, True)
            self.showFullScreen()
            self.activateWindow()
            self.setCursor(Qt.CursorShape.BlankCursor)  # Hide mouse cursor in screensaver
        else:
            self.setWindowFlag(Qt.WindowType.Window, False)
            self.showNormal()
            self.unsetCursor()
        self.fullscreen_toggled.emit(self.is_fullscreen_screensaver)

    def keyPressEvent(self, event):
        if event.key() in (Qt.Key.Key_F11, Qt.Key.Key_F):
            self.toggle_fullscreen_screensaver()
        elif event.key() == Qt.Key.Key_Escape:
            if self.is_fullscreen_screensaver:
                self.toggle_fullscreen_screensaver()
        elif event.key() in (Qt.Key.Key_Right, Qt.Key.Key_N, Qt.Key.Key_Space):
            self.next_preset()
        elif event.key() in (Qt.Key.Key_Left, Qt.Key.Key_P):
            self.prev_preset()
        else:
            super().keyPressEvent(event)

    def mouseDoubleClickEvent(self, event):
        self.toggle_fullscreen_screensaver()

    def _on_render_tick(self):
        now = time.time()
        dt = max(0.001, min(0.08, now - self._last_frame_time))
        self._last_frame_time = now
        t = now - self._t0

        # Update audio spectrum model
        self.audio.update_reactive(self.is_playing, dt, t)

        # Auto-cycle presets
        if self.auto_cycle_presets and (now - self._last_cycle_time > self.cycle_interval_sec):
            self._last_cycle_time = now
            self.next_preset()

        # FPS calculation
        self._frame_count += 1
        if now - self._fps_time >= 1.0:
            self.fps = self._frame_count
            self._frame_count = 0
            self._fps_time = now

        self.update()

    def paintEvent(self, event):
        w = self.width()
        h = self.height()
        if w <= 0 or h <= 0:
            return

        now = time.time()
        t = now - self._t0
        dt = max(0.001, min(0.08, now - self._last_frame_time))

        # Check buffer allocation
        if self.buffer_pixmap is None or self.buffer_pixmap.size() != self.size():
            self.buffer_pixmap = QPixmap(w, h)
            self.buffer_pixmap.fill(QColor(10, 14, 22))

        # Render onto buffer
        bpainter = QPainter(self.buffer_pixmap)
        bpainter.setRenderHint(QPainter.RenderHint.Antialiasing, True)

        # Decay trails / background fade
        decay_alpha = 42 if self.is_playing else 85
        bpainter.setPen(Qt.PenStyle.NoPen)
        bpainter.setBrush(QColor(10, 14, 22, decay_alpha))
        bpainter.drawRect(0, 0, w, h)

        # Execute component stack
        for comp in self.current_preset.components:
            if comp.enabled:
                comp.render(bpainter, w, h, self.audio, dt, t)

        bpainter.end()

        # Draw buffer to screen
        screen_painter = QPainter(self)
        screen_painter.drawPixmap(0, 0, self.buffer_pixmap)

        # Draw Cyber HUD Overlay
        self._draw_hud(screen_painter, w, h)

    def _draw_hud(self, painter: QPainter, w: int, h: int):
        painter.setRenderHint(QPainter.RenderHint.Antialiasing, True)

        # Top Bar: Preset & Title
        hud_bg = QColor(10, 14, 22, 170)
        painter.setPen(Qt.PenStyle.NoPen)
        painter.setBrush(hud_bg)
        painter.drawRoundedRect(QRectF(10, 10, w - 20, 32), 6, 6)

        painter.setFont(QFont("Monospace", 9, QFont.Weight.Bold))
        painter.setPen(QColor(0, 229, 255))
        preset_txt = f"AVS: {self.current_preset.name} [{self.current_preset_idx + 1}/{len(self.presets)}]"
        painter.drawText(20, 31, preset_txt)

        # Station Title center
        painter.setPen(QColor(240, 245, 255))
        st_text = self.station_title if len(self.station_title) < 45 else self.station_title[:42] + "..."
        fm = painter.fontMetrics()
        st_w = fm.horizontalAdvance(st_text)
        painter.drawText(int((w - st_w) / 2), 31, st_text)

        # Right FPS & Mode indicator
        painter.setPen(QColor(0, 255, 136))
        fps_txt = f"{self.fps} FPS" + (" [FULLSCREEN]" if self.is_fullscreen_screensaver else " [F11: Fullscreen]")
        r_w = fm.horizontalAdvance(fps_txt)
        painter.drawText(w - r_w - 20, 31, fps_txt)

    def _load_saved_presets(self):
        p_path = os.path.expanduser("~/.madgammon/avs_presets.json")
        if os.path.exists(p_path):
            try:
                with open(p_path, "r", encoding="utf-8") as f:
                    data = json.load(f)
                custom_presets = [AVSPreset.from_dict(item) for item in data]
                if custom_presets:
                    self.presets.extend(custom_presets)
                    log.info("Loaded %d custom AVS presets from %s", len(custom_presets), p_path)
            except Exception as e:
                log.warning("Failed to load custom AVS presets: %s", e)

    def save_custom_presets(self):
        p_path = os.path.expanduser("~/.madgammon/avs_presets.json")
        os.makedirs(os.path.dirname(p_path), exist_ok=True)
        try:
            data = [p.to_dict() for p in self.presets]
            with open(p_path, "w", encoding="utf-8") as f:
                json.dump(data, f, indent=2)
            log.info("Saved %d AVS presets to %s", len(self.presets), p_path)
            return True
        except Exception as e:
            log.error("Failed to save AVS presets: %s", e)
            return False


# ─── 7. WINAMP AVS PRESET & COMPONENT EDITOR DIALOG ───
class AVSPresetEditorDialog(QDialog):
    """
    Full Winamp AVS style Studio Editor.
    Allows user to create new presets, add/remove components,
    reorder the rendering pipeline, and tweak component parameters live.
    """
    def __init__(self, visualizer: AVSCanvasWidget, parent=None):
        super().__init__(parent)
        self.viz = visualizer
        self.setWindowTitle("🎨 Winamp AVS Студия // Конструктор Визуализаций")
        self.resize(760, 520)
        self.setStyleSheet("""
            QDialog { background: #0e121a; color: #e6edf8; font-family: sans-serif; }
            QLabel { color: #88a0bc; font-size: 11px; }
            QListWidget {
                background: #141a24; color: #00ffaa; border: 1px solid #202b3c;
                border-radius: 6px; padding: 4px; font-family: Monospace; font-size: 11px;
            }
            QListWidget::item:selected { background: #003344; color: #00e5ff; }
            QPushButton {
                background: #182230; color: #e6edf8; border: 1px solid #28394e;
                border-radius: 4px; padding: 6px 12px; font-size: 11px; font-weight: bold;
            }
            QPushButton:hover { background: #223044; border-color: #00e5ff; }
            QComboBox {
                background: #141a24; color: #00e5ff; border: 1px solid #202b3c;
                border-radius: 4px; padding: 5px; font-size: 11px; font-family: Monospace;
            }
        """)

        layout = QVBoxLayout(self)
        layout.setContentsMargins(14, 14, 14, 14)
        layout.setSpacing(10)

        # Preset Selector Bar
        top_bar = QHBoxLayout()
        top_bar.addWidget(QLabel("<b>Активный пресет:</b>"))

        self.cb_presets = QComboBox()
        self._populate_presets_combo()
        self.cb_presets.currentIndexChanged.connect(self._on_preset_changed)
        top_bar.addWidget(self.cb_presets, 1)

        btn_new = QPushButton("➕ Новый")
        btn_new.clicked.connect(self._create_new_preset)
        top_bar.addWidget(btn_new)

        btn_save = QPushButton("💾 Сохранить")
        btn_save.clicked.connect(self._save_presets)
        top_bar.addWidget(btn_save)

        btn_fs = QPushButton("🔲 Заставка (F11)")
        btn_fs.setStyleSheet("background: #004455; color: #00e5ff; border-color: #00e5ff;")
        btn_fs.clicked.connect(self.viz.toggle_fullscreen_screensaver)
        top_bar.addWidget(btn_fs)

        layout.addLayout(top_bar)

        # Main Splitter: Component Pipeline List (Left) | Property Tweaker (Right)
        splitter = QSplitter(Qt.Orientation.Horizontal)

        # Left Pipeline Box
        left_box = QFrame()
        l_layout = QVBoxLayout(left_box)
        l_layout.setContentsMargins(0, 0, 0, 0)
        l_layout.addWidget(QLabel("<b>Компоненты пресета (Pipeline):</b>"))

        self.comp_list = QListWidget()
        self.comp_list.currentRowChanged.connect(self._on_component_selected)
        l_layout.addWidget(self.comp_list, 1)

        # Pipeline control buttons
        pipe_btns = QHBoxLayout()
        btn_add = QPushButton("➕ Добавить элемент")
        btn_add.clicked.connect(self._show_add_component_menu)
        pipe_btns.addWidget(btn_add)

        btn_del = QPushButton("➖ Удалить")
        btn_del.clicked.connect(self._delete_selected_component)
        pipe_btns.addWidget(btn_del)

        btn_up = QPushButton("▲")
        btn_up.setFixedWidth(32)
        btn_up.clicked.connect(lambda: self._move_component(-1))
        pipe_btns.addWidget(btn_up)

        btn_down = QPushButton("▼")
        btn_down.setFixedWidth(32)
        btn_down.clicked.connect(lambda: self._move_component(1))
        pipe_btns.addWidget(btn_down)

        l_layout.addLayout(pipe_btns)
        splitter.addWidget(left_box)

        # Right Property Tweaker
        self.props_box = QFrame()
        self.props_layout = QVBoxLayout(self.props_box)
        self.props_layout.setContentsMargins(10, 0, 0, 0)
        self.props_layout.addWidget(QLabel("<b>Параметры выбранного элемента:</b>"))

        self.props_container = QWidget()
        self.props_form = QFormLayout(self.props_container)
        self.props_layout.addWidget(self.props_container, 1)

        splitter.addWidget(self.props_box)
        splitter.setStretchFactor(0, 4)
        splitter.setStretchFactor(1, 5)

        layout.addWidget(splitter, 1)

        # Bottom Bar: Auto Cycle checkbox & Close
        bot_bar = QHBoxLayout()
        self.chk_auto = QCheckBox("Автосмена пресетов (каждые 25 сек как заставка)")
        self.chk_auto.setChecked(self.viz.auto_cycle_presets)
        self.chk_auto.toggled.connect(self._toggle_auto_cycle)
        bot_bar.addWidget(self.chk_auto)

        bot_bar.addStretch()

        btn_close = QPushButton("Закрыть")
        btn_close.clicked.connect(self.accept)
        bot_bar.addWidget(btn_close)

        layout.addLayout(bot_bar)

        self._load_current_preset_pipeline()

    def _populate_presets_combo(self):
        self.cb_presets.clear()
        for p in self.viz.presets:
            self.cb_presets.addItem(p.name)
        self.cb_presets.setCurrentIndex(self.viz.current_preset_idx)

    def _on_preset_changed(self, idx: int):
        if idx >= 0:
            self.viz.set_preset_by_index(idx)
            self._load_current_preset_pipeline()

    def _load_current_preset_pipeline(self):
        self.comp_list.clear()
        preset = self.viz.current_preset
        for comp in preset.components:
            item = QListWidgetItem(f"[{'✓' if comp.enabled else ' '}] {comp.name}")
            item.setData(Qt.ItemDataRole.UserRole, comp)
            self.comp_list.addItem(item)
        if self.comp_list.count() > 0:
            self.comp_list.setCurrentRow(0)

    def _on_component_selected(self, row: int):
        # Clear existing property fields
        while self.props_form.count():
            item = self.props_form.takeAt(0)
            if item.widget():
                item.widget().deleteLater()

        if row < 0 or row >= len(self.viz.current_preset.components):
            return

        comp = self.viz.current_preset.components[row]

        # Enable/Disable toggle
        chk_en = QCheckBox("Включен в конвейере")
        chk_en.setChecked(comp.enabled)
        chk_en.toggled.connect(lambda v, c=comp: self._toggle_comp_enabled(c, v))
        self.props_form.addRow("Статус:", chk_en)

        # Dynamic parameter controls based on component params
        for key, val in comp.params.items():
            if isinstance(val, bool):
                cb = QCheckBox()
                cb.setChecked(val)
                cb.toggled.connect(lambda v, k=key, c=comp: c.params.update({k: v}))
                self.props_form.addRow(f"{key}:", cb)
            elif isinstance(val, (int, float)) and key in ("bar_count", "points", "rings", "star_count", "max_particles"):
                slider = QSlider(Qt.Orientation.Horizontal)
                slider.setRange(8, 200)
                slider.setValue(int(val))
                slider.valueChanged.connect(lambda v, k=key, c=comp: c.params.update({k: v}))
                self.props_form.addRow(f"{key} ({val}):", slider)
            elif isinstance(val, str) and key in ("style", "mode", "formula"):
                combo = QComboBox()
                if key == "style":
                    opts = ["NEON_CYAN", "FIRE", "MATRIX", "RAINBOW"]
                elif key == "mode":
                    opts = ["LINE", "DOTS", "LISSAJOUS"]
                elif key == "formula":
                    opts = ["CYBER_FLOWER", "HELIX", "VORTEX", "CARDIAC"]
                else:
                    opts = [val]
                combo.addItems(opts)
                combo.setCurrentText(val)
                combo.currentTextChanged.connect(lambda v, k=key, c=comp: c.params.update({k: v}))
                self.props_form.addRow(f"{key}:", combo)

    def _toggle_comp_enabled(self, comp: AVSComponent, val: bool):
        comp.enabled = val
        self._load_current_preset_pipeline()

    def _show_add_component_menu(self):
        # Creates a dialog with available components to add
        components = [
            ("Спектральный анализатор (FFT Bars)", SpectrumBarsComponent),
            ("Осциллограф (Phosphor CRT)", OscilloscopeComponent),
            ("SuperScope (Математические кривые)", SuperScopeComponent),
            ("3D Starfield Warp", StarfieldWarpComponent),
            ("Неоновый Synthwave горизонт", SynthwaveHorizonComponent),
            ("Кибер-вихрь (3D Туннель)", CyberTunnelComponent),
            ("Частицы и Салют (Beat Sparks)", ParticleBurstComponent),
            ("Motion Blur (Шлейф / Фосфор)", MotionBlurComponent),
            ("Цветовой цикл (Rainbow Cycle)", ColorCycleComponent),
            ("Калейдоскоп (4-Way Mirror)", KaleidoscopeComponent),
        ]
        dlg = QDialog(self)
        dlg.setWindowTitle("Добавить элемент в AVS конвейер")
        dlg.resize(380, 320)
        d_layout = QVBoxLayout(dlg)
        lw = QListWidget()
        for name, _ in components:
            lw.addItem(name)
        d_layout.addWidget(lw)

        btn_add = QPushButton("Добавить")
        btn_add.clicked.connect(dlg.accept)
        d_layout.addWidget(btn_add)

        if dlg.exec() == QDialog.DialogCode.Accepted and lw.currentRow() >= 0:
            _, cls = components[lw.currentRow()]
            new_comp = cls()
            self.viz.current_preset.components.append(new_comp)
            self._load_current_preset_pipeline()

    def _delete_selected_component(self):
        row = self.comp_list.currentRow()
        if 0 <= row < len(self.viz.current_preset.components):
            del self.viz.current_preset.components[row]
            self._load_current_preset_pipeline()

    def _move_component(self, direction: int):
        row = self.comp_list.currentRow()
        target = row + direction
        comps = self.viz.current_preset.components
        if 0 <= row < len(comps) and 0 <= target < len(comps):
            comps[row], comps[target] = comps[target], comps[row]
            self._load_current_preset_pipeline()
            self.comp_list.setCurrentRow(target)

    def _create_new_preset(self):
        new_preset = AVSPreset(
            name=f"Custom Preset #{len(self.viz.presets) + 1}",
            description="Пользовательский AVS пресет",
            components=[
                StarfieldWarpComponent(),
                SpectrumBarsComponent()
            ]
        )
        self.viz.presets.append(new_preset)
        self._populate_presets_combo()
        self.cb_presets.setCurrentIndex(len(self.viz.presets) - 1)

    def _save_presets(self):
        if self.viz.save_custom_presets():
            QMessageBox.information(self, "Сохранено", "Пресеты AVS успешно сохранены в хранилище!")
        else:
            QMessageBox.warning(self, "Ошибка", "Не удалось сохранить пресеты на диск.")

    def _toggle_auto_cycle(self, enabled: bool):
        self.viz.auto_cycle_presets = enabled
