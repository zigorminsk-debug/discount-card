#!/usr/bin/env python3
"""Генератор launcher-иконок приложения BY-Card без внешних зависимостей.

Рисует иконку в супер-разрешении (4x) и усредняет -> мягкое сглаживание.
Запуск:  python3 tools/make_icons.py
"""
import math
import os
import struct
import zlib

OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "app/src/main/res")
SS = 4  # supersampling

BG_FROM = (0x25, 0x63, 0xEB)
BG_TO = (0x7C, 0x3A, 0xED)
CARD = (0xFF, 0xFF, 0xFF)
BARS = (0x0F, 0x17, 0x2A)
SOFT = (0xC7, 0xD2, 0xFE)

BAR_PATTERN = [3, 1, 1, 2, 4, 1, 2, 1, 3, 1, 1, 3, 2, 1, 4, 1, 1, 2]


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def rounded_rect(x, y, x0, y0, x1, y1, r):
    if x < x0 or x > x1 or y < y0 or y > y1:
        return False
    cx = min(max(x, x0 + r), x1 - r)
    cy = min(max(y, y0 + r), y1 - r)
    return (x - cx) ** 2 + (y - cy) ** 2 <= r * r


def arc(x, y, cx, cy, r, w, a0, a1):
    d = math.hypot(x - cx, y - cy)
    if abs(d - r) > w / 2:
        return False
    ang = math.degrees(math.atan2(y - cy, x - cx)) % 360
    return a0 <= ang <= a1


def shade(u, v, circular):
    """Возвращает (r,g,b,a) для нормированных координат 0..1."""
    # маска формы
    if circular:
        inside = (u - 0.5) ** 2 + (v - 0.5) ** 2 <= 0.5 ** 2
    else:
        inside = rounded_rect(u, v, 0.0, 0.0, 1.0, 1.0, 0.225)
    if not inside:
        return (0, 0, 0, 0)

    col = lerp(BG_FROM, BG_TO, max(0.0, min(1.0, (u + v) / 2)))

    # NFC-волны справа сверху
    for rr in (0.085, 0.135, 0.185):
        if arc(u, v, 0.26, 0.255, rr, 0.028, -62, 62):
            col = lerp(col, (0xFF, 0xFF, 0xFF), 0.85)

    # карта
    if rounded_rect(u, v, 0.175, 0.40, 0.825, 0.80, 0.055):
        col = CARD
        # полоски штрих-кода
        if 0.465 <= v <= 0.655:
            total = sum(BAR_PATTERN)
            x0, x1 = 0.225, 0.775
            pos = x0
            unit = (x1 - x0) / total
            for i, w in enumerate(BAR_PATTERN):
                nxt = pos + w * unit
                if pos <= u < nxt and i % 2 == 0:
                    col = BARS
                    break
                pos = nxt
        # нижняя «строка номера»
        if rounded_rect(u, v, 0.225, 0.695, 0.52, 0.735, 0.02):
            col = SOFT
    return (col[0], col[1], col[2], 255)


def render(size, circular=False, full_bleed=False):
    big = size * SS
    rows = []
    for py in range(size):
        row = bytearray()
        for px in range(size):
            racc = gacc = bacc = aacc = 0
            for sy in range(SS):
                for sx in range(SS):
                    u = (px * SS + sx + 0.5) / big
                    v = (py * SS + sy + 0.5) / big
                    if full_bleed:
                        # для adaptive-foreground рисуем только контент в safe-zone
                        u = (u - 0.5) / 0.66 + 0.5
                        v = (v - 0.5) / 0.66 + 0.5
                        if not (0 <= u <= 1 and 0 <= v <= 1):
                            continue
                    r, g, b, a = shade(u, v, circular)
                    racc += r * a
                    gacc += g * a
                    bacc += b * a
                    aacc += a
            n = SS * SS
            if aacc == 0:
                row += bytes((0, 0, 0, 0))
            else:
                row += bytes((racc // aacc, gacc // aacc, bacc // aacc, aacc // n))
        rows.append(bytes(row))
    return rows


def write_png(path, size, rows):
    raw = b"".join(b"\x00" + r for r in rows)
    def chunk(tag, data):
        c = struct.pack(">I", len(data)) + tag + data
        return c + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)
    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(raw, 9))
    png += chunk(b"IEND", b"")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(png)
    print("  ", path, size, "px", len(png), "bytes")


def main():
    densities = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
    for d, s in densities.items():
        write_png(os.path.join(OUT, f"mipmap-{d}/ic_launcher.png"), s, render(s, circular=False))
        write_png(os.path.join(OUT, f"mipmap-{d}/ic_launcher_round.png"), s, render(s, circular=True))
    write_png(os.path.join(OUT, "drawable/ic_app_logo.png"), 192, render(192, circular=False))


if __name__ == "__main__":
    main()
