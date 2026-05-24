"""Generate Pulpit Ink brand assets for Play Store and GitHub Pages.

Outputs (all into play-console/assets/):
  - icon-512.png          (Play Store icon, 512x512 PNG, alpha)
  - icon-1024.png         (high-res master, 1024x1024)
  - feature-graphic-1024x500.png  (Play Store feature graphic)
  - landing-hero.png      (landing page hero, 1600x900)

Brand:
  - Deep ink navy gradient background
  - Warm gold ink-drop mark with quill nib slit and 3 sound-wave bars inside
  - Conveys: voice -> ink/text, sermon -> document
"""

from __future__ import annotations

import math
import os
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter, ImageFont

OUT = Path(__file__).resolve().parent

# Brand palette
INK_DEEP = (15, 27, 54)        # #0F1B36
INK_MID = (27, 42, 78)         # #1B2A4E
INK_LIGHT = (45, 65, 110)      # #2D416E
GOLD = (212, 168, 90)          # #D4A85A
GOLD_LIGHT = (235, 200, 130)   # #EBC882
PAPER = (245, 241, 232)        # #F5F1E8 (parchment)
PAPER_DIM = (210, 200, 180)


def find_font(candidates: list[str], size: int) -> ImageFont.FreeTypeFont:
    win_fonts = Path("C:/Windows/Fonts")
    for name in candidates:
        p = win_fonts / name
        if p.exists():
            return ImageFont.truetype(str(p), size)
    return ImageFont.load_default()


def serif_font(size: int) -> ImageFont.FreeTypeFont:
    return find_font(["georgia.ttf", "georgiab.ttf", "times.ttf"], size)


def serif_bold(size: int) -> ImageFont.FreeTypeFont:
    return find_font(["georgiab.ttf", "timesbd.ttf", "georgia.ttf"], size)


def sans_font(size: int) -> ImageFont.FreeTypeFont:
    return find_font(["segoeui.ttf", "arial.ttf"], size)


def sans_bold(size: int) -> ImageFont.FreeTypeFont:
    return find_font(["segoeuib.ttf", "arialbd.ttf"], size)


def korean_font(size: int) -> ImageFont.FreeTypeFont:
    return find_font(["malgunbd.ttf", "malgun.ttf", "NanumGothicBold.ttf"], size)


def radial_gradient(size: tuple[int, int], inner: tuple, outer: tuple, center=None) -> Image.Image:
    """Smooth radial gradient from inner color (at center) to outer color (at corner)."""
    w, h = size
    if center is None:
        center = (w / 2, h * 0.4)
    cx, cy = center
    max_r = math.hypot(max(cx, w - cx), max(cy, h - cy))
    img = Image.new("RGB", size, outer)
    px = img.load()
    for y in range(h):
        for x in range(w):
            r = math.hypot(x - cx, y - cy) / max_r
            r = min(1.0, r)
            t = r ** 1.4
            px[x, y] = (
                int(inner[0] * (1 - t) + outer[0] * t),
                int(inner[1] * (1 - t) + outer[1] * t),
                int(inner[2] * (1 - t) + outer[2] * t),
            )
    return img


def ink_drop_polygon(cx: float, cy: float, width: float, height: float, points: int = 200):
    """Classic teardrop / ink-drop silhouette.

    Pointed apex at top, rounded bowl at bottom. Width/height are full bounding box.
    Returns list of (x, y) tuples ready for ImageDraw.polygon.
    """
    pts = []
    for i in range(points):
        t = i / points * 2 * math.pi
        # Asymmetric parametric ink-drop: narrower at top, fatter at bottom
        # Standard teardrop: y depends on sin shape, x narrows toward top
        # Use modified equation: x = width/2 * sin(t) * (1 - cos(t))/2 doesn't quite work.
        # Use: scale x by a function that goes 0 at top (t=0), max at bottom, smooth
        s = (1 - math.cos(t)) / 2  # 0 at top, 1 at bottom
        x_scale = math.sin(t) * (0.5 + 0.5 * s)
        y_scale = -math.cos(t)  # -1 at top, 1 at bottom
        x = cx + x_scale * width / 2
        y = cy + y_scale * height / 2
        pts.append((x, y))
    return pts


