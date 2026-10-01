#!/usr/bin/env python3
"""Genera los iconos de launcher (PNG) de Mix.Casete con estilo casete retro.

Uso:  python3 tools/gen_icons.py
Requiere: Pillow (pip install pillow)

Dibuja un casete a línea negra gruesa sobre fondo claro, en una base de 864 px
(con antialiasing) y la reescala a todas las densidades mdpi..xxxhdpi
(ic_launcher.png e ic_launcher_round.png).
"""
from PIL import Image, ImageDraw
import math
import os

BASE = 432
K = (18, 18, 18, 255)          # negro de línea (borde grueso)
CREAM = (245, 240, 226, 255)   # fondo claro
TAPE = (40, 40, 46, 255)       # grafito de la cinta

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def draw_cassette(size: int) -> Image.Image:
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    s = size / BASE  # escala desde la base de 432 px

    def S(v: float) -> float:
        return v * s

    def box(x0, y0, x1, y1):
        return (S(x0), S(y0), S(x1), S(y1))

    lw_main = max(2, round(S(9)))
    lw_mid = max(2, round(S(6)))
    lw_thin = max(1, round(S(4)))

    # --- Placa circular del icono (fondo claro + borde negro) --------------
    cx = cy = size / 2.0
    pad = S(16)
    rad = size / 2 - pad
    d.ellipse([cx - rad, cy - rad, cx + rad, cy + rad],
              fill=CREAM, outline=K, width=max(3, round(S(12))))

    # --- Carcasa del casete -------------------------------------------------
    d.rounded_rectangle(box(88, 148, 344, 302), radius=S(22),
                        outline=K, width=lw_main, fill=(252, 249, 240, 255))

    # Tornillos en las esquinas
    for sx, sy in ((106, 166), (326, 166), (106, 284), (326, 284)):
        rr = S(7)
        d.ellipse([S(sx) - rr, S(sy) - rr, S(sx) + rr, S(sy) + rr],
                  outline=K, width=lw_thin)

    # --- Ventana del casete (marco biselado) --------------------------------
    d.rounded_rectangle(box(120, 172, 312, 240), radius=S(10),
                        outline=K, width=lw_mid, fill=(235, 228, 210, 255))

    # Cinta: dos discos oscuros y puente entre ellos
    tape_y = S(206)
    left_r, right_r = S(26), S(20)
    d.ellipse([S(162) - left_r, tape_y - left_r, S(162) + left_r, tape_y + left_r],
              fill=TAPE)
    d.ellipse([S(270) - right_r, tape_y - right_r, S(270) + right_r, tape_y + right_r],
              fill=TAPE)
    d.rectangle([S(162), tape_y - S(4), S(270), tape_y + S(4)], fill=TAPE)

    # Carretes: cubos dentados
    for ccx, crr in ((162, 15), (270, 15)):
        r_out, r_in = S(crr), S(crr * 0.55)
        x, y = S(ccx), tape_y
        d.ellipse([x - r_out, y - r_out, x + r_out, y + r_out],
                  outline=K, width=lw_thin, fill=(252, 249, 240, 255))
        teeth = 6
        for t in range(teeth):
            a = 2 * math.pi * t / teeth
            x2 = x + r_in * math.cos(a)
            y2 = y + r_in * math.sin(a)
            d.line([(x, y), (x2, y2)], fill=K, width=lw_thin)
        rr = S(4)
        d.ellipse([x - rr, y - rr, x + rr, y + rr], fill=K)

    # --- Ranuras inferiores (guías de cabezal) -------------------------------
    d.rounded_rectangle(box(150, 258, 282, 280), radius=S(6),
                        outline=K, width=lw_mid)
    for xx in (194, 238):
        d.line([(S(xx), S(258)), (S(xx), S(280))], fill=K, width=lw_thin)

    # Etiqueta superior de la carcasa
    d.line([(S(120), S(162)), (S(312), S(162))], fill=K, width=lw_thin)

    return img


def main():
    sizes = {
        "mdpi": 48,
        "hdpi": 72,
        "xhdpi": 96,
        "xxhdpi": 144,
        "xxxhdpi": 192,
    }
    big = draw_cassette(BASE * 2)  # antialias: dibujar al doble y reducir
    for dpi, px in sizes.items():
        icon = big.resize((px, px), Image.LANCZOS)
        out_dir = os.path.join(ROOT, "app", "src", "main", "res", f"mipmap-{dpi}")
        os.makedirs(out_dir, exist_ok=True)
        for name in ("ic_launcher.png", "ic_launcher_round.png"):
            path = os.path.join(out_dir, name)
            icon.save(path, "PNG")
            print(f"wrote {path} ({px}x{px})")


if __name__ == "__main__":
    main()
