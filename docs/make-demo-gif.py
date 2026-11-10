#!/usr/bin/env python3
"""Builds docs/images/store-demo.gif from the four in-game arena captures in docs/images/.

Usage: python3 docs/make-demo-gif.py [output.gif]
"""
import sys
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
IMG_DIR = ROOT / "docs" / "images"
OUT_PATH = Path(sys.argv[1]) if len(sys.argv) > 1 else (IMG_DIR / "store-demo.gif")

W, H = 840, 472

BG_GLASS = (17, 19, 26, 235)
BORDER = (58, 66, 82, 255)
TEXT_MAIN = (245, 247, 250, 255)
TEXT_SUB = (190, 198, 214, 255)
DOT_IDLE = (68, 76, 94, 255)


def load_font(size: int, bold: bool = False):
    candidates = (
        ["segoeuib.ttf", "arialbd.ttf", "DejaVuSans-Bold.ttf"]
        if bold
        else ["segoeui.ttf", "arial.ttf", "DejaVuSans.ttf"]
    )
    for name in candidates:
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            continue
    return ImageFont.load_default()


FONT_TITLE = load_font(16, bold=True)
FONT_SUB = load_font(13, bold=False)
FONT_BADGE = load_font(11, bold=True)


def clean_chat_area(img: Image.Image, clean_ref: Image.Image) -> Image.Image:
    """Replaces any bottom-left chat box with the clean stone-brick floor from store-hud.png."""
    out = img.copy()
    box = (0, int(img.height * 0.72), int(img.width * 0.48), int(img.height * 0.93))
    patch = clean_ref.crop(box)
    out.paste(patch, box)
    return out