def draw_ink_drop_mark(img: Image.Image, cx: float, cy: float, height: float,
                        fill=GOLD):
    """Draw the ink-drop wordmark element: teardrop + pen-nib slit (V-cut at top of slit + tine breather hole)."""
    # Build a separate hi-res mask for clean anti-aliased edges
    scale = 4
    s_h = int(height * scale)
    s_w = int(height * 0.78 * scale)
    pad = s_w  # padding around so blur/shadow don't clip
    canvas = Image.new("RGBA", (s_w + pad * 2, s_h + pad * 2), (0, 0, 0, 0))
    cdraw = ImageDraw.Draw(canvas, "RGBA")

    cx_s = pad + s_w / 2
    cy_s = pad + s_h / 2

    # Drop body
    pts = ink_drop_polygon(cx_s, cy_s, s_w, s_h)
    cdraw.polygon(pts, fill=fill + (255,))

    # Subtle inner highlight (lighter on upper-left, suggests volume)
    hl = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    hl_draw = ImageDraw.Draw(hl)
    hl_pts = ink_drop_polygon(cx_s - s_w * 0.06, cy_s - s_h * 0.04, s_w * 0.78, s_h * 0.78)
    hl_draw.polygon(hl_pts, fill=GOLD_LIGHT + (110,))
    hl = hl.filter(ImageFilter.GaussianBlur(radius=int(s_h * 0.04)))
    # Mask the highlight by the drop shape
    drop_mask = Image.new("L", canvas.size, 0)
    ImageDraw.Draw(drop_mask).polygon(pts, fill=255)
    canvas_with_hl = Image.composite(hl, Image.new("RGBA", canvas.size, (0, 0, 0, 0)), drop_mask)
    canvas.alpha_composite(canvas_with_hl)

    # Pen-nib slit: a SHORT tapered slot near the apex of the drop only.
    # Stops well above center so it never reads as a punctuation glyph.
    slit_top_y = cy_s - s_h * 0.34
    slit_bot_y = cy_s - s_h * 0.06
    slit_top_w = max(3, int(s_w * 0.038))
    slit_bot_w = max(2, int(s_w * 0.014))
    slit_poly = [
        (cx_s - slit_top_w, slit_top_y),
        (cx_s + slit_top_w, slit_top_y),
        (cx_s + slit_bot_w, slit_bot_y),
        (cx_s - slit_bot_w, slit_bot_y),
    ]
    cdraw.polygon(slit_poly, fill=INK_DEEP + (255,))
    cdraw.ellipse(
        (cx_s - slit_bot_w, slit_bot_y - slit_bot_w, cx_s + slit_bot_w, slit_bot_y + slit_bot_w),
        fill=INK_DEEP + (255,),
    )

    # Three sound-wave bars below the slit, centered, tapered widths.
    # Together with the slit, the mark reads as "voice -> ink".
    bar_y_center = cy_s + s_h * 0.22
    bar_h = max(4, int(s_h * 0.045))
    gap = bar_h * 2.6
    widths = [s_w * 0.36, s_w * 0.54, s_w * 0.36]
    for i, bw in enumerate(widths):
        y = bar_y_center + (i - 1) * gap
        cdraw.rounded_rectangle(
            (cx_s - bw / 2, y - bar_h / 2, cx_s + bw / 2, y + bar_h / 2),
            radius=bar_h / 2,
            fill=INK_DEEP + (255,),
        )

    # Downscale with LANCZOS for smooth edges
    out_w = int((s_w + pad * 2) / scale)
    out_h = int((s_h + pad * 2) / scale)
    canvas = canvas.resize((out_w, out_h), Image.Resampling.LANCZOS)

    # Soft drop shadow
    shadow_src = canvas.split()[3].point(lambda a: int(a * 0.55))
    shadow = Image.new("RGBA", canvas.size, INK_DEEP + (0,))
    shadow.putalpha(shadow_src)
    shadow = shadow.filter(ImageFilter.GaussianBlur(radius=max(3, int(height * 0.045))))

    paste_x = int(cx - out_w / 2)
    paste_y = int(cy - out_h / 2)
    img.alpha_composite(shadow, (paste_x + int(height * 0.018), paste_y + int(height * 0.03)))
    img.alpha_composite(canvas, (paste_x, paste_y))


