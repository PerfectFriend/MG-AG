import math
from PIL import Image, ImageDraw, ImageFont

def create_madgammon_icon(output_path="desktop/icons/madgammon.png", size=256):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    # 1. Rounded backdrop
    margin = 8
    radius = 54
    draw.rounded_rectangle(
        [(margin, margin), (size - margin, size - margin)],
        radius=radius,
        fill=(13, 17, 23, 255),
        outline=(0, 255, 102, 230),
        width=4
    )

    # Inner subtle glow border
    draw.rounded_rectangle(
        [(margin + 4, margin + 4), (size - margin - 4, size - margin - 4)],
        radius=radius - 4,
        outline=(0, 229, 255, 80),
        width=2
    )

    # 2. Draw Backgammon Points (Triangles at top and bottom edges)
    # Top triangles
    pts_top = [
        [(36, 16), (56, 70), (76, 16)],
        [(86, 16), (106, 60), (126, 16)],
        [(136, 16), (156, 60), (176, 16)],
        [(186, 16), (206, 70), (226, 16)]
    ]
    for i, pts in enumerate(pts_top):
        color = (245, 158, 11, 160) if i % 2 == 0 else (16, 185, 129, 140)
        draw.polygon(pts, fill=color)

    # Bottom triangles
    pts_bottom = [
        [(36, 240), (56, 186), (76, 240)],
        [(86, 240), (106, 196), (126, 240)],
        [(136, 240), (156, 196), (176, 240)],
        [(186, 240), (206, 186), (226, 240)]
    ]
    for i, pts in enumerate(pts_bottom):
        color = (16, 185, 129, 140) if i % 2 == 0 else (245, 158, 11, 160)
        draw.polygon(pts, fill=color)

    # 3. Draw Left Die (Golden / Emerald Cube)
    die1_x, die1_y = 60, 95
    die_w, die_h = 62, 62
    draw.rounded_rectangle(
        [(die1_x, die1_y), (die1_x + die_w, die1_y + die_h)],
        radius=14,
        fill=(22, 27, 34, 255),
        outline=(245, 158, 11, 255),
        width=3
    )
    # 5 dots pattern
    r = 5
    cx, cy = die1_x + die_w // 2, die1_y + die_h // 2
    offset = 16
    dots_5 = [
        (cx, cy),
        (cx - offset, cy - offset),
        (cx + offset, cy - offset),
        (cx - offset, cy + offset),
        (cx + offset, cy + offset)
    ]
    for dot_x, dot_y in dots_5:
        draw.ellipse([(dot_x - r, dot_y - r), (dot_x + r, dot_y + r)], fill=(245, 158, 11, 255))

    # 4. Draw Right Die (Cyan / Emerald Cube)
    die2_x, die2_y = 134, 105
    draw.rounded_rectangle(
        [(die2_x, die2_y), (die2_x + die_w, die2_y + die_h)],
        radius=14,
        fill=(22, 27, 34, 255),
        outline=(0, 255, 102, 255),
        width=3
    )
    # 6 dots pattern
    dots_6 = [
        (die2_x + 18, die2_y + 14), (die2_x + 44, die2_y + 14),
        (die2_x + 18, die2_y + 31), (die2_x + 44, die2_y + 31),
        (die2_x + 18, die2_y + 48), (die2_x + 44, die2_y + 48)
    ]
    for dot_x, dot_y in dots_6:
        draw.ellipse([(dot_x - r, dot_y - r), (dot_x + r, dot_y + r)], fill=(0, 255, 102, 255))

    # 5. Cyberpunk Circuit Lines and Shield Emblem
    draw.line([(30, 128), (52, 128)], fill=(0, 229, 255, 200), width=2)
    draw.line([(204, 136), (226, 136)], fill=(0, 229, 255, 200), width=2)
    draw.ellipse([(26, 124), (34, 132)], fill=(0, 229, 255, 255))
    draw.ellipse([(222, 132), (230, 140)], fill=(0, 229, 255, 255))

    img.save(output_path, "PNG")
    print(f"Icon saved to {output_path}")

if __name__ == "__main__":
    create_madgammon_icon()
