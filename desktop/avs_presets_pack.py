"""
MadGammon Winamp AVS 100 Presets Super-Pack & Advanced APE Components
Legendary visual effects inspired by VISBOT, Nullsoft AVS 1998-2002,
UnConeD / Acko.net, Yathosho, Rovastar, and classic PC Demoscene.
"""

import math
import random
from typing import List, Dict, Any, Tuple
from dataclasses import dataclass, field

from PyQt6.QtGui import (
    QPainter, QColor, QPen, QBrush, QLinearGradient, QRadialGradient,
    QPainterPath, QFont
)
from PyQt6.QtCore import Qt, QPointF

from avs_visualizer import (
    AVSComponent, AVSPreset, AudioSpectrumData,
    SpectrumBarsComponent, OscilloscopeComponent, SuperScopeComponent,
    StarfieldWarpComponent, SynthwaveHorizonComponent, CyberTunnelComponent,
    ParticleBurstComponent, MotionBlurComponent, ColorCycleComponent,
    KaleidoscopeComponent
)

# ═══════════════════════════════════════════════════════════════════
#  NEW ADVANCED APE / AVS RENDER COMPONENTS
# ═══════════════════════════════════════════════════════════════════

class CircularSpectrumComponent(AVSComponent):
    """Radial / Polar circular FFT spectrum ring pulsing to the beat."""
    def __init__(self):
        super().__init__("Круговой спектр (Polar Spectrum)", "RENDER")
        self.params = {
            "radius_ratio": 0.28,
            "bar_count": 64,
            "color_mode": "CYBER_CYAN",  # CYBER_CYAN, FIRE, NEON_PURPLE, RAINBOW
            "spike_scale": 1.0,
            "rotate_speed": 0.25,
        }

    def render(self, painter: QPainter, width: int, height: int, audio: AudioSpectrumData, dt: float, t: float):
        if not self.enabled:
            return

        cx, cy = width / 2.0, height / 2.0
        base_r = min(width, height) * float(self.params.get("radius_ratio", 0.28))
        base_r += audio.bass * 25.0
        bar_count = int(self.params.get("bar_count", 64))
        rot = t * float(self.params.get("rotate_speed", 0.25))
        mode = self.params.get("color_mode", "CYBER_CYAN")
        scale = float(self.params.get("spike_scale", 1.0)) * 90.0

        step_angle = (2.0 * math.pi) / bar_count
        step_band = max(1, len(audio.bands) // bar_count)

        for i in range(bar_count):
            ang = rot + i * step_angle
            b_idx = min(len(audio.bands) - 1, i * step_band)
            val = audio.bands[b_idx]
            spike = val * scale

            x1 = cx + math.cos(ang) * base_r
            y1 = cy + math.sin(ang) * base_r
            x2 = cx + math.cos(ang) * (base_r + spike)
            y2 = cy + math.sin(ang) * (base_r + spike)

            if mode == "FIRE":
                col = QColor(255, int(180 * (1.0 - val)), 20, 220)
            elif mode == "NEON_PURPLE":
                col = QColor(int(180 + 75 * val), 0, 255, 230)
            elif mode == "RAINBOW":
                hue = int((i / bar_count * 360 + t * 60) % 360)
                col = QColor.fromHsv(hue, 220, 255, 230)
            else:
                col = QColor(0, int(200 + 55 * val), 255, 230)

            pen = QPen(col, 2.5)
            pen.setCapStyle(Qt.PenCapStyle.RoundCap)
            painter.setPen(pen)
            painter.drawLine(QPointF(x1, y1), QPointF(x2, y2))


class VUMeterNeedleComponent(AVSComponent):
    """Dual analogue studio VU meter with warm bulb glow and reactive needles."""
    def __init__(self):
        super().__init__("Стрелочный индикатор (Analogue VU Meter)", "RENDER")
        self.params = {
            "style": "WARM_AMBER",  # WARM_AMBER, CYBER_BLUE, MATRIX_GREEN
            "needle_smoothness": 0.85,
        }
        self._left_val = 0.0
        self._right_val = 0.0

    def render(self, painter: QPainter, width: int, height: int, audio: AudioSpectrumData, dt: float, t: float):
        if not self.enabled:
            return

        target_l = min(1.0, audio.bass * 1.3 + audio.mids * 0.4)
        target_r = min(1.0, audio.mids * 0.9 + audio.highs * 1.2)
        sm = float(self.params.get("needle_smoothness", 0.85))
        self._left_val = self._left_val * sm + target_l * (1.0 - sm)
        self._right_val = self._right_val * sm + target_r * (1.0 - sm)

        style = self.params.get("style", "WARM_AMBER")
        if style == "CYBER_BLUE":
            bg_col = QColor(10, 20, 35, 180)
            arc_col = QColor(0, 229, 255)
            needle_col = QColor(255, 255, 255)
        elif style == "MATRIX_GREEN":
            bg_col = QColor(5, 25, 10, 180)
            arc_col = QColor(0, 255, 100)
            needle_col = QColor(200, 255, 200)
        else:
            bg_col = QColor(35, 25, 10, 180)
            arc_col = QColor(255, 180, 40)
            needle_col = QColor(255, 60, 40)

        meter_w = min(180.0, width * 0.28)
        meter_h = meter_w * 0.65
        y_pos = height - meter_h - 14.0

        for idx, (x_center, val, lbl) in enumerate([
            (width * 0.32, self._left_val, "LEFT VU"),
            (width * 0.68, self._right_val, "RIGHT VU")
        ]):
            rect_x = x_center - meter_w / 2.0
            painter.setBrush(QBrush(bg_col))
            painter.setPen(QPen(arc_col, 1.5))
            painter.drawRoundedRect(int(rect_x), int(y_pos), int(meter_w), int(meter_h), 6, 6)

            # Arc
            arc_r = meter_w * 0.42
            arc_cx, arc_cy = x_center, y_pos + meter_h * 0.88
            painter.setPen(QPen(QColor(80, 80, 80, 150), 2.0))
            painter.drawArc(int(arc_cx - arc_r), int(arc_cy - arc_r), int(arc_r * 2), int(arc_r * 2), 45 * 16, 90 * 16)

            # Needle: -45 deg to +45 deg
            deg = -45.0 + val * 90.0
            rad = math.radians(deg - 90.0)
            nx = arc_cx + math.cos(rad) * (arc_r * 0.95)
            ny = arc_cy + math.sin(rad) * (arc_r * 0.95)

            painter.setPen(QPen(needle_col, 2.0))
            painter.drawLine(QPointF(arc_cx, arc_cy), QPointF(nx, ny))

            # Pivot
            painter.setBrush(QBrush(needle_col))
            painter.drawEllipse(QPointF(arc_cx, arc_cy), 4.0, 4.0)

            # Label
            painter.setPen(QPen(arc_col))
            painter.setFont(QFont("Monospace", 7, QFont.Weight.Bold))
            painter.drawText(int(rect_x + 6), int(y_pos + 14), lbl)


class MatrixRainComponent(AVSComponent):
    """Digital green Matrix code drops pulsing on bass hits."""
    def __init__(self):
        super().__init__("Матричный дождь (Matrix Rain)", "RENDER")
        self.params = {
            "density": 28,
            "char_speed": 1.2,
            "color": "PHOSPHOR_GREEN",  # PHOSPHOR_GREEN, CYAN_CODE, GOLD_DATA
        }
        self._drops: List[List[float]] = []  # [x, y, speed, char_idx]

    def _init_drops(self, width: int):
        density = int(self.params.get("density", 28))
        self._drops = []
        step = width / max(1, density)
        for i in range(density):
            x = i * step + step * 0.5
            y = random.uniform(-100, 300)
            spd = random.uniform(80, 220)
            self._drops.append([x, y, spd, random.randint(0x30A0, 0x30FF)])

    def render(self, painter: QPainter, width: int, height: int, audio: AudioSpectrumData, dt: float, t: float):
        if not self.enabled:
            return

        if not self._drops or len(self._drops) != int(self.params.get("density", 28)):
            self._init_drops(width)

        col_mode = self.params.get("color", "PHOSPHOR_GREEN")
        speed_mod = float(self.params.get("char_speed", 1.2)) * (1.0 + audio.bass * 2.0)

        painter.setFont(QFont("Monospace", 9, QFont.Weight.Bold))

        for d in self._drops:
            d[1] += d[2] * dt * speed_mod
            if d[1] > height + 40:
                d[1] = random.uniform(-40, 0)
                d[3] = random.randint(0x30A0, 0x30FF)

            lead_char = chr(int(d[3]))
            x = d[0]
            y = d[1]

            # Trailing glow
            for trail_idx in range(6):
                ty = y - trail_idx * 14.0
                if ty < 0 or ty > height:
                    continue
                alpha = max(20, int(255 - trail_idx * 40))
                if col_mode == "CYAN_CODE":
                    c = QColor(0, 220, 255, alpha) if trail_idx > 0 else QColor(255, 255, 255, 255)
                elif col_mode == "GOLD_DATA":
                    c = QColor(255, 190, 40, alpha) if trail_idx > 0 else QColor(255, 255, 200, 255)
                else:
                    c = QColor(0, 255, 70, alpha) if trail_idx > 0 else QColor(220, 255, 220, 255)

                painter.setPen(QPen(c))
                painter.drawText(int(x), int(ty), lead_char)


class SpirographLaserComponent(AVSComponent):
    """Laser club spirograph (hypotrochoid) with spinning harmonic beams."""
    def __init__(self):
        super().__init__("Лазерный спирограф (Laser Spirograph)", "RENDER")
        self.params = {
            "R": 110.0,
            "r": 42.0,
            "d": 75.0,
            "beams": 1,
            "color_style": "LASER_NEON",
        }

    def render(self, painter: QPainter, width: int, height: int, audio: AudioSpectrumData, dt: float, t: float):
        if not self.enabled:
            return

        cx, cy = width / 2.0, height / 2.0
        R = float(self.params.get("R", 110.0)) * (1.0 + audio.bass * 0.4)
        r = float(self.params.get("r", 42.0))
        d = float(self.params.get("d", 75.0)) * (1.0 + audio.highs * 0.5)

        points = []
        steps = 180
        rot = t * 1.5

        for i in range(steps):
            theta = (i / steps) * (math.pi * 6.0) + rot
            diff = R - r
            x = cx + diff * math.cos(theta) + d * math.cos(diff * theta / r)
            y = cy + diff * math.sin(theta) - d * math.sin(diff * theta / r)
            points.append(QPointF(x, y))

        if len(points) > 1:
            path = QPainterPath(points[0])
            for pt in points[1:]:
                path.lineTo(pt)

            style = self.params.get("color_style", "LASER_NEON")
            if style == "LASER_GREEN":
                col = QColor(0, 255, 100, 200)
            elif style == "LASER_PINK":
                col = QColor(255, 0, 150, 200)
            else:
                hue = int((t * 50) % 360)
                col = QColor.fromHsv(hue, 220, 255, 220)

            pen = QPen(col, 2.0)
            painter.setPen(pen)
            painter.setBrush(Qt.BrushStyle.NoBrush)
            painter.drawPath(path)


class ShockwaveRingsComponent(AVSComponent):
    """Expanding audio shockwaves emitted on bass kick drums."""
    def __init__(self):
        super().__init__("Ударные звуковые волны (Shockwaves)", "RENDER")
        self.params = {
            "max_rings": 5,
            "color": "CYAN_WAVE",
        }
        self._rings: List[List[float]] = []  # [radius, alpha, growth_rate]

    def render(self, painter: QPainter, width: int, height: int, audio: AudioSpectrumData, dt: float, t: float):
        if not self.enabled:
            return

        cx, cy = width / 2.0, height / 2.0
        max_r = min(width, height) * 0.65

        # Trigger new ring on beat
        if audio.beat_detected and len(self._rings) < int(self.params.get("max_rings", 5)):
            self._rings.append([10.0, 255.0, 180.0 + audio.bass * 200.0])

        new_rings = []
        painter.setBrush(Qt.BrushStyle.NoBrush)

        for r, alpha, spd in self._rings:
            r += spd * dt
            alpha -= dt * 140.0
            if r < max_r and alpha > 5:
                new_rings.append([r, alpha, spd])

                c_type = self.params.get("color", "CYAN_WAVE")
                if c_type == "RED_BLAST":
                    col = QColor(255, 40, 20, int(alpha))
                elif c_type == "GOLD_PULSE":
                    col = QColor(255, 200, 0, int(alpha))
                else:
                    col = QColor(0, 229, 255, int(alpha))

                painter.setPen(QPen(col, 2.5))
                painter.drawEllipse(QPointF(cx, cy), r, r)

        self._rings = new_rings


class WireframeMeshComponent(AVSComponent):
    """3D wireframe polyhedral crystal mesh (classic APE Texer / Demoscene)."""
    def __init__(self):
        super().__init__("3D Полигональный кристалл (Wireframe Mesh)", "RENDER")
        self.params = {
            "faces": 8,
            "rotation_speed": 0.8,
            "wire_color": "CYBER_GOLD",
        }

    def render(self, painter: QPainter, width: int, height: int, audio: AudioSpectrumData, dt: float, t: float):
        if not self.enabled:
            return

        cx, cy = width / 2.0, height / 2.0
        size = min(width, height) * 0.22 * (1.0 + audio.bass * 0.45)
        rot_y = t * float(self.params.get("rotation_speed", 0.8))
        rot_x = t * 0.5

        # 3D vertices of an octahedron
        verts_3d = [
            (0, -1, 0), (1, 0, 0), (0, 0, 1), (-1, 0, 0), (0, 0, -1), (0, 1, 0)
        ]
        edges = [
            (0,1), (0,2), (0,3), (0,4),
            (5,1), (5,2), (5,3), (5,4),
            (1,2), (2,3), (3,4), (4,1)
        ]

        # Rotate & project
        proj_pts = []
        for x, y, z in verts_3d:
            # Rot Y
            x1 = x * math.cos(rot_y) + z * math.sin(rot_y)
            z1 = -x * math.sin(rot_y) + z * math.cos(rot_y)
            # Rot X
            y2 = y * math.cos(rot_x) - z1 * math.sin(rot_x)
            z2 = y * math.sin(rot_x) + z1 * math.cos(rot_x)

            # Perspective
            dist = 3.0
            fov = dist / (dist + z2 * 0.6)
            px = cx + x1 * size * fov
            py = cy + y2 * size * fov
            proj_pts.append(QPointF(px, py))

        col_style = self.params.get("wire_color", "CYBER_GOLD")
        if col_style == "NEON_BLUE":
            col = QColor(0, 220, 255, 230)
        elif col_style == "EMERALD":
            col = QColor(0, 255, 120, 230)
        else:
            col = QColor(255, 190, 40, 230)

        painter.setPen(QPen(col, 2.0))
        for i1, i2 in edges:
            painter.drawLine(proj_pts[i1], proj_pts[i2])


class WaveformRibbonComponent(AVSComponent):
    """Floating 3D multi-layered audio waveform ribbon."""
    def __init__(self):
        super().__init__("Парящая волновая лента (Waveform Ribbon)", "RENDER")
        self.params = {
            "ribbon_layers": 3,
            "style": "PRISM",
        }

    def render(self, painter: QPainter, width: int, height: int, audio: AudioSpectrumData, dt: float, t: float):
        if not self.enabled:
            return

        layers = int(self.params.get("ribbon_layers", 3))
        cy = height * 0.5
        steps = 40
        dx = width / steps

        for lay in range(layers):
            offset_y = (lay - layers / 2.0) * 22.0
            phase = t * 2.0 + lay * 0.7
            path = QPainterPath()

            first = True
            for i in range(steps + 1):
                x = i * dx
                b_idx = min(len(audio.bands) - 1, int(i / steps * len(audio.bands)))
                amp = audio.bands[b_idx] * (height * 0.28)
                y = cy + offset_y + math.sin(i * 0.4 + phase) * amp

                if first:
                    path.moveTo(x, y)
                    first = False
                else:
                    path.lineTo(x, y)

            hue = int((t * 40 + lay * 60) % 360)
            col = QColor.fromHsv(hue, 200, 255, 160)
            pen = QPen(col, 2.5)
            painter.setPen(pen)
            painter.setBrush(Qt.BrushStyle.NoBrush)
            painter.drawPath(path)


# ═══════════════════════════════════════════════════════════════════
#  REGISTER EXTENDED COMPONENTS
# ═══════════════════════════════════════════════════════════════════

EXTENDED_COMPONENT_REGISTRY = {
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
    "CircularSpectrumComponent": CircularSpectrumComponent,
    "VUMeterNeedleComponent": VUMeterNeedleComponent,
    "MatrixRainComponent": MatrixRainComponent,
    "SpirographLaserComponent": SpirographLaserComponent,
    "ShockwaveRingsComponent": ShockwaveRingsComponent,
    "WireframeMeshComponent": WireframeMeshComponent,
    "WaveformRibbonComponent": WaveformRibbonComponent,
}


# ═══════════════════════════════════════════════════════════════════
#  THE 100 WINAMP AVS SUPER-PACK GENERATOR
# ═══════════════════════════════════════════════════════════════════

def get_100_avs_presets() -> List[AVSPreset]:
    """
    Generates a massive catalogue of 100 distinct Winamp AVS Presets
    spanning 7 thematic categories:
    1. Nullsoft Classic 1998-2002 (15)
    2. VISBOT & Demoscene Masterpieces (15)
    3. Cyberpunk & Synthwave (15)
    4. Analog Laboratory & VU Meters (15)
    5. Psychedelic & Sacred Geometry (15)
    6. Deep Space & Hyperspace Flight (15)
    7. Quantum Physics, Cybernetics & DNA (10)
    """
    presets: List[AVSPreset] = []

    # ─── 1. NULLSOFT CLASSIC (1-15) ───
    classic_configs = [
        ("Classic Cyan FFT", "NEON_CYAN", "LINE", 32),
        ("Firestorm Spectrum", "FIRE", "DOTS", 48),
        ("Phosphor CRT 1998", "MATRIX", "LINE", 24),
        ("Rainbow Dance Party", "RAINBOW", "LINE", 64),
        ("Subwoofer Shockwave", "NEON_CYAN", "LINE", 16),
        ("Dual Wave Oscilloscope", "MATRIX", "LISSAJOUS", 32),
        ("Mirror Neon Waterfall", "NEON_CYAN", "LINE", 40),
        ("Studio Peak Hold", "FIRE", "DOTS", 32),
        ("Vintage Radio Tape", "RAINBOW", "LINE", 28),
        ("Acid Trance Party", "FIRE", "LISSAJOUS", 48),
        ("Golden Age 2000", "FIRE", "LINE", 50),
        ("Monochrome Precision", "MATRIX", "DOTS", 32),
        ("Stereo Channel Split", "NEON_CYAN", "LINE", 64),
        ("Bass Cannon 808", "FIRE", "LINE", 20),
        ("Midnight Chillout", "RAINBOW", "LINE", 36),
    ]
    for idx, (name, style, osc_mode, bars) in enumerate(classic_configs, 1):
        sb = SpectrumBarsComponent()
        sb.params["style"] = style
        sb.params["bar_count"] = bars
        osc = OscilloscopeComponent()
        osc.params["mode"] = osc_mode
        p = AVSPreset(
            name=f"⚡ [{idx:02d}/100] Classic Winamp: {name}",
            description="Оригинальный дух классического визуализатора Nullsoft Winamp 2.x",
            components=[sb, osc]
        )
        if idx % 3 == 0:
            p.components.append(ParticleBurstComponent())
        presets.append(p)

    # ─── 2. VISBOT & DEMOSCENE (16-30) ───
    visbot_configs = [
        ("VISBOT Vector Crystal", "CYBER_GOLD", 1.2),
        ("Acko Texer Mesh", "NEON_BLUE", 0.9),
        ("Yathosho Demoscene Engine", "EMERALD", 1.5),
        ("Rovastar Pulse Matrix", "CYBER_GOLD", 0.7),
        ("UnConeD Cyber Monolith", "NEON_BLUE", 1.8),
        ("Farbrausch 64k Intro", "EMERALD", 1.0),
        ("Assembly '99 Winner", "CYBER_GOLD", 1.4),
        ("The Party Polygon Warp", "NEON_BLUE", 0.8),
        ("Future Crew Starflight", "EMERALD", 1.6),
        ("Second Reality Reboot", "CYBER_GOLD", 1.1),
        ("Sub-Atomic Geometry", "NEON_BLUE", 1.3),
        ("Silicon Dream Core", "EMERALD", 0.9),
        ("Vertex Shader 2002", "CYBER_GOLD", 1.7),
        ("Demoscene Hall of Fame", "NEON_BLUE", 1.0),
        ("VISBOT Grand Finale", "EMERALD", 2.0),
    ]
    for idx, (name, col, spd) in enumerate(visbot_configs, 16):
        mesh = WireframeMeshComponent()
        mesh.params["wire_color"] = col
        mesh.params["rotation_speed"] = spd
        tunnel = CyberTunnelComponent()
        p = AVSPreset(
            name=f"🏆 [{idx:02d}/100] Demoscene: {name}",
            description="Сложнейшие математические структуры в традициях культовых демосцен-паков",
            components=[tunnel, mesh, ParticleBurstComponent()]
        )
        presets.append(p)

    # ─── 3. CYBERPUNK & SYNTHWAVE (31-45) ───
    synth_configs = [
        ("Outrun Sunset Highway", "CYBER_CYAN", 1.0),
        ("Tokyo Neon Rain 2077", "CYAN_CODE", 1.4),
        ("Kavinsky Nightcall", "FIRE", 0.8),
        ("Blade Runner Spinner", "NEON_PURPLE", 1.2),
        ("Chiba City Data Terminal", "GOLD_DATA", 1.5),
        ("Cyber Grid Megastructure", "CYBER_CYAN", 0.9),
        ("Vaporwave Mall 1992", "RAINBOW", 0.7),
        ("Retrofuturism Miami", "FIRE", 1.1),
        ("Gibson Matrix Deck", "PHOSPHOR_GREEN", 1.6),
        ("Neo-Seoul Hologram", "NEON_PURPLE", 1.0),
        ("Arcade Laser 1984", "FIRE", 1.3),
        ("Night City Subwoofer", "CYBER_CYAN", 1.2),
        ("Cyberdeck Console", "PHOSPHOR_GREEN", 1.4),
        ("Laser Highway Rush", "NEON_PURPLE", 0.9),
        ("End of the Grid", "FIRE", 1.5),
    ]
    for idx, (name, col_mode, spd) in enumerate(synth_configs, 31):
        horizon = SynthwaveHorizonComponent()
        m_rain = MatrixRainComponent()
        m_rain.params["color"] = col_mode
        m_rain.params["char_speed"] = spd
        p = AVSPreset(
            name=f"🌆 [{idx:02d}/100] Cyberpunk: {name}",
            description="Неоновый ретровейв, киберпанк и цифровой дождь в ритме бита",
            components=[horizon, m_rain, SpectrumBarsComponent()]
        )
        presets.append(p)

    # ─── 4. ANALOG LABORATORY & VU METERS (46-60) ───
    lab_configs = [
        ("Nixie & Amber VU Meters", "WARM_AMBER", "LISSAJOUS"),
        ("Tektronix Green Phosphor", "MATRIX_GREEN", "LINE"),
        ("Studer A820 Reel-to-Reel", "WARM_AMBER", "CIRCULAR"),
        ("Soviet Radio Station 1974", "WARM_AMBER", "LINE"),
        ("Precision Audio Testbench", "CYBER_BLUE", "LISSAJOUS"),
        ("Tube Amplifier Glow", "WARM_AMBER", "CIRCULAR"),
        ("Mastering Studio Console", "CYBER_BLUE", "LINE"),
        ("Analog Tape Saturation", "WARM_AMBER", "DOTS"),
        ("Laboratory Oscillograph", "MATRIX_GREEN", "LISSAJOUS"),
        ("Broadcast Transmitter", "CYBER_BLUE", "LINE"),
        ("Vintage Ham Radio", "WARM_AMBER", "DOTS"),
        ("Oscilloscope Harmonic Test", "MATRIX_GREEN", "CIRCULAR"),
        ("Stereo Level Balancer", "CYBER_BLUE", "LISSAJOUS"),
        ("Vacuum Tube Pre-Amp", "WARM_AMBER", "LINE"),
        ("Military Radar Scope", "MATRIX_GREEN", "CIRCULAR"),
    ]
    for idx, (name, vu_style, osc_style) in enumerate(lab_configs, 46):
        vu = VUMeterNeedleComponent()
        vu.params["style"] = vu_style
        osc = OscilloscopeComponent()
        osc.params["mode"] = osc_style
        p = AVSPreset(
            name=f"📟 [{idx:02d}/100] Lab & VU: {name}",
            description="Аналоговые студийные приборы, ламповые индикаторы и осциллографы",
            components=[osc, vu]
        )
        presets.append(p)

    # ─── 5. PSYCHEDELIC & SACRED GEOMETRY (61-75) ───
    psych_configs = [
        ("Mandala Laser Spirograph", "LASER_NEON", 120.0, 48.0),
        ("Hypotrochoid Warp Drive", "LASER_PINK", 140.0, 36.0),
        ("Sacred Geometry Portal", "LASER_GREEN", 100.0, 50.0),
        ("Kaleidoscopic Flower", "LASER_NEON", 160.0, 60.0),
        ("DMT Hyperspace Crystal", "LASER_PINK", 110.0, 44.0),
        ("Tibetan Wheel of Time", "LASER_GREEN", 130.0, 52.0),
        ("Golden Ratio Vortex", "LASER_NEON", 115.0, 38.0),
        ("Chakra Alignment Pulse", "LASER_PINK", 150.0, 45.0),
        ("Metatron Cube Laser", "LASER_GREEN", 125.0, 40.0),
        ("Fractal Infinity Mirror", "LASER_NEON", 170.0, 55.0),
        ("Trance Portal 140 BPM", "LASER_PINK", 135.0, 42.0),
        ("Shamanic Vision Beam", "LASER_GREEN", 105.0, 35.0),
        ("Cosmic Yantra Pulsar", "LASER_NEON", 145.0, 48.0),
        ("Psychedelic Mushroom", "LASER_PINK", 120.0, 50.0),
        ("Enlightenment Spire", "LASER_GREEN", 160.0, 40.0),
    ]
    for idx, (name, col, R, r) in enumerate(psych_configs, 61):
        spiro = SpirographLaserComponent()
        spiro.params["color_style"] = col
        spiro.params["R"] = R
        spiro.params["r"] = r
        circ = CircularSpectrumComponent()
        circ.params["color_mode"] = "RAINBOW" if idx % 2 == 0 else "NEON_PURPLE"
        p = AVSPreset(
            name=f"🌀 [{idx:02d}/100] Sacred: {name}",
            description="Сакральная геометрия, лазерные спирографы и гипнотические мандалы",
            components=[spiro, circ, KaleidoscopeComponent()]
        )
        presets.append(p)

    # ─── 6. DEEP SPACE & HYPERSPACE (76-90) ───
    space_configs = [
        ("Andromeda Galactic Core", "CYAN_WAVE", 250),
        ("Wormhole Event Horizon", "RED_BLAST", 350),
        ("Supernova Shockwave", "GOLD_PULSE", 400),
        ("Solar Flare Eruption", "RED_BLAST", 300),
        ("Orion Nebula Flight", "CYAN_WAVE", 200),
        ("Pulsar Beam Lighthouse", "GOLD_PULSE", 450),
        ("Black Hole Singularity", "CYAN_WAVE", 500),
        ("Interstellar Warp 9", "GOLD_PULSE", 350),
        ("Saturn Rings Resonance", "CYAN_WAVE", 220),
        ("Dark Matter Filament", "RED_BLAST", 280),
        ("Voyager Golden Record", "GOLD_PULSE", 180),
        ("Cosmic Microwave Echo", "CYAN_WAVE", 320),
        ("Quasar Radiation Jet", "RED_BLAST", 420),
        ("Stargate Transit Vortex", "GOLD_PULSE", 380),
        ("Edge of the Observable Universe", "CYAN_WAVE", 480),
    ]
    for idx, (name, wave_col, star_count) in enumerate(space_configs, 76):
        stars = StarfieldWarpComponent()
        stars.params["star_count"] = star_count
        shock = ShockwaveRingsComponent()
        shock.params["color"] = wave_col
        circ = CircularSpectrumComponent()
        circ.params["color_mode"] = "FIRE" if wave_col == "RED_BLAST" else "CYBER_CYAN"
        p = AVSPreset(
            name=f"🚀 [{idx:02d}/100] Space: {name}",
            description="Глубокий космос, ударные волны сверхновых и гиперпрыжки сквозь звезды",
            components=[stars, shock, circ]
        )
        presets.append(p)

    # ─── 7. QUANTUM, CYBERNETICS & DNA (91-100) ───
    quantum_configs = [
        ("DNA Double Helix Synthesis", "HELIX", 4),
        ("Quantum Ribbon Wavepacket", "LISSAJOUS", 3),
        ("Neural Synapse Firing", "VORTEX", 5),
        ("Cybernetic Cortex Pulse", "ROSE", 3),
        ("Electron Probability Cloud", "SPIRAL", 4),
        ("Higgs Boson Particle Track", "BUTTERFLY", 5),
        ("Nanotech Assembly Mesh", "HELIX", 3),
        ("Bioluminescent Deep Sea", "ROSE", 4),
        ("Consciousness Quantum Leap", "VORTEX", 5),
        ("Sovereign Cyber Masterpiece", "HELIX", 6),
    ]
    for idx, (name, formula, ribbons) in enumerate(quantum_configs, 91):
        superscope = SuperScopeComponent()
        superscope.params["formula"] = formula
        ribbon = WaveformRibbonComponent()
        ribbon.params["ribbon_layers"] = ribbons
        p = AVSPreset(
            name=f"🧬 [{idx:02d}/100] Quantum: {name}",
            description="Квантовая физика, нити ДНК и нейросетевые биомеханические структуры",
            components=[superscope, ribbon, ParticleBurstComponent()]
        )
        presets.append(p)

    return presets
