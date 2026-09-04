from __future__ import annotations

import math
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / ".localdeps"))

from PIL import Image, ImageDraw, ImageEnhance, ImageFilter, ImageFont
import imageio_ffmpeg


ROOT = Path(__file__).resolve().parent
ASSETS = ROOT / "assets"
TEASERS = ASSETS / "teasers"
AUDIO = Path(r"C:\Users\João\Downloads\157_CAFAJESTE_X_FINAL_FANTASY_KLICKAUD.mp3")
OUT = ROOT / "manicoba_edit.mp4"

W, H = 1080, 1920
FPS = 30
DURATION = 24.0


def font(size: int, heavy: bool = True) -> ImageFont.FreeTypeFont:
    candidates = [
        r"C:\Windows\Fonts\arialbd.ttf" if heavy else r"C:\Windows\Fonts\arial.ttf",
        r"C:\Windows\Fonts\impact.ttf",
        r"C:\Windows\Fonts\segoeuib.ttf",
    ]
    for item in candidates:
        if Path(item).exists():
            return ImageFont.truetype(item, size=size)
    return ImageFont.load_default(size=size)


FONT_BIG = font(138)
FONT_MID = font(94)
FONT_SMALL = font(64)
FONT_TINY = font(44, heavy=False)


def ease_out(x: float) -> float:
    return 1 - (1 - x) ** 3


def cover(im: Image.Image, zoom: float, ox: float, oy: float) -> Image.Image:
    scale = max(W / im.width, H / im.height) * zoom
    nw, nh = int(im.width * scale), int(im.height * scale)
    resized = im.resize((nw, nh), Image.Resampling.LANCZOS)
    max_x = max(0, nw - W)
    max_y = max(0, nh - H)
    x = int(max_x * (0.5 + ox))
    y = int(max_y * (0.5 + oy))
    x = max(0, min(max_x, x))
    y = max(0, min(max_y, y))
    return resized.crop((x, y, x + W, y + H))


def text_box(draw: ImageDraw.ImageDraw, xy: tuple[int, int], text: str, fnt, fill=(255, 255, 255), stroke=8):
    draw.text(xy, text, font=fnt, fill=fill, stroke_width=stroke, stroke_fill=(0, 0, 0))


def centered(draw: ImageDraw.ImageDraw, y: int, text: str, fnt, fill=(255, 255, 255), stroke=9):
    bbox = draw.textbbox((0, 0), text, font=fnt, stroke_width=stroke)
    x = (W - (bbox[2] - bbox[0])) // 2
    text_box(draw, (x, y), text, fnt, fill=fill, stroke=stroke)


def add_vignette(frame: Image.Image, strength: int = 125) -> Image.Image:
    overlay = Image.new("L", (W, H), 0)
    d = ImageDraw.Draw(overlay)
    d.ellipse((-W * 0.35, -H * 0.15, W * 1.35, H * 1.12), fill=255)
    mask = Image.eval(overlay.filter(ImageFilter.GaussianBlur(170)), lambda p: 255 - int(p * 0.72))
    dark = Image.new("RGB", (W, H), (0, 0, 0))
    return Image.composite(dark, frame, Image.eval(mask, lambda p: int(p * strength / 255)))


def flash(frame: Image.Image, amount: float) -> Image.Image:
    if amount <= 0:
        return frame
    white = Image.new("RGB", (W, H), (255, 255, 255))
    return Image.blend(frame, white, min(0.82, amount))


images = [Image.open(p).convert("RGB") for p in sorted(ASSETS.glob("*.jpg"))]
if not images:
    raise SystemExit("No JPG assets found.")
teaser_images = {
    "feijoada": Image.open(TEASERS / "feijoada.jpg").convert("RGB"),
    "churrasco": Image.open(TEASERS / "churrasco.jpg").convert("RGB"),
    "acaraje": Image.open(TEASERS / "acaraje.jpg").convert("RGB"),
    "coxinha": Image.open(TEASERS / "coxinha.jpg").convert("RGB"),
}