def make_app_icon(size: int = 1024) -> Image.Image:
    """Square app icon. Play Store will round corners automatically."""
    bg = radial_gradient((size, size), INK_LIGHT, INK_DEEP, center=(size / 2, size * 0.35))
    img = bg.convert("RGBA")

    # Subtle vignette ring of warmer color near top to add depth
    overlay = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    odraw = ImageDraw.Draw(overlay)
    # Thin top arc highlight
    odraw.ellipse(
        (-size * 0.4, -size * 0.6, size * 1.4, size * 0.6),
        fill=(255, 255, 255, 14),
    )
    img.alpha_composite(overlay)

    # Inner soft circle behind the drop to anchor it
    glow = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    gdraw = ImageDraw.Draw(glow)
    r = int(size * 0.36)
    cx, cy = size // 2, int(size * 0.52)
    gdraw.ellipse((cx - r, cy - r, cx + r, cy + r), fill=GOLD + (28,))
    glow = glow.filter(ImageFilter.GaussianBlur(radius=int(size * 0.05)))
    img.alpha_composite(glow)

    # The ink drop itself
    draw_ink_drop_mark(img, cx=size / 2, cy=size * 0.52, height=size * 0.66, fill=GOLD)

    return img


def make_adaptive_foreground(size: int = 432) -> Image.Image:
    """108dp = 432px @ xxxhdpi; we'll output 1024 for the full master."""
    # Adaptive icon foreground: the centered mark on transparent background,
    # safe area is roughly center 66% of the 108dp viewport.
    s = 1024
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    # Drop only, sized to fit safe zone (~66%)
    draw_ink_drop_mark(img, cx=s / 2, cy=s * 0.52, height=s * 0.60, fill=GOLD)
    return img


def make_adaptive_background(size: int = 1024) -> Image.Image:
    return radial_gradient((size, size), INK_LIGHT, INK_DEEP, center=(size / 2, size * 0.35)).convert("RGBA")


def make_feature_graphic() -> Image.Image:
    """1024x500 Play Store feature graphic.

    Layout: mark on the left, wordmark + taglines in the middle on a clear field,
    decorative waveform tucked into the far right (faded so it never overlaps text).
    """
    w, h = 1024, 500
    bg = radial_gradient((w, h), INK_LIGHT, INK_DEEP, center=(w * 0.30, h * 0.4))
    img = bg.convert("RGBA")

    # Subtle horizontal score lines for paper texture
    overlay = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    odraw = ImageDraw.Draw(overlay)
    for y in range(0, h, 14):
        odraw.line([(0, y), (w, y)], fill=(255, 255, 255, 5))
    img.alpha_composite(overlay)

    # Decorative waveform: only in the far right (x = 78%~98%), fading out at both ends
    wf = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    wfd = ImageDraw.Draw(wf)
    import random
    random.seed(7)
    base_x = w * 0.78
    end_x = w * 0.97
    n_bars = 30
    for i in range(n_bars):
        t = i / (n_bars - 1)
        x = base_x + (end_x - base_x) * t
        amp = (math.sin(i * 0.55) * 0.5 + 0.5)
        amp += random.uniform(-0.08, 0.08)
        amp = max(0.12, min(1.0, amp))
        bar_h = amp * h * 0.38
        cy = h * 0.5
        bar_w = 4
        # Fade in then out
        fade = math.sin(t * math.pi)
        alpha = int(180 * fade)
        wfd.rounded_rectangle(
            (x - bar_w / 2, cy - bar_h / 2, x + bar_w / 2, cy + bar_h / 2),
            radius=2,
            fill=GOLD + (alpha,),
        )
    img.alpha_composite(wf)

    # Left side: icon mark
    draw_ink_drop_mark(img, cx=w * 0.15, cy=h * 0.5, height=h * 0.78, fill=GOLD)

    # Center: wordmark and tagline (kept inside x = 30%..76%)
    draw = ImageDraw.Draw(img, "RGBA")
    title_font = serif_bold(78)
    sub_font = sans_font(26)
    ko_font = korean_font(28)

    title = "Pulpit Ink"
    tx = int(w * 0.30)
    ty = int(h * 0.27)
    # Stronger shadow for legibility on textured bg
    draw.text((tx + 2, ty + 4), title, fill=(0, 0, 0, 200), font=title_font)
    draw.text((tx, ty), title, fill=PAPER, font=title_font)

    # Thin gold underline rule
    rule_y = ty + 102
    draw.rectangle((tx, rule_y, tx + 250, rule_y + 3), fill=GOLD)

    sub = "Sermons, transcribed in ink."
    draw.text((tx, ty + 125), sub, fill=GOLD_LIGHT, font=sub_font)

    ko = "설교를 글로 옮기는 가장 빠른 길"
    draw.text((tx, ty + 165), ko, fill=PAPER_DIM, font=ko_font)

    return img


