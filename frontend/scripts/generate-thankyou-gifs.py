"""Generates the bundled thank-you animations.

The thank-you page shows one of a small set of animations, picked at random.
They are generated rather than hand-drawn so they stay small and so the
repository carries no opaque binary blobs whose contents nobody can review.

Run with:  python3 scripts/generate-thankyou-gifs.py
"""

import math
import os
import random

from PIL import Image, ImageDraw

OUT = os.path.join(os.path.dirname(__file__), '..', 'src', 'assets', 'thankyou')
W, H = 240, 180


def new():
    """A blank palette frame."""
    return Image.new('P', (W, H), 255)


def save(name, frames, duration=90, loop=0):
    """Writes an animated GIF."""
    path = os.path.join(OUT, name)
    frames[0].save(
        path,
        save_all=True,
        append_images=frames[1:],
        duration=duration,
        loop=loop,
        optimize=True,
    )
    print(f"{name}: {os.path.getsize(path)} bytes")


def celebrate():
    """A green tick that draws itself in."""
    frames = []
    for i in range(6):
        im = new()
        d = ImageDraw.Draw(im)
        cx, cy, r = W // 2, H // 2, 46
        d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(220, 253, 231))
        d.line([cx - 22, cy + 2, cx - 6, cy + 20], fill=(22, 101, 52), width=9)
        if i > 2:
            d.line([cx - 6, cy + 20, cx + 24, cy - 18], fill=(22, 101, 52), width=9)
        frames.append(im)
    save('celebrate.gif', frames)


def star():
    """A star that bounces."""
    frames = []
    for i in range(8):
        im = new()
        d = ImageDraw.Draw(im)
        cx = W // 2
        cy = int(H / 2 - 34 * math.sin(i / 8 * 2 * math.pi))
        d.ellipse([cx - 40, cy - 40, cx + 40, cy + 40], fill=(255, 240, 200))
        d.ellipse([cx - 24, cy - 24, cx + 24, cy + 24], fill=(250, 204, 21))
        frames.append(im)
    save('star.gif', frames)


def thumb():
    """A thumbs-up that slides across."""
    frames = []
    for i in range(6):
        im = new()
        d = ImageDraw.Draw(im)
        x = 30 + i * 8
        y = 100
        d.rounded_rectangle([x, y, x + 60, y + 44], radius=10, fill=(253, 230, 138))
        d.rounded_rectangle([x + 62, y + 8, x + 96, y + 36], radius=8, fill=(252, 211, 77))
        frames.append(im)
    save('thumb.gif', frames)


def confetti():
    """Coloured squares falling."""
    random.seed(7)
    palette = [
        (248, 113, 113),
        (250, 204, 21),
        (74, 222, 128),
        (96, 165, 250),
        (232, 121, 249),
    ]
    frames = []
    for _ in range(8):
        im = new()
        d = ImageDraw.Draw(im)
        for _ in range(22):
            px = random.randint(10, W - 10)
            py = random.randint(10, H - 10)
            d.rectangle([px, py, px + 7, py + 7], fill=random.choice(palette))
        frames.append(im)
    save('confetti.gif', frames)


def main():
    """Regenerates every animation."""
    os.makedirs(OUT, exist_ok=True)
    celebrate()
    star()
    thumb()
    confetti()


if __name__ == '__main__':
    main()