def teaser_collage() -> Image.Image:
    keys = ["feijoada", "churrasco", "acaraje", "coxinha"]
    canvas = Image.new("RGB", (W, H), (0, 0, 0))
    boxes = [(0, 0, W // 2, H // 2), (W // 2, 0, W, H // 2), (0, H // 2, W // 2, H), (W // 2, H // 2, W, H)]
    for key, box in zip(keys, boxes):
        bw, bh = box[2] - box[0], box[3] - box[1]
        im = teaser_images[key]
        scale = max(bw / im.width, bh / im.height) * 1.08
        resized = im.resize((int(im.width * scale), int(im.height * scale)), Image.Resampling.LANCZOS)
        x = (resized.width - bw) // 2
        y = (resized.height - bh) // 2
        canvas.paste(resized.crop((x, y, x + bw, y + bh)), box)
    return canvas


INTRO_IMAGE = teaser_collage()

segments = [
    (0.00, 1.75, "intro", "QUEM E A MELHOR", "COMIDA DO BRASIL?"),
    (1.75, 2.25, "feijoada", "FEIJOADA?", ""),
    (2.25, 2.75, "churrasco", "CHURRASCO?", ""),
    (2.75, 3.25, "acaraje", "ACARAJE?", ""),
    (3.25, 4.25, "coxinha", "COXINHA?", ""),
    (4.25, 5.12, "intro", "CALMA.", ""),
    (5.12, 6.50, "mani0", "MANI\u00c7OBA", ""),
    (6.50, 8.00, "mani7", "7 DIAS", "DE PREPARO"),
    (8.00, 9.45, "mani3", "FOLHA DA", "MANDIOCA"),
    (9.45, 11.00, "mani5", "FEIJOADA", "PARAENSE"),
    (11.00, 12.55, "mani6", "PESADA.", ""),
    (12.55, 14.10, "mani8", "HISTORICA.", ""),
    (14.10, 15.70, "mani2", "ABSURDA.", ""),
    (15.70, 17.15, "mani4", "A MELHOR", "DO BRASIL"),
    (17.15, 19.00, "mani1", "NAO SE DISCUTE", ""),
    (19.00, 21.20, "mani0", "SE RESPEITA", ""),
    (21.20, 24.00, "mani7", "MANI\u00c7OBA", "NO TOPO."),
]


def current_segment(t: float):
    for idx, seg in enumerate(segments):
        if seg[0] <= t < seg[1]:
            return idx, seg
    return len(segments) - 1, segments[-1]


def source_image(source: str) -> Image.Image:
    if source == "intro":
        return INTRO_IMAGE
    if source.startswith("mani"):
        return images[int(source[4:]) % len(images)]
    return teaser_images[source]


def make_frame(t: float) -> Image.Image:
    idx, (start, end, source, line1, line2) = current_segment(t)
    local = (t - start) / max(0.001, end - start)
    im = source_image(source)

    base_zoom = 1.06 + 0.18 * ease_out(local)
    shake = 0.012 if t > 5.0 else 0.004
    ox = math.sin(t * 43.0 + idx) * shake + math.sin(t * 9.0) * 0.009
    oy = math.cos(t * 37.0 + idx * 2.1) * shake
    frame = cover(im, base_zoom, ox, oy)

    if t < 5.0:
        frame = frame.filter(ImageFilter.GaussianBlur(1.8 if idx == 0 else 0.5))
        frame = ImageEnhance.Brightness(frame).enhance(0.58)
    else:
        frame = ImageEnhance.Contrast(frame).enhance(1.18)
        frame = ImageEnhance.Color(frame).enhance(1.13)
        frame = ImageEnhance.Brightness(frame).enhance(0.82)

    frame = add_vignette(frame, 115 if t > 5 else 150)
    draw = ImageDraw.Draw(frame)

    if idx == 0:
        centered(draw, 690, line1, FONT_MID)
        centered(draw, 805, line2, FONT_MID)
        centered(draw, 1140, "?", font(150), fill=(255, 225, 40), stroke=10)
    elif 1 <= idx <= 4:
        centered(draw, 825, line1, FONT_BIG, fill=(255, 225, 40), stroke=10)
    elif idx == 5:
        centered(draw, 820, line1, FONT_BIG, fill=(255, 255, 255), stroke=10)
    elif idx == 6:
        centered(draw, 760, line1, font(166), fill=(255, 226, 40), stroke=12)
        centered(draw, 930, "A RESPOSTA.", FONT_SMALL, fill=(255, 255, 255), stroke=7)
    elif idx == len(segments) - 1:
        centered(draw, 700, line1, font(160), fill=(255, 226, 40), stroke=12)
        centered(draw, 880, line2, font(136), fill=(255, 255, 255), stroke=10)
        centered(draw, 1540, "acabou a conversa", FONT_TINY, fill=(255, 255, 255), stroke=5)
    else:
        y = 770 if line2 else 840
        centered(draw, y, line1, FONT_BIG if len(line1) < 11 else FONT_MID, fill=(255, 255, 255), stroke=10)
        if line2:
            centered(draw, y + 125, line2, FONT_BIG if len(line2) < 11 else FONT_MID, fill=(255, 226, 40), stroke=10)

    # Beat bars and tiny visual grit.
    if t > 5.0:
        beat = abs(math.sin((t - 5.12) * math.pi * 2.15))
        bar_h = int(18 + beat * 36)
        draw.rectangle((0, 0, W, bar_h), fill=(0, 0, 0))
        draw.rectangle((0, H - bar_h, W, H), fill=(0, 0, 0))
    if int(t * FPS) % 15 in (0, 1):
        frame = flash(frame, 0.22 if t > 5.0 else 0.10)

    return frame


def render() -> None:
    ffmpeg = imageio_ffmpeg.get_ffmpeg_exe()
    total = int(DURATION * FPS)
    cmd = [
        ffmpeg,
        "-y",
        "-f",
        "rawvideo",
        "-vcodec",
        "rawvideo",
        "-s",
        f"{W}x{H}",
        "-pix_fmt",
        "rgb24",
        "-r",
        str(FPS),
        "-i",
        "-",
        "-i",
        str(AUDIO),
        "-t",
        str(DURATION),
        "-map",
        "0:v:0",
        "-map",
        "1:a:0",
        "-c:v",
        "libx264",
        "-preset",
        "medium",
        "-crf",
        "18",
        "-pix_fmt",
        "yuv420p",
        "-c:a",
        "aac",
        "-b:a",
        "192k",
        "-shortest",
        "-movflags",
        "+faststart",
        str(OUT),
    ]
    proc = subprocess.Popen(cmd, stdin=subprocess.PIPE)
    assert proc.stdin is not None
    for n in range(total):
        proc.stdin.write(make_frame(n / FPS).tobytes())
    proc.stdin.close()
    code = proc.wait()
    if code:
        raise SystemExit(code)


if __name__ == "__main__":
    render()
