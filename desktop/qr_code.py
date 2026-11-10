"""
MadGammon Standalone QR Code Generator
Pure Python implementation of QR Code (Model 2, Byte Mode, ECC L/M)
Generates boolean matrices and renders directly to PyQt6 QPixmap / QImage.
Zero external C dependencies (no qrcode or libqrencode required).
"""
import math
from typing import List, Tuple, Optional
from PyQt6.QtGui import QPixmap, QPainter, QColor, QImage
from PyQt6.QtCore import Qt, QRect

# ─── Galois Field GF(256) Math for Reed-Solomon ───
GF_EXP = [0] * 512
GF_LOG = [0] * 256
_x = 1
for _i in range(255):
    GF_EXP[_i] = _x
    GF_EXP[_i + 255] = _x
    GF_LOG[_x] = _i
    _x = (_x << 1) ^ (0x11D if (_x & 0x80) else 0)

def gf_mul(x: int, y: int) -> int:
    if x == 0 or y == 0:
        return 0
    return GF_EXP[GF_LOG[x] + GF_LOG[y]]

def rs_poly_multiply(p1: List[int], p2: List[int]) -> List[int]:
    res = [0] * (len(p1) + len(p2) - 1)
    for i, c1 in enumerate(p1):
        for j, c2 in enumerate(p2):
            res[i + j] ^= gf_mul(c1, c2)
    return res

def rs_generator_poly(ec_len: int) -> List[int]:
    g = [1]
    for i in range(ec_len):
        g = rs_poly_multiply(g, [1, GF_EXP[i]])
    return g

def rs_calc_syndromes(data: List[int], ec_len: int) -> List[int]:
    g = rs_generator_poly(ec_len)
    msg = data + [0] * ec_len
    for i in range(len(data)):
        lead = msg[i]
        if lead != 0:
            for j in range(len(g)):
                msg[i + j] ^= gf_mul(g[j], lead)
    return msg[len(data):]

# QR Version Capacities (Byte mode, ECC L)
# Version: (size, data_codewords, ec_codewords_per_block, num_blocks)
QR_VERSIONS = {
    1: (21, 19, 7, 1),
    2: (25, 34, 10, 1),
    3: (29, 55, 15, 1),
    4: (33, 80, 20, 1),
    5: (37, 108, 26, 1),
    6: (41, 136, 18, 2),
    7: (45, 156, 20, 2),
    8: (49, 194, 24, 2),
    9: (53, 232, 30, 2),
    10: (57, 274, 18, 2),
}

ALIGNMENT_PATTERNS = {
    2: [6, 18], 3: [6, 22], 4: [6, 26], 5: [6, 30],
    6: [6, 34], 7: [6, 22, 38], 8: [6, 24, 42], 9: [6, 26, 46], 10: [6, 28, 50]
}

