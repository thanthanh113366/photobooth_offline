"""Generate four matching vintage PNG overlays with truly transparent photo windows.

Requires rsvg-convert. The SVG geometry is kept here so the packaged frames can
be rebuilt at a higher resolution without inventing detail by upscaling a PNG.
"""

from pathlib import Path
import math
import random
import subprocess


ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "app/src/main/res/drawable-nodpi"
FRAMES = ROOT / "frames"
WIDTH, HEIGHT = 2400, 3552
TOP, BOTTOM = 560, 3070


def windows(count):
    cols = 1 if count <= 2 else 2
    rows = count // cols
    gap_x, gap_y = 58, 62
    outer_x = 170
    width = (WIDTH - 2 * outer_x - (cols - 1) * gap_x) / cols
    height = (BOTTOM - TOP - (rows - 1) * gap_y) / rows
    return [
        (outer_x + col * (width + gap_x), TOP + row * (height + gap_y), width, height)
        for row in range(rows)
        for col in range(cols)
    ]


def star(x, y, radius=34):
    points = []
    for i in range(16):
        angle = math.pi * i / 8 - math.pi / 2
        r = radius if i % 4 == 0 else radius * (0.13 if i % 2 else 0.34)
        points.append(f"{x + r * math.cos(angle):.1f},{y + r * math.sin(angle):.1f}")
    return f'<polygon points="{" ".join(points)}" fill="url(#gold)"/>'


def laurel(cx, cy, direction):
    parts = [f'<path d="M {cx} {cy} Q {cx + direction * 130} {cy - 36} '
             f'{cx + direction * 225} {cy - 125}" fill="none" stroke="#b88a42" stroke-width="7"/>']
    for i in range(6):
        x = cx + direction * (26 + i * 34)
        y = cy - 8 - i * 14
        parts.append(f'<ellipse cx="{x}" cy="{y - 25}" rx="13" ry="35" '
                     f'transform="rotate({direction * 48} {x} {y - 25})" fill="url(#gold)"/>')
    return "".join(parts)


def svg(count):
    slots = windows(count)
    holes = " ".join(f'M {x:.1f},{y:.1f} h {w:.1f} v {h:.1f} h {-w:.1f} Z'
                     for x, y, w, h in slots)
    outlines = []
    for x, y, w, h in slots:
        outlines.append(f'<rect x="{x - 11:.1f}" y="{y - 11:.1f}" '
                        f'width="{w + 22:.1f}" height="{h + 22:.1f}" '
                        f'fill="none" stroke="#71542d" stroke-width="4"/>')
        outlines.append(f'<rect x="{x - 3:.1f}" y="{y - 3:.1f}" '
                        f'width="{w + 6:.1f}" height="{h + 6:.1f}" '
                        f'fill="none" stroke="url(#gold)" stroke-width="7"/>')

    rng = random.Random(2026 + count)
    speckles = []
    for _ in range(1550):
        x, y = rng.randrange(WIDTH), rng.randrange(HEIGHT)
        if any(wx - 14 < x < wx + ww + 14 and wy - 14 < y < wy + wh + 14
               for wx, wy, ww, wh in slots):
            continue
        speckles.append(f'<circle cx="{x}" cy="{y}" r="{rng.choice((1, 1, 2, 3))}" '
                        f'fill="{rng.choice(("#d4aa62", "#81643e", "#f0d298"))}" '
                        f'opacity="{rng.uniform(.035, .11):.3f}"/>')

    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{WIDTH}" height="{HEIGHT}"
      viewBox="0 0 {WIDTH} {HEIGHT}">
      <defs>
        <radialGradient id="black"><stop stop-color="#292116"/>
          <stop offset="0.65" stop-color="#181611"/>
          <stop offset="1" stop-color="#0e0d0b"/></radialGradient>
        <linearGradient id="gold" x1="0" y1="0" x2="1" y2="1">
          <stop stop-color="#8e632c"/><stop offset="0.25" stop-color="#e8bf71"/>
          <stop offset="0.55" stop-color="#b8873d"/>
          <stop offset="0.8" stop-color="#f1d49a"/>
          <stop offset="1" stop-color="#8e632c"/>
        </linearGradient>
      </defs>
      <path d="M 0,0 H {WIDTH} V {HEIGHT} H 0 Z {holes}"
            fill="url(#black)" fill-rule="evenodd"/>
      {''.join(speckles)}
      <rect x="45" y="45" width="2310" height="3462" rx="88"
            fill="none" stroke="#8c6c3c" stroke-width="8"/>
      <rect x="77" y="77" width="2246" height="3398" rx="65"
            fill="none" stroke="url(#gold)" stroke-width="5"/>
      <rect x="105" y="105" width="2190" height="3342" rx="52"
            fill="none" stroke="#755c35" stroke-width="3"/>
      <path d="M 170 115 H 550 M 1850 115 H 2230 M 170 3437 H 550 M 1850 3437 H 2230"
            fill="none" stroke="url(#gold)" stroke-width="7"/>
      {star(175, 348, 46)} {star(2225, 348, 46)}
      {star(175, 3200, 38)} {star(2225, 3200, 38)}
      <text x="1200" y="306" text-anchor="middle" fill="url(#gold)"
            font-family="Liberation Serif" font-style="italic" font-size="250">Graduation</text>
      <path d="M 180 389 H 742 M 1658 389 H 2220" fill="none"
            stroke="url(#gold)" stroke-width="5"/>
      {laurel(820, 453, -1)} {laurel(1580, 453, 1)}
      <text x="1200" y="483" text-anchor="middle" fill="url(#gold)"
            font-family="Liberation Serif" font-size="155" letter-spacing="34">UIT</text>
      {''.join(outlines)}
      <text x="1200" y="3335" text-anchor="middle" fill="url(#gold)"
            font-family="Liberation Serif" font-style="italic" font-size="158">HaMinhTruong</text>
      <path d="M 180 3375 H 670 M 1730 3375 H 2220" fill="none"
            stroke="url(#gold)" stroke-width="5"/>
      {star(1200, 3400, 29)}
    </svg>'''


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    FRAMES.mkdir(parents=True, exist_ok=True)
    for count in (1, 2, 4, 8):
        source = FRAMES / f"graduation-frame-{count}.svg"
        target = OUT / f"graduation_frame_{count}.png"
        source.write_text(svg(count), encoding="utf-8")
        with target.open("wb") as output:
            subprocess.run(["rsvg-convert", str(source)], stdout=output, check=True)
        print(target)


if __name__ == "__main__":
    main()
