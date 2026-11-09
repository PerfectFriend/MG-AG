#!/usr/bin/env python3
"""
Generates an artistic Cyberpunk + Van Gogh + Antoni Gaudí fusion banner
for MadGammon Desktop.

Artistic elements:
  - Van Gogh: Swirling turbulent vortices, luminous golden stars, expressive brush strokes.
  - Antoni Gaudí: Parabolic catenary arches, organic serpentine contours,
                  and Catalan 'trencadís' mosaic tessellation.
  - Cyberpunk: Neon cyan/emerald glowing circuit traces, digital data nodes,
               high-tech typography and holographic lighting.
"""

import math
import random
import os
from PIL import Image, ImageDraw, ImageFont, ImageFilter

def create_art_banner(width=800, height=220, output_path="desktop/assets/banner_cyber_gogh_gaudi.png"):
    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    random.seed(42)

    # Base image in RGBA
    img = Image.new("RGBA", (width, height), (15, 15, 26, 255))
    draw = ImageDraw.Draw(img)

    # 1. Deep Midnight Cosmic & Cobalt Gradient (Van Gogh night sky)
    for y in range(height):
        t = y / float(height)
        r = int(10 * (1 - t) + 24 * t)
        g = int(12 * (1 - t) + 32 * t)
        b = int(35 * (1 - t) + 68 * t)
        draw.line([(0, y), (width, y)], fill=(r, g, b, 255))

    # 2. Gaudí Organic Undulating Strata & Parabolic Arches
    # Draw catenary arch curves in deep teal and terracotta
    arch_layer = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    arch_draw = ImageDraw.Draw(arch_layer)

    for i in range(7):
        cx = width * (0.15 + i * 0.12)
        arch_w = 70 + (i % 3) * 25
        arch_h = height * (0.65 + (i % 2) * 0.25)
        # Parabolic arch points
        arch_pts = []
        for step in range(50):
            st = step / 49.0
            x = cx - arch_w / 2 + st * arch_w
            # Parabola: y = h * (1 - (2st - 1)^2)
            y = height - arch_h * (1.0 - (2 * st - 1) ** 2)
            arch_pts.append((x, y))

        for j in range(len(arch_pts) - 1):
            arch_draw.line([arch_pts[j], arch_pts[j+1]], fill=(0, 200, 180, 45), width=3)

    img = Image.alpha_composite(img, arch_layer)

    # 3. Gaudí 'Trencadís' Mosaic Tile Mosaic Field
    # Scattered polygonal ceramic fragments with rich glazes
    mosaic_layer = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    m_draw = ImageDraw.Draw(mosaic_layer)

    mosaic_palette = [
        (212, 175, 55, 110),   # Gold
        (0, 220, 180, 100),    # Turquoise
        (220, 80, 60, 90),     # Terracotta red
        (30, 140, 240, 100),   # Cobalt blue
        (0, 255, 160, 85),     # Emerald
        (240, 220, 160, 110),  # Ivory
    ]

    for _ in range(140):
        mx = random.uniform(0, width)
        my = random.uniform(height * 0.35, height)
        size = random.uniform(8, 22)
        # Irregular polygon (trencadís fragment)
        num_v = random.randint(3, 5)
        poly = []
        for v in range(num_v):
            ang = (v / float(num_v)) * 2 * math.pi + random.uniform(-0.3, 0.3)
            r_v = size * random.uniform(0.6, 1.2)
            poly.append((mx + r_v * math.cos(ang), my + r_v * math.sin(ang)))
        col = random.choice(mosaic_palette)
        m_draw.polygon(poly, fill=col, outline=(0, 255, 255, 60))

    img = Image.alpha_composite(img, mosaic_layer)

    # 4. Van Gogh Swirling Golden Vortices (Starry Night spirals)
    vortex_layer = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    v_draw = ImageDraw.Draw(vortex_layer)

    centers = [
        (width * 0.22, height * 0.42, 65),
        (width * 0.78, height * 0.38, 55),
        (width * 0.50, height * 0.65, 45),
        (width * 0.92, height * 0.75, 38),
    ]

    for (vcx, vcy, radius) in centers:
        # Spiral arms
        for arm in range(4):
            arm_offset = arm * (math.pi / 2.0)
            pts = []
            for step in range(60):
                theta = step * 0.14 + arm_offset
                r = (step / 60.0) * radius
                px = vcx + r * math.cos(theta)
                py = vcy + r * math.sin(theta) * 0.75
                pts.append((px, py))

            for k in range(len(pts) - 1):
                col_t = k / len(pts)
                r_c = int(255 * (1 - col_t) + 212 * col_t)
                g_c = int(215 * (1 - col_t) + 140 * col_t)
                b_c = int(30 * (1 - col_t) + 255 * col_t)
                alpha = int(140 * col_t)
                v_draw.line([pts[k], pts[k+1]], fill=(r_c, g_c, b_c, alpha), width=2)

        # Luminous golden star center with halo
        halo = Image.new("RGBA", (width, height), (0, 0, 0, 0))
        h_draw = ImageDraw.Draw(halo)
        h_draw.ellipse(
            [vcx - radius * 0.35, vcy - radius * 0.35, vcx + radius * 0.35, vcy + radius * 0.35],
            fill=(255, 230, 80, 180)
        )
        h_draw.ellipse(
            [vcx - radius * 0.15, vcy - radius * 0.15, vcx + radius * 0.15, vcy + radius * 0.15],
            fill=(255, 255, 240, 240)
        )
        vortex_layer = Image.alpha_composite(vortex_layer, halo)

    img = Image.alpha_composite(img, vortex_layer)

    # 5. Cyberpunk Neon Circuits & Holographic Vector Geometry
    cyber_layer = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    c_draw = ImageDraw.Draw(cyber_layer)

    # Horizontal glowing neon circuit buslines
    circuit_y = [25, 45, height - 35, height - 15]
    for cy in circuit_y:
        c_draw.line([(0, cy), (width, cy)], fill=(0, 230, 255, 80), width=1)
        # Node pads
        for nx in range(30, width, 65):
            c_draw.rectangle([nx - 2, cy - 2, nx + 2, cy + 2], fill=(0, 255, 255, 170))

    # Diagonal bus traces connecting circuits
    traces = [
        [(50, 25), (85, 60), (220, 60)],
        [(width - 70, 25), (width - 110, 65), (width - 240, 65)],
        [(120, height - 15), (150, height - 45), (320, height - 45)],
        [(width - 140, height - 15), (width - 180, height - 55), (width - 340, height - 55)],
    ]
    for tr in traces:
        for m in range(len(tr) - 1):
            c_draw.line([tr[m], tr[m+1]], fill=(0, 255, 200, 120), width=2)
            c_draw.ellipse([tr[m][0]-3, tr[m][1]-3, tr[m][0]+3, tr[m][1]+3], fill=(0, 255, 255, 200))

    img = Image.alpha_composite(img, cyber_layer)

    # 6. Subtle Cyber Scanlines & Vignette
    scanline_layer = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    s_draw = ImageDraw.Draw(scanline_layer)
    for sy in range(0, height, 4):
        s_draw.line([(0, sy), (width, sy)], fill=(0, 0, 0, 30), width=1)
    img = Image.alpha_composite(img, scanline_layer)

    # 7. Typography (Rendered with drop shadow & neon glow)
    text_layer = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    t_draw = ImageDraw.Draw(text_layer)

    font_large = None
    font_sub = None
    font_badge = None

    # Try loading truetype fonts
    font_paths = [
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
        "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf",
        "/usr/share/fonts/truetype/freefont/FreeSansBold.ttf",
    ]
    for fp in font_paths:
        if os.path.exists(fp):
            try:
                font_large = ImageFont.truetype(fp, 32)
                font_sub = ImageFont.truetype(fp, 13)
                font_badge = ImageFont.truetype(fp, 11)
                break
            except Exception:
                pass

    if font_large is None:
        font_large = ImageFont.load_default()
        font_sub = ImageFont.load_default()
        font_badge = ImageFont.load_default()

    # Center texts
    title_text = "MADGAMMON"
    sub_text = "ШЕШ-БЕШ • CYBER-ORGANIC TACTIC"

    # Glow for title
    tx = int(width / 2.0 - 110)
    ty = int(height * 0.22)

    # Multi-pass shadow for neon bloom
    for ox, oy, alpha in [(-2, -2, 40), (2, 2, 40), (0, 0, 80), (-1, 1, 60)]:
        t_draw.text((tx + ox, ty + oy), title_text, font=font_large, fill=(0, 230, 255, alpha))
    # Crisp core text
    t_draw.text((tx, ty), title_text, font=font_large, fill=(255, 255, 255, 240))

    # Subtitle in warm Van Gogh gold
    sx = int(width / 2.0 - 145)
    sy = ty + 42
    t_draw.text((sx, sy), sub_text, font=font_sub, fill=(245, 205, 75, 230))

    # Badges: "VAN GOGH • GAUDÍ • CYBERPUNK"
    badge_txt = "ORGANIC GAUDÍ VORTEX • E2EE SOVEREIGN ENGINE"
    bx = int(width / 2.0 - 150)
    by = sy + 24
    t_draw.text((bx, by), badge_txt, font=font_badge, fill=(0, 255, 180, 190))

    img = Image.alpha_composite(img, text_layer)

    # 8. Border Frame
    f_draw = ImageDraw.Draw(img)
    f_draw.rectangle([0, 0, width - 1, height - 1], outline=(0, 220, 255, 180), width=2)
    # Corner brackets
    cl = 16
    f_draw.line([(0, cl), (0, 0), (cl, 0)], fill=(255, 215, 60, 255), width=3)
    f_draw.line([(width - cl, 0), (width - 1, 0), (width - 1, cl)], fill=(255, 215, 60, 255), width=3)
    f_draw.line([(0, height - cl), (0, height - 1), (cl, height - 1)], fill=(255, 215, 60, 255), width=3)
    f_draw.line([(width - cl, height - 1), (width - 1, height - 1), (width - 1, height - cl)], fill=(255, 215, 60, 255), width=3)

    img.save(output_path, "PNG")
    print(f"Art banner saved successfully to {output_path} ({width}x{height})")
    return output_path

if __name__ == "__main__":
    create_art_banner()
