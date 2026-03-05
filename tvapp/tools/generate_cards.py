#!/usr/bin/env python3
"""Generate a 52-card PNG set for tvapp resources.

Usage:
  python tvapp/tools/generate_cards.py
"""

from pathlib import Path

try:
    from PIL import Image, ImageDraw, ImageFont
except ImportError as exc:
    raise SystemExit(
        "Pillow is required. Install with: pip install pillow"
    ) from exc

OUT_DIR = Path(__file__).resolve().parents[1] / "src" / "main" / "res" / "drawable-nodpi"
W, H = 750, 1050
RANKS = ["A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K"]
SUITS = [
    ("s", "♠", (20, 20, 20)),
    ("h", "♥", (180, 0, 0)),
    ("d", "♦", (200, 0, 0)),
    ("c", "♣", (20, 20, 20)),
]


def load_font(path: str, size: int):
    try:
        return ImageFont.truetype(path, size)
    except OSError:
        return ImageFont.load_default()


def main() -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    for old in OUT_DIR.glob("card_*.png"):
        old.unlink()

    font_big = load_font("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 320)
    font_corner = load_font("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 92)

    created = 0
    for rank in RANKS:
        for suit_code, suit_symbol, color in SUITS:
            image = Image.new("RGB", (W, H), (245, 245, 245))
            draw = ImageDraw.Draw(image)
            draw.rounded_rectangle(
                (20, 20, W - 20, H - 20),
                radius=28,
                outline=(30, 30, 30),
                width=8,
                fill=(255, 255, 255),
            )

            corner_text = f"{rank}{suit_symbol}"
            bbox = draw.textbbox((0, 0), corner_text, font=font_corner)
            tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
            draw.text((60, 45), corner_text, font=font_corner, fill=color)
            draw.text((W - 60 - tw, H - 45 - th), corner_text, font=font_corner, fill=color)

            center_bbox = draw.textbbox((0, 0), suit_symbol, font=font_big)
            cw, ch = center_bbox[2] - center_bbox[0], center_bbox[3] - center_bbox[1]
            draw.text(((W - cw) / 2, (H - ch) / 2 - 20), suit_symbol, font=font_big, fill=color)

            output = OUT_DIR / f"card_{rank.lower()}{suit_code}.png"
            image.save(output)
            created += 1

    print(f"Generated {created} card files in {OUT_DIR}")


if __name__ == "__main__":
    main()