def make_landing_hero() -> Image.Image:
    """1600x900 hero for the GitHub Pages landing site.

    Same layout principle as feature graphic but bigger and more breathing room.
    """
    w, h = 1600, 900
    bg = radial_gradient((w, h), INK_LIGHT, INK_DEEP, center=(w * 0.35, h * 0.45))
    img = bg.convert("RGBA")

    # Faint horizontal score lines
    overlay = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    odraw = ImageDraw.Draw(overlay)
    for y in range(0, h, 22):
        odraw.line([(0, y), (w, y)], fill=(255, 255, 255, 5))
    img.alpha_composite(overlay)

    # Waveform on far right only (x = 75%..97%)
    wf = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    wfd = ImageDraw.Draw(wf)
    import random
    random.seed(11)
    base_x = w * 0.75
    end_x = w * 0.97
    n_bars = 50
    for i in range(n_bars):
        t = i / (n_bars - 1)
        x = base_x + (end_x - base_x) * t
        amp = (math.sin(i * 0.45) * 0.5 + 0.5)
        amp += random.uniform(-0.08, 0.08)
        amp = max(0.10, min(1.0, amp))
        bar_h = amp * h * 0.35
        cy = h * 0.55
        bar_w = 7
        fade = math.sin(t * math.pi)
        alpha = int(170 * fade)
        wfd.rounded_rectangle(
            (x - bar_w / 2, cy - bar_h / 2, x + bar_w / 2, cy + bar_h / 2),
            radius=3,
            fill=GOLD + (alpha,),
        )
    img.alpha_composite(wf)

    # Mark on the left
    draw_ink_drop_mark(img, cx=w * 0.17, cy=h * 0.52, height=h * 0.70, fill=GOLD)

    # Wordmark + copy (centered in x = 32%..72%)
    draw = ImageDraw.Draw(img, "RGBA")
    title_font = serif_bold(132)
    sub_font = sans_font(40)
    ko_font = korean_font(44)

    title = "Pulpit Ink"
    tx = int(w * 0.32)
    ty = int(h * 0.28)
    draw.text((tx + 3, ty + 5), title, fill=(0, 0, 0, 200), font=title_font)
    draw.text((tx, ty), title, fill=PAPER, font=title_font)

    rule_y = ty + 175
    draw.rectangle((tx, rule_y, tx + 380, rule_y + 5), fill=GOLD)

    sub = "Sermons, transcribed in ink."
    draw.text((tx, ty + 210), sub, fill=GOLD_LIGHT, font=sub_font)

    ko = "설교를 글로 옮기는 가장 빠른 길"
    draw.text((tx, ty + 270), ko, fill=PAPER_DIM, font=ko_font)

    return img


def save(img: Image.Image, name: str):
    out = OUT / name
    img.save(out, "PNG", optimize=True)
    print(f"  wrote {out} ({img.size[0]}x{img.size[1]})")


def main():
    print("Generating Pulpit Ink brand assets...")
    OUT.mkdir(parents=True, exist_ok=True)

    icon_1024 = make_app_icon(1024)
    save(icon_1024, "icon-1024.png")
    save(icon_1024.resize((512, 512), Image.Resampling.LANCZOS), "icon-512.png")

    save(make_adaptive_foreground(), "adaptive-foreground-1024.png")
    save(make_adaptive_background(), "adaptive-background-1024.png")

    save(make_feature_graphic(), "feature-graphic-1024x500.png")
    save(make_landing_hero(), "landing-hero-1600x900.png")
    print("Done.")


if __name__ == "__main__":
    main()