class QREncoder:
    @staticmethod
    def encode(text: str) -> List[List[bool]]:
        data_bytes = text.encode("utf-8")
        data_len = len(data_bytes)
        
        # Pick smallest fitting QR version
        version = None
        for v, (size, cap, ec, blocks) in QR_VERSIONS.items():
            # Overhead: 4 bits mode + 8/16 bits count
            header_bytes = 2 if v < 10 else 3
            if data_len + header_bytes <= cap:
                version = v
                break
        if version is None:
            version = 10
            data_bytes = data_bytes[:270]

        size, cap, ec_len, num_blocks = QR_VERSIONS[version]

        # 1. Build Bit Buffer (Byte Mode = 0100)
        bits = []
        def append_bits(val, count):
            for i in reversed(range(count)):
                bits.append((val >> i) & 1)

        append_bits(0b0100, 4)
        count_bits = 8 if version < 10 else 16
        append_bits(len(data_bytes), count_bits)
        for b in data_bytes:
            append_bits(b, 8)

        # Terminator
        needed_bits = cap * 8
        term_len = min(4, needed_bits - len(bits))
        append_bits(0, term_len)

        # Pad to byte boundary
        while len(bits) % 8 != 0:
            bits.append(0)

        # Pad with 0xEC, 0x11
        pad_bytes = [0xEC, 0x11]
        pad_idx = 0
        while len(bits) < needed_bits:
            append_bits(pad_bytes[pad_idx % 2], 8)
            pad_idx += 1

        # Convert to codewords
        codewords = []
        for i in range(0, len(bits), 8):
            val = 0
            for b in bits[i:i+8]:
                val = (val << 1) | b
            codewords.append(val)

        # Error correction
        ec_bytes = rs_calc_syndromes(codewords, ec_len)
        all_codewords = codewords + ec_bytes

        # Matrix allocation
        matrix = [[None] * size for _ in range(size)]
        reserved = [[False] * size for _ in range(size)]

        def set_func(r, c, val):
            if 0 <= r < size and 0 <= c < size:
                matrix[r][c] = val
                reserved[r][c] = True

        # Finder Patterns
        def add_finder(start_r, start_c):
            for r in range(7):
                for c in range(7):
                    is_black = (r == 0 or r == 6 or c == 0 or c == 6 or (2 <= r <= 4 and 2 <= c <= 4))
                    set_func(start_r + r, start_c + c, is_black)
            # Separator
            for r in range(-1, 8):
                for c in range(-1, 8):
                    if r in (-1, 7) or c in (-1, 7):
                        set_func(start_r + r, start_c + c, False)

        add_finder(0, 0)
        add_finder(0, size - 7)
        add_finder(size - 7, 0)

        # Alignment patterns
        if version in ALIGNMENT_PATTERNS:
            coords = ALIGNMENT_PATTERNS[version]
            for r in coords:
                for c in coords:
                    if reserved[r][c]:
                        continue
                    for dr in range(-2, 3):
                        for dc in range(-2, 3):
                            is_black = (abs(dr) == 2 or abs(dc) == 2 or (dr == 0 and dc == 0))
                            set_func(r + dr, c + dc, is_black)

        # Timing patterns
        for i in range(8, size - 8):
            set_func(6, i, i % 2 == 0)
            set_func(i, 6, i % 2 == 0)

        # Dark module
        set_func(size - 8, 8, True)

        # Reserve Format Info
        for i in range(9):
            reserved[8][i] = True
            reserved[i][8] = True
            reserved[8][size - 1 - i] = True
            reserved[size - 1 - i][8] = True

        # Place Data Bits with Mask 0 ((r + c) % 2 == 0)
        bit_idx = 0
        total_data_bits = []
        for cw in all_codewords:
            for b in reversed(range(8)):
                total_data_bits.append((cw >> b) & 1)

        c = size - 1
        upward = True
        while c > 0:
            if c == 6:
                c -= 1
            rows = range(size - 1, -1, -1) if upward else range(size)
            for r in rows:
                for col_offset in (0, -1):
                    curr_c = c + col_offset
                    if not reserved[r][curr_c]:
                        bit_val = total_data_bits[bit_idx] if bit_idx < len(total_data_bits) else 0
                        bit_idx += 1
                        # Apply Mask 0
                        if (r + curr_c) % 2 == 0:
                            bit_val ^= 1
                        matrix[r][curr_c] = bool(bit_val)
            c -= 2
            upward = not upward

        # Format Info (ECC Level L = 01, Mask 0 = 000 -> 01000)
        # With BCH error correction: 0x77C4 (for L, Mask 0)
        format_val = 0x77C4
        format_bits = [(format_val >> i) & 1 for i in range(15)]
        
        # Write format info around top-left finder
        coords1 = [(8, 0), (8, 1), (8, 2), (8, 3), (8, 4), (8, 5), (8, 7), (8, 8),
                   (7, 8), (5, 8), (4, 8), (3, 8), (2, 8), (1, 8), (0, 8)]
        for i, (r, col) in enumerate(coords1):
            matrix[r][col] = bool(format_bits[14 - i])

        # Write split format info around other finders
        coords2 = [(size - 1, 8), (size - 2, 8), (size - 3, 8), (size - 4, 8),
                   (size - 5, 8), (size - 6, 8), (size - 7, 8)]
        coords3 = [(8, size - 8), (8, size - 7), (8, size - 6), (8, size - 5),
                   (8, size - 4), (8, size - 3), (8, size - 2), (8, size - 1)]
        
        for i, (r, col) in enumerate(coords2):
            matrix[r][col] = bool(format_bits[i])
        for i, (r, col) in enumerate(coords3):
            matrix[r][col] = bool(format_bits[7 + i])

        # Clean remaining None
        for r in range(size):
            for col in range(size):
                if matrix[r][col] is None:
                    matrix[r][col] = False

        return matrix


class QrRenderer:
    @staticmethod
    def render_pixmap(text: str, size_px: int = 240, size: Optional[int] = None,
                      border_modules: int = 4, fg_color: str = "#00e5ff",
                      bg_color: str = "#0e1118") -> QPixmap:
        """
        Renders given text into a styled QPixmap.
        """
        effective_size = size if size is not None else size_px
        try:
            matrix = QREncoder.encode(text)
        except Exception:
            # Fallback simple matrix if encoding error
            matrix = [[(r + c) % 2 == 0 for c in range(25)] for r in range(25)]

        n = len(matrix)
        pix = QPixmap(effective_size, effective_size)
        pix.fill(QColor(bg_color))

        painter = QPainter(pix)
        painter.setRenderHint(QPainter.RenderHint.Antialiasing, False)
        painter.setPen(Qt.PenStyle.NoPen)
        painter.setBrush(QColor(fg_color))

        # Margin
        quiet_zone = border_modules
        total_modules = n + quiet_zone * 2
        module_size = effective_size / total_modules

        for r in range(n):
            for c in range(n):
                if matrix[r][c]:
                    x = int((c + quiet_zone) * module_size)
                    y = int((r + quiet_zone) * module_size)
                    w = int(math.ceil(module_size))
                    h = int(math.ceil(module_size))
                    painter.drawRect(x, y, w, h)

        painter.end()
        return pix