def render_banner(
    base: Image.Image,
    step_idx: int,
    total_steps: int,
    tag: str,
    title: str,
    subtitle: str,
    accent: tuple[int, int, int],
    progress: float,
) -> Image.Image:
    frame = base.convert("RGBA")
    overlay = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    draw = ImageDraw.Draw(overlay)

    # Top-right header card: sits in the empty night sky to the right of the Stealth HUD
    bw, bh = 626, 62
    bx = W - bw - 12
    by = 10

    draw.rounded_rectangle(
        [bx, by, bx + bw, by + bh],
        radius=7,
        fill=BG_GLASS,
        outline=BORDER,
        width=1,
    )

    # Left accent stripe
    draw.rounded_rectangle(
        [bx + 1, by + 1, bx + 6, by + bh - 1],
        radius=3,
        fill=accent + (255,),
    )

    # Tag pill
    tag_w = int(draw.textlength(tag, font=FONT_BADGE)) + 16
    tx, ty = bx + 16, by + 10
    draw.rounded_rectangle(
        [tx, ty, tx + tag_w, ty + 19],
        radius=4,
        fill=(accent[0] // 4 + 18, accent[1] // 4 + 20, accent[2] // 4 + 26, 255),
        outline=accent + (215,),
        width=1,
    )
    draw.text((tx + 8, ty + 2), tag, font=FONT_BADGE, fill=accent + (255,))

    # Title next to tag
    draw.text((tx + tag_w + 10, by + 8), title, font=FONT_TITLE, fill=TEXT_MAIN)

    # Subtitle on second row
    draw.text((bx + 16, by + 34), subtitle, font=FONT_SUB, fill=TEXT_SUB)

    # Step dots at top-right of banner
    dot_r = 4
    dots_w = total_steps * (dot_r * 2 + 6)
    dx0 = bx + bw - dots_w - 10
    dy0 = by + 16
    for i in range(total_steps):
        cx = dx0 + i * (dot_r * 2 + 6)
        color = accent + (255,) if i == step_idx else DOT_IDLE
        draw.ellipse([cx - dot_r, dy0 - dot_r, cx + dot_r, dy0 + dot_r], fill=color)

    # Progress bar along bottom edge of card
    prog_w = int((bw - 16) * max(0.0, min(1.0, progress)))
    if prog_w > 0:
        draw.rectangle(
            [bx + 8, by + bh - 3, bx + 8 + prog_w, by + bh - 2],
            fill=accent + (225,),
        )

    return Image.alpha_composite(frame, overlay).convert("RGB")


def build_shared_palette(key_frames: list[Image.Image]) -> Image.Image:
    """Creates a single 256-color palette image covering all four slides so colors stay vivid."""
    strip = Image.new("RGB", (W * len(key_frames), H))
    for i, f in enumerate(key_frames):
        strip.paste(f, (i * W, 0))
    return strip.quantize(colors=256, method=Image.Quantize.MAXCOVERAGE)


def main():
    raw_debug = Image.open(IMG_DIR / "store-debug.png").convert("RGB")
    raw_hearing = Image.open(IMG_DIR / "store-hearing.png").convert("RGB")
    raw_hud = Image.open(IMG_DIR / "store-hud.png").convert("RGB")
    raw_status = Image.open(IMG_DIR / "store-status.png").convert("RGB")

    s1 = clean_chat_area(raw_debug, raw_hud).resize((W, H), Image.Resampling.LANCZOS)
    s2 = clean_chat_area(raw_hearing, raw_hud).resize((W, H), Image.Resampling.LANCZOS)
    s3 = raw_hud.resize((W, H), Image.Resampling.LANCZOS)
    s4 = clean_chat_area(raw_status, raw_hud).resize((W, H), Image.Resampling.LANCZOS)

    slides = [
        (
            s1,
            "1 · HEARING & WALLS",
            "Mobs Hear Footsteps, Blocks & Gunshots",
            "Stone walls, wool, rain and Feather Falling muffle noise; open doors let it through.",
            (255, 196, 76),
        ),
        (
            s2,
            "2 · INVESTIGATION & GUNS",
            "Alerted Mobs Walk to the Sound Source",
            "Supports TaCZ, PointBlank, CGM & Scorched Guns — suppressors & wall bullet impacts.",
            (255, 120, 90),
        ),
        (
            s3,
            "3 · VOICE, LIGHT & SCENT",
            "Simple Voice Chat, Torchlight & Blood Trails",
            "Mobs hear you speak, spot held torches from afar, and track wounded players' blood.",
            (235, 82, 80),
        ),
        (
            s4,
            "4 · STEALTH & LIVE CONFIG",
            "Sneak in the Dark + 45+ Settings & Presets",
            "Server-side (vanilla clients can join) · Fabric, Quilt, Forge, NeoForge & Paper 1.16.5–26.3.",
            (88, 214, 141),
        ),
    ]

    key_rendered = [
        render_banner(img, idx, len(slides), tag, title, sub, accent, 1.0)
        for idx, (img, tag, title, sub, accent) in enumerate(slides)
    ]
    master_palette = build_shared_palette(key_rendered)

    frames: list[Image.Image] = []
    durations: list[int] = []

    substeps = 5
    for idx, (img, tag, title, sub, accent) in enumerate(slides):
        for step in range(substeps):
            prog = (step + 1) / substeps
            rendered = render_banner(img, idx, len(slides), tag, title, sub, accent, prog)
            frames.append(rendered)
            durations.append(340 if step < substeps - 1 else 520)

        next_idx = (idx + 1) % len(slides)
        cur_end = key_rendered[idx]
        nxt_img, n_tag, n_title, n_sub, n_accent = slides[next_idx]
        nxt_start = render_banner(nxt_img, next_idx, len(slides), n_tag, n_title, n_sub, n_accent, 0.2)
        for alpha in (0.35, 0.7):
            blended = Image.blend(cur_end, nxt_start, alpha)
            frames.append(blended)
            durations.append(90)

    quantized = [
        f.quantize(colors=256, palette=master_palette, dither=Image.Dither.FLOYDSTEINBERG)
        for f in frames
    ]
    OUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    quantized[0].save(
        OUT_PATH,
        save_all=True,
        append_images=quantized[1:],
        duration=durations,
        loop=0,
        optimize=True,
    )
    print(f"Wrote {OUT_PATH} ({OUT_PATH.stat().st_size / 1024:.1f} KB, {len(quantized)} frames)")


if __name__ == "__main__":
    main()
