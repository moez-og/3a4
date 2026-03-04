#!/usr/bin/env python3
"""sortie_recap_ai.py

Génère une vidéo récap cinématique, ultra-dynamique, style court-métrage de voyage.

Dépendances autorisées:
  - moviepy==1.0.3
  - Pillow>=11
  - numpy
  - requests (optionnel: titre/mood via OpenAI API)

Aucune autre dépendance Python externe n'est utilisée.
FFmpeg doit être installé et accessible via PATH ("ffmpeg" / "ffmpeg.exe").

Entrée JSON (inchangée):
{
  "sortie": {"id": 123, "title": "...", "city": "...", "activity": "...", "date": "..."},
  "media": [{"path": "...", "type": "IMAGE"}, ...]
}

Sortie stdout (JSON strict):
  {"ok": true, "title": "...", "mood": "...", "output": "..."}
  ou
  {"ok": false, "error": "...", "message": "..."}

Logs détaillés -> stderr.

Exit codes:
  0: succès
  2: échec

"""

from __future__ import annotations

import argparse
import json
import math
import os
import random
import subprocess
import sys
import tempfile
import time
import wave
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Iterable


# ----------------------------- Logging / helpers -----------------------------


def eprint(*a):
    print(*a, file=sys.stderr)


def log(msg: str):
    ts = datetime.now().strftime("%H:%M:%S")
    eprint(f"[{ts}] {msg}")


def safe_str(x) -> str:
    return "" if x is None else str(x)


def fail(error_code: str, message: str) -> "never":
    # stdout must be clean JSON for Java parsing.
    print(json.dumps({"ok": False, "error": error_code, "message": message}, ensure_ascii=False))
    raise SystemExit(2)


def clamp(v: float, a: float, b: float) -> float:
    return a if v < a else (b if v > b else v)


def ease_in_out(p: float) -> float:
    # Smoothstep
    p = clamp(p, 0.0, 1.0)
    return p * p * (3.0 - 2.0 * p)


def ease_out(p: float) -> float:
    p = clamp(p, 0.0, 1.0)
    return 1.0 - (1.0 - p) * (1.0 - p)


# ----------------------------- Pillow compatibility --------------------------


def patch_pillow_antialias() -> None:
    """Pillow 10+ removed PIL.Image.ANTIALIAS; MoviePy 1.0.3 may still reference it."""
    try:
        from PIL import Image as PILImage
    except Exception:
        return

    if hasattr(PILImage, "ANTIALIAS"):
        return

    try:
        PILImage.ANTIALIAS = PILImage.Resampling.LANCZOS
    except Exception:
        if hasattr(PILImage, "LANCZOS"):
            PILImage.ANTIALIAS = PILImage.LANCZOS


# ----------------------------- Fonts / drawing ------------------------------


def load_fonts():
    from PIL import ImageFont

    def try_font(names: Iterable[str], size: int):
        for n in names:
            try:
                return ImageFont.truetype(n, size)
            except Exception:
                pass
        return ImageFont.load_default()

    # Windows-friendly list
    title_font = try_font(["SegoeUI.ttf", "segoeui.ttf", "arial.ttf"], 66)
    sub_font = try_font(["SegoeUI.ttf", "segoeui.ttf", "arial.ttf"], 26)
    small_font = try_font(["SegoeUI.ttf", "segoeui.ttf", "arial.ttf"], 22)
    outro_font = try_font(["SegoeUI.ttf", "segoeui.ttf", "arial.ttf"], 48)
    return title_font, sub_font, small_font, outro_font


def wrap_text(text: str, max_chars: int, max_lines: int) -> list[str]:
    text = (text or "").strip()
    if not text:
        return ["Recap"]

    words = text.split()
    lines: list[str] = []
    cur: list[str] = []
    for w in words:
        cur.append(w)
        if len(" ".join(cur)) > max_chars:
            last = cur.pop()
            if cur:
                lines.append(" ".join(cur))
            cur = [last]
    if cur:
        lines.append(" ".join(cur))
    return lines[:max_lines]


# ----------------------------- Media selection / mood ------------------------


def guess_mood(activity: str) -> str:
    a = (activity or "").strip().lower()
    if any(k in a for k in ["sport", "vélo", "velo", "running", "randonnée", "randonnee", "fitness", "match"]):
        return "electro"
    if any(k in a for k in ["restaurant", "café", "cafe", "dîner", "diner", "brunch"]):
        return "jazz"
    if any(k in a for k in ["nature", "camp", "plage", "montagne", "lac", "forêt", "foret"]):
        return "acoustic"
    return "chill"


def openai_title_and_mood(title: str, city: str, activity: str) -> tuple[str, str]:
    """Optional. Uses requests only; no openai python dependency."""
    api_key = os.getenv("OPENAI_API_KEY")
    if not api_key:
        t = title.strip() or f"Recap — {activity or 'Sortie'}"
        return (t[:60], guess_mood(activity))

    try:
        import requests
    except Exception:
        t = title.strip() or f"Recap — {activity or 'Sortie'}"
        return (t[:60], guess_mood(activity))

    model = os.getenv("OPENAI_MODEL", "gpt-4o-mini")
    prompt = (
        "Tu es un assistant créatif. Donne un titre court et accrocheur pour une vidéo récap (max 60 caractères) "
        "et choisis une ambiance musicale (un seul mot parmi: jazz, electro, acoustic, chill).\n"
        f"Sortie: {title}\nVille: {city}\nActivité: {activity}\n"
        "Réponds en JSON strict: {\"title\":..., \"mood\":...}."
    )

    try:
        r = requests.post(
            "https://api.openai.com/v1/chat/completions",
            headers={"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"},
            json={
                "model": model,
                "messages": [
                    {"role": "system", "content": "Réponds en JSON strict."},
                    {"role": "user", "content": prompt},
                ],
                "temperature": 0.7,
            },
            timeout=25,
        )
        r.raise_for_status()
        data = r.json()
        content = data["choices"][0]["message"]["content"]
        obj = json.loads(content)
        out_title = safe_str(obj.get("title")).strip() or (title.strip() or "Recap")
        mood = safe_str(obj.get("mood")).strip().lower() or guess_mood(activity)
        if mood not in ("jazz", "electro", "acoustic", "chill"):
            mood = guess_mood(activity)
        return (out_title[:60], mood)
    except Exception as ex:
        log(f"openai_unavailable: {ex}")
        t = title.strip() or f"Recap — {activity or 'Sortie'}"
        return (t[:60], guess_mood(activity))


def pick_music(mood: str) -> str | None:
    # Optional folder of tracks.
    music_dir = os.getenv("RECAP_MUSIC_DIR", os.path.join("assets", "music"))
    if not os.path.isdir(music_dir):
        return None

    mood = (mood or "").strip().lower()

    tracks: list[str] = []
    for fn in os.listdir(music_dir):
        if fn.lower().endswith((".mp3", ".wav", ".m4a", ".aac")):
            tracks.append(os.path.join(music_dir, fn))

    if not tracks:
        return None

    # Prefer mood keyword
    prefer = [t for t in tracks if mood and mood in os.path.basename(t).lower()]
    prefer.sort()
    tracks.sort()
    return (prefer[0] if prefer else tracks[0])


# ----------------------------- FFmpeg detection ------------------------------


def ensure_ffmpeg() -> str:
    for cand in ("ffmpeg", "ffmpeg.exe"):
        try:
            subprocess.run([cand, "-version"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=4)
            return cand
        except Exception:
            pass
    raise RuntimeError(
        "ffmpeg_not_found: Installe FFmpeg puis ajoute-le au PATH. Téléchargement: https://ffmpeg.org/download.html"
    )


# ----------------------------- Image validation & prep -----------------------


@dataclass(frozen=True)
class Photo:
    path: Path
    rgb: "object"  # numpy array HxWx3
    w: int
    h: int


def load_image_rgb(path: Path, timeout_s: float) -> Photo | None:
    t0 = time.monotonic()
    try:
        from PIL import Image, ImageOps
        import numpy as np

        with Image.open(path) as im:
            im = ImageOps.exif_transpose(im)
            im = im.convert("RGB")
            w, h = im.size
            if w < 16 or h < 16:
                log(f"skip_image (too small): {path} ({w}x{h})")
                return None
            arr = np.array(im)  # explicit numpy array

        if (time.monotonic() - t0) > timeout_s:
            log(f"skip_image (timeout>{timeout_s}s): {path}")
            return None

        return Photo(path=path, rgb=arr, w=int(w), h=int(h))

    except Exception as ex:
        log(f"skip_image (corrupted): {path} :: {ex}")
        return None


# ----------------------------- Vignette mask ---------------------------------


def build_vignette_mask(width: int, height: int, strength: float = 0.40):
    """Returns float mask in [0..1] where edges are 1 and center is 0."""
    import numpy as np

    y = np.linspace(-1.0, 1.0, height, dtype=np.float32)
    x = np.linspace(-1.0, 1.0, width, dtype=np.float32)
    xx, yy = np.meshgrid(x, y)
    rr = np.sqrt(xx * xx + yy * yy)
    rr = np.clip(rr, 0.0, 1.0)
    # soften curve
    mask = rr ** 1.8
    return np.clip(mask * float(strength), 0.0, 1.0)


def apply_vignette(frame_rgb, vignette_mask):
    import numpy as np

    f = frame_rgb.astype(np.float32)
    # multiply RGB by (1 - mask)
    m = 1.0 - vignette_mask[..., None]
    out = f * m
    return np.clip(out, 0, 255).astype(np.uint8)


# ----------------------------- Overlay compositing ---------------------------


def alpha_composite_rgb(base_rgb, overlay_rgba):
    """Alpha-composite RGBA overlay onto RGB base, all numpy uint8."""
    import numpy as np

    if overlay_rgba is None:
        return base_rgb

    b = base_rgb.astype(np.float32)
    o = overlay_rgba.astype(np.float32)
    a = (o[..., 3:4] / 255.0)
    rgb = o[..., 0:3]
    out = rgb * a + b * (1.0 - a)
    return np.clip(out, 0, 255).astype(np.uint8)


def draw_overlay(width: int, height: int, idx: int, total: int, frac: float):
    """Creates RGBA overlay with index (01,02,...) and progress bar."""
    from PIL import Image, ImageDraw
    import numpy as np

    _, _, small_font, _ = load_fonts()

    im = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    d = ImageDraw.Draw(im, "RGBA")

    pad = 22
    bar_h = 4

    # Progress bar background
    bx0, by0 = pad, height - pad
    bx1, by1 = width - pad, by0 + bar_h
    d.rounded_rectangle((bx0, by0, bx1, by1), radius=999, fill=(255, 255, 255, 35))

    frac = clamp(frac, 0.0, 1.0)
    fx1 = int(bx0 + (bx1 - bx0) * frac)
    if fx1 > bx0:
        d.rounded_rectangle((bx0, by0, fx1, by1), radius=999, fill=(255, 255, 255, 85))

    # Index tag bottom-right
    tag = f"{idx:02d}"
    bbox = d.textbbox((0, 0), tag, font=small_font)
    tw = bbox[2] - bbox[0]
    th = bbox[3] - bbox[1]

    px = width - pad - tw - 18
    py = height - pad - th - 18 - bar_h
    d.rounded_rectangle((px - 10, py - 7, px + tw + 10, py + th + 7), radius=999, fill=(0, 0, 0, 80))
    d.text((px, py), tag, font=small_font, fill=(255, 255, 255, 210))

    return np.array(im)


# ----------------------------- Ken Burns plans --------------------------------


@dataclass(frozen=True)
class KenBurnsPlan:
    mode: str
    # normalized start/end: centers in [0..1]
    cx0: float
    cy0: float
    cx1: float
    cy1: float
    z0: float
    z1: float
    rot0_deg: float
    rot1_deg: float


def make_plan(rng: random.Random) -> KenBurnsPlan:
    modes = [
        "zoom_in_center",
        "zoom_out_corner",
        "pan_lr",
        "pan_tb",
        "rotate_zoom",
    ]
    mode = rng.choice(modes)

    if mode == "zoom_in_center":
        return KenBurnsPlan(mode, 0.50, 0.50, 0.50, 0.50, rng.uniform(1.02, 1.07), rng.uniform(1.10, 1.18), 0.0, 0.0)

    if mode == "zoom_out_corner":
        corner = rng.choice([(0.15, 0.15), (0.85, 0.15), (0.15, 0.85), (0.85, 0.85)])
        return KenBurnsPlan(mode, corner[0], corner[1], 0.50, 0.50, rng.uniform(1.14, 1.22), rng.uniform(1.02, 1.08), 0.0, 0.0)

    if mode == "pan_lr":
        y = rng.uniform(0.35, 0.65)
        left = rng.uniform(0.15, 0.30)
        right = rng.uniform(0.70, 0.85)
        return KenBurnsPlan(mode, left, y, right, y, rng.uniform(1.06, 1.14), rng.uniform(1.06, 1.14), 0.0, 0.0)

    if mode == "pan_tb":
        x = rng.uniform(0.35, 0.65)
        top = rng.uniform(0.15, 0.30)
        bot = rng.uniform(0.70, 0.85)
        return KenBurnsPlan(mode, x, top, x, bot, rng.uniform(1.06, 1.14), rng.uniform(1.06, 1.14), 0.0, 0.0)

    # rotate_zoom
    return KenBurnsPlan(
        mode,
        rng.uniform(0.40, 0.60),
        rng.uniform(0.40, 0.60),
        rng.uniform(0.40, 0.60),
        rng.uniform(0.40, 0.60),
        rng.uniform(1.03, 1.09),
        rng.uniform(1.12, 1.20),
        rng.uniform(-2.0, -0.8),
        rng.uniform(0.8, 2.0),
    )


def render_kenburns_frame(photo: Photo, plan: KenBurnsPlan, width: int, height: int, t: float, dur: float):
    """Returns RGB numpy frame (uint8) at time t."""
    from PIL import Image
    import numpy as np

    p = 0.0 if dur <= 0 else clamp(t / dur, 0.0, 1.0)
    p2 = ease_in_out(p)

    cx = plan.cx0 + (plan.cx1 - plan.cx0) * p2
    cy = plan.cy0 + (plan.cy1 - plan.cy0) * p2
    zoom = plan.z0 + (plan.z1 - plan.z0) * p2

    rot = plan.rot0_deg + (plan.rot1_deg - plan.rot0_deg) * p2
    if plan.mode == "rotate_zoom":
        # slight oscillation
        rot += math.sin(p * math.tau) * 0.35

    base = Image.fromarray(photo.rgb)

    # Cover scale (ensure image covers canvas at minimum zoom)
    scale_cover = max(width / photo.w, height / photo.h)
    scale = scale_cover * zoom

    new_w = max(2, int(photo.w * scale))
    new_h = max(2, int(photo.h * scale))
    im = base.resize((new_w, new_h), resample=Image.Resampling.LANCZOS)

    # rotation
    if abs(rot) > 1e-3:
        im = im.rotate(rot, resample=Image.Resampling.BICUBIC, expand=True, fillcolor=(0, 0, 0))

    iw, ih = im.size

    # crop center coordinates
    x_center = int(iw * cx)
    y_center = int(ih * cy)

    # clamp crop rectangle
    x0 = clamp(x_center - width // 2, 0, max(0, iw - width))
    y0 = clamp(y_center - height // 2, 0, max(0, ih - height))
    x0 = int(x0)
    y0 = int(y0)

    crop = im.crop((x0, y0, x0 + width, y0 + height))
    return np.array(crop)


# ----------------------------- Transitions -----------------------------------


def _motion_blur_h(frame_rgb, strength: int):
    """Simple horizontal motion blur via shifted averaging."""
    import numpy as np

    strength = int(clamp(strength, 1, 24))
    acc = frame_rgb.astype(np.float32)
    count = 1

    # symmetric shifts
    for s in range(1, strength + 1):
        shifted1 = np.zeros_like(frame_rgb)
        shifted2 = np.zeros_like(frame_rgb)
        shifted1[:, s:, :] = frame_rgb[:, :-s, :]
        shifted2[:, :-s, :] = frame_rgb[:, s:, :]
        acc += shifted1.astype(np.float32)
        acc += shifted2.astype(np.float32)
        count += 2

    return np.clip(acc / float(count), 0, 255).astype(np.uint8)


def transition_frame(kind: str, a_rgb, b_rgb, width: int, height: int, t: float, dur: float):
    """Create transition RGB frame between a and b (both uint8 RGB)."""
    from PIL import Image
    import numpy as np

    p = 0.0 if dur <= 0 else clamp(t / dur, 0.0, 1.0)
    p2 = ease_in_out(p)

    kind = (kind or "crossfade").lower()

    if kind == "crossfade":
        out = a_rgb.astype(np.float32) * (1.0 - p2) + b_rgb.astype(np.float32) * p2
        return np.clip(out, 0, 255).astype(np.uint8)

    if kind in ("slide_left", "slide_right"):
        dir_sign = -1 if kind == "slide_left" else 1
        shift = int(width * p2) * dir_sign

        out = np.zeros((height, width, 3), dtype=np.uint8)

        # A moves out
        ax0 = shift
        if ax0 >= 0:
            # A shifted right
            src = a_rgb[:, : width - ax0, :]
            out[:, ax0:, :] = src
        else:
            # A shifted left
            src = a_rgb[:, -ax0 :, :]
            out[:, : width + ax0, :] = src

        # B moves in
        bx0 = shift - (width * dir_sign)
        if bx0 >= 0:
            src = b_rgb[:, : width - bx0, :]
            out[:, bx0:, :] = src
        else:
            src = b_rgb[:, -bx0 :, :]
            out[:, : width + bx0, :] = src

        return out

    if kind == "zoom_explosion":
        # Next clip explodes from center with fade
        # Scale from 0.65 -> 1.0
        z = 0.65 + 0.35 * ease_out(p)
        b_pil = Image.fromarray(b_rgb)
        scaled = b_pil.resize((max(2, int(width * z)), max(2, int(height * z))), resample=Image.Resampling.LANCZOS)

        canvas = Image.new("RGB", (width, height), (0, 0, 0))
        x = (width - scaled.size[0]) // 2
        y = (height - scaled.size[1]) // 2
        canvas.paste(scaled, (x, y))

        b2 = np.array(canvas)
        out = a_rgb.astype(np.float32) * (1.0 - p2) + b2.astype(np.float32) * p2
        return np.clip(out, 0, 255).astype(np.uint8)

    if kind == "whip_pan":
        # Whip-pan: fast blur + slide + crossfade
        # blur peaks mid-transition
        blur_strength = int(2 + 14 * (1.0 - abs(2.0 * p - 1.0)))
        a_blur = _motion_blur_h(a_rgb, blur_strength)
        b_blur = _motion_blur_h(b_rgb, blur_strength)

        # slide amount
        shift = int(width * (0.25 + 0.75 * p2))
        out = np.zeros((height, width, 3), dtype=np.uint8)

        # A slides out left
        if shift < width:
            out[:, : width - shift, :] = a_blur[:, shift:, :]

        # B slides in from right
        if shift < width:
            out[:, width - shift :, :] = b_blur[:, :shift, :]
        else:
            out[:, :, :] = b_blur

        # add crossfade tint
        out2 = out.astype(np.float32) * 0.70 + (b_rgb.astype(np.float32) * p2 + a_rgb.astype(np.float32) * (1.0 - p2)) * 0.30
        return np.clip(out2, 0, 255).astype(np.uint8)

    # fallback
    out = a_rgb.astype(np.float32) * (1.0 - p2) + b_rgb.astype(np.float32) * p2
    return np.clip(out, 0, 255).astype(np.uint8)


# ----------------------------- Intro / Outro rendering ------------------------


def build_gradient_background(width: int, height: int, t: float, dur: float):
    """Blue night -> deep violet gradient appearing progressively."""
    import numpy as np

    # progress from black
    p = 0.0 if dur <= 0 else clamp(t / dur, 0.0, 1.0)

    top = np.array([10, 18, 46], dtype=np.float32)  # blue night
    bot = np.array([52, 16, 86], dtype=np.float32)  # deep violet

    y = np.linspace(0.0, 1.0, height, dtype=np.float32)[:, None]
    grad = top * (1.0 - y) + bot * y
    img = np.tile(grad[None, :, :], (width, 1, 1)).transpose(1, 0, 2)

    # subtle vignette-like darkening
    xx = np.linspace(-1.0, 1.0, width, dtype=np.float32)[None, :]
    yy = np.linspace(-1.0, 1.0, height, dtype=np.float32)[:, None]
    rr = np.sqrt(xx * xx + yy * yy)
    rr = np.clip(rr, 0.0, 1.0)
    dark = 1.0 - 0.22 * (rr ** 1.6)
    img = img * dark[..., None]

    # appear from black
    img = img * p

    return np.clip(img, 0, 255).astype(np.uint8)


def render_intro_frame(width: int, height: int, title: str, subtitle: str, t: float, dur: float):
    from PIL import Image, ImageDraw
    import numpy as np

    bg = build_gradient_background(width, height, t, dur)
    im = Image.fromarray(bg)
    d = ImageDraw.Draw(im, "RGBA")

    title_font, sub_font, _, _ = load_fonts()

    # timing
    type_start = 0.55
    type_end = dur * 0.62

    # typewriter
    p_type = 0.0
    if t <= type_start:
        p_type = 0.0
    elif t >= type_end:
        p_type = 1.0
    else:
        p_type = (t - type_start) / max(1e-6, (type_end - type_start))

    lines = wrap_text(title, max_chars=26, max_lines=3)
    full = "\n".join(lines)
    shown = full[: int(len(full) * p_type)]

    # Title position
    y = int(height * 0.30)
    for line in shown.split("\n"):
        if not line:
            continue
        bbox = d.textbbox((0, 0), line, font=title_font)
        tw = bbox[2] - bbox[0]
        th = bbox[3] - bbox[1]
        x = (width - tw) // 2

        # glow: multiple draws
        for dx, dy, a in [(-2, 0, 40), (2, 0, 40), (0, -2, 40), (0, 2, 40), (0, 0, 55)]:
            d.text((x + dx, y + dy), line, font=title_font, fill=(255, 255, 255, a))

        # main
        d.text((x, y), line, font=title_font, fill=(255, 255, 255, 235))
        y += th + 10

    # Animated bar under the last title line
    bar_p = ease_out(p_type)
    bar_w = int(width * 0.52 * bar_p)
    bar_h = 3
    bx0 = (width - bar_w) // 2
    by0 = int(height * 0.56)
    d.rounded_rectangle((bx0, by0, bx0 + bar_w, by0 + bar_h), radius=999, fill=(255, 255, 255, int(110 + 70 * bar_p)))

    # Subtitle slides from bottom with fade
    if subtitle.strip():
        sub_p = clamp((t - (type_start + 0.25)) / 0.85, 0.0, 1.0)
        sub_p2 = ease_out(sub_p)
        bbox = d.textbbox((0, 0), subtitle, font=sub_font)
        tw = bbox[2] - bbox[0]
        th = bbox[3] - bbox[1]
        x = (width - tw) // 2
        y_sub = int(height * 0.68 + (1.0 - sub_p2) * 40)
        d.text((x, y_sub), subtitle, font=sub_font, fill=(220, 230, 255, int(220 * sub_p2)))

    # Subtle flash when title completes
    flash_t = type_end
    if abs(t - flash_t) <= 0.20 and p_type >= 0.98:
        k = 1.0 - abs(t - flash_t) / 0.20
        alpha = int(110 * (k ** 1.5))
        d.rectangle((0, 0, width, height), fill=(255, 255, 255, alpha))

    # Fade in/out
    fade_in = clamp(t / 0.6, 0.0, 1.0)
    fade_out = clamp((dur - t) / 0.7, 0.0, 1.0)
    a = min(fade_in, fade_out)
    if a < 0.999:
        black = Image.new("RGB", (width, height), (0, 0, 0))
        im = Image.blend(black, im.convert("RGB"), a)

    return np.array(im.convert("RGB"))


def render_outro_frame(width: int, height: int, last_rgb, title: str, date: str, t: float, dur: float):
    from PIL import Image, ImageDraw
    import numpy as np

    p = 0.0 if dur <= 0 else clamp(t / dur, 0.0, 1.0)
    p2 = ease_in_out(p)

    # Freeze + slow zoom out
    # Start slightly zoomed-in, then zoom out
    z = 1.08 - 0.08 * p2

    base = Image.fromarray(last_rgb)
    scaled = base.resize((max(2, int(width * z)), max(2, int(height * z))), resample=Image.Resampling.LANCZOS)
    canvas = Image.new("RGB", (width, height), (0, 0, 0))
    x = (width - scaled.size[0]) // 2
    y = (height - scaled.size[1]) // 2
    canvas.paste(scaled, (x, y))

    im = canvas.convert("RGBA")
    d = ImageDraw.Draw(im, "RGBA")

    _, _, sub_font, outro_font = load_fonts()

    # Fade to black progressively
    fade = clamp((p - 0.25) / 0.75, 0.0, 1.0)
    if fade > 0:
        d.rectangle((0, 0, width, height), fill=(0, 0, 0, int(220 * fade)))

    # Pulse main text
    pulse = 0.5 + 0.5 * math.sin(2.0 * math.pi * 1.2 * t)
    alpha = int(210 * clamp((p - 0.12) / 0.25, 0.0, 1.0) * (0.80 + 0.20 * pulse))
    scale = 1.0 + 0.02 * pulse

    main = "Merci d'avoir participé 🎉"
    bbox = d.textbbox((0, 0), main, font=outro_font)
    tw = bbox[2] - bbox[0]
    th = bbox[3] - bbox[1]

    x0 = (width - int(tw * scale)) // 2
    y0 = int(height * 0.40)

    # Draw with simple glow
    for dx, dy, a in [(-2, 0, 40), (2, 0, 40), (0, -2, 40), (0, 2, 40)]:
        d.text((x0 + dx, y0 + dy), main, font=outro_font, fill=(255, 255, 255, int(a * alpha / 255)))
    d.text((x0, y0), main, font=outro_font, fill=(255, 255, 255, alpha))

    sub = (title or "").strip()
    sub2 = (date or "").strip()
    footer = sub + (" • " + sub2 if sub and sub2 else sub2)

    if footer:
        bbox = d.textbbox((0, 0), footer, font=sub_font)
        tw2 = bbox[2] - bbox[0]
        x2 = (width - tw2) // 2
        y2 = int(height * 0.55)
        d.text((x2, y2), footer, font=sub_font, fill=(230, 235, 255, int(alpha * 0.90)))

    # Final fade to black
    end_fade = clamp((p - 0.85) / 0.15, 0.0, 1.0)
    if end_fade > 0:
        d.rectangle((0, 0, width, height), fill=(0, 0, 0, int(255 * end_fade)))

    return np.array(im.convert("RGB"))


# ----------------------------- BPM estimation --------------------------------


def ffmpeg_to_wav(ffmpeg: str, src: str, dst: str) -> None:
    cmd = [
        ffmpeg,
        "-y",
        "-i",
        src,
        "-ac",
        "1",
        "-ar",
        "44100",
        "-vn",
        dst,
    ]
    r = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if r.returncode != 0:
        err = (r.stderr or b"").decode("utf-8", errors="ignore")
        raise RuntimeError(f"ffmpeg_audio_extract_failed: {err[:260]}")


def estimate_bpm_from_wav(path_wav: str) -> float:
    """Lightweight BPM estimate using energy envelope + autocorrelation.

    No external libs. Not perfect, but good enough for a default sync.
    """
    import numpy as np

    with wave.open(path_wav, "rb") as wf:
        sr = wf.getframerate()
        n = wf.getnframes()
        raw = wf.readframes(n)

    # int16 mono
    audio = np.frombuffer(raw, dtype=np.int16).astype(np.float32)
    if audio.size < sr * 2:
        return 120.0

    audio /= max(1.0, float(np.max(np.abs(audio))))

    # Energy envelope
    frame = 1024
    hop = 512
    count = 1 + (audio.size - frame) // hop if audio.size >= frame else 0
    if count <= 20:
        return 120.0

    env = np.empty(count, dtype=np.float32)
    for i in range(count):
        seg = audio[i * hop : i * hop + frame]
        env[i] = float(np.sqrt(np.mean(seg * seg) + 1e-8))

    env -= float(np.mean(env))
    env = np.maximum(env, 0.0)

    # Autocorrelation
    ac = np.correlate(env, env, mode="full")[count - 1 :]
    ac[0] = 0.0

    # Search tempo range 60..180 BPM
    bpm_min, bpm_max = 60.0, 180.0
    lag_min = int((60.0 / bpm_max) * (sr / hop))
    lag_max = int((60.0 / bpm_min) * (sr / hop))
    lag_min = max(1, lag_min)
    lag_max = max(lag_min + 1, lag_max)

    window = ac[lag_min:lag_max]
    if window.size <= 0:
        return 120.0

    best = int(np.argmax(window)) + lag_min
    bpm = 60.0 * (sr / hop) / float(best)

    if not (bpm_min - 1 <= bpm <= bpm_max + 1):
        return 120.0

    # Normalize to common doubles/halves
    while bpm < 80:
        bpm *= 2
    while bpm > 170:
        bpm /= 2

    return float(clamp(bpm, 60.0, 180.0))


# ----------------------------- Movie assembly --------------------------------


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--input", required=True, help="input JSON")
    ap.add_argument("--output", required=True, help="output mp4")
    ap.add_argument("--width", type=int, default=1280)
    ap.add_argument("--height", type=int, default=720)
    ap.add_argument("--fps", type=int, default=30)
    ap.add_argument("--photo_duration", type=float, default=3.5)
    ap.add_argument("--transition", type=float, default=0.60)
    args = ap.parse_args()

    width = max(640, int(args.width))
    height = max(360, int(args.height))
    fps = max(15, int(args.fps))
    photo_duration_target = max(1.0, float(args.photo_duration))
    trans_dur = clamp(float(args.transition), 0.25, 1.2)

    patch_pillow_antialias()

    try:
        ffmpeg = ensure_ffmpeg()
        log(f"ffmpeg: {ffmpeg}")
    except Exception as ex:
        fail("ffmpeg_not_found", safe_str(ex))

    try:
        from moviepy.editor import VideoClip, concatenate_videoclips, AudioFileClip
    except Exception as ex:
        fail("missing_dependency", f"moviepy manquant: {ex}")

    try:
        import numpy as np
    except Exception as ex:
        fail("missing_dependency", f"numpy manquant: {ex}")

    try:
        from PIL import Image  # noqa: F401
    except Exception as ex:
        fail("missing_dependency", f"Pillow manquant: {ex}")

    input_path = Path(args.input)
    out_path = Path(args.output)

    if not input_path.exists():
        fail("bad_input", f"Input JSON introuvable: {input_path}")

    try:
        payload = json.loads(input_path.read_text(encoding="utf-8"))
    except Exception as ex:
        fail("bad_input", f"JSON invalide: {ex}")

    sortie = payload.get("sortie") or {}
    media = payload.get("media") or []

    sortie_id = int(sortie.get("id") or 0)
    title0 = safe_str(sortie.get("title"))
    city = safe_str(sortie.get("city"))
    activity = safe_str(sortie.get("activity"))
    date = safe_str(sortie.get("date"))

    video_title, mood = openai_title_and_mood(title0, city, activity)
    subtitle = " • ".join([x for x in [city.strip(), activity.strip()] if x])

    # Collect photos (skip corrupted)
    rng = random.Random(sortie_id if sortie_id else 1337)

    log(f"payload: sortie_id={sortie_id} media_count={len(media)}")

    photos: list[Photo] = []
    max_media = 60  # hard safety cap

    for it in media[:max_media]:
        try:
            p = Path(safe_str((it or {}).get("path")))
            t = safe_str((it or {}).get("type")).upper().strip()
            if not p.exists():
                log(f"skip_missing: {p}")
                continue
            if t and t != "IMAGE":
                # This cinematic prompt focuses on photos. We ignore videos gracefully.
                log(f"skip_non_image: {p} type={t}")
                continue

            ph = load_image_rgb(p, timeout_s=45.0)
            if ph is not None:
                photos.append(ph)
        except Exception as ex:
            log(f"skip_media: {it} :: {ex}")

    if len(photos) < 1:
        fail("not_enough_valid_media", "Minimum 1 photo valide requise (toutes les photos sont illisibles/corrompues).")

    total = len(photos)
    log(f"valid_photos={total}")

    # Music + BPM
    music_path = pick_music(mood)
    bpm = 120.0

    if music_path:
        log(f"music: {music_path}")
        try:
            with tempfile.TemporaryDirectory() as td:
                wav_path = os.path.join(td, "music.wav")
                ffmpeg_to_wav(ffmpeg, music_path, wav_path)
                bpm = estimate_bpm_from_wav(wav_path)
                log(f"bpm_estimated={bpm:.1f}")
        except Exception as ex:
            log(f"bpm_estimation_failed -> fallback 120: {ex}")
            bpm = 120.0
    else:
        log("music: none (fallback bpm=120)")

    beat_sec = 60.0 / float(bpm)

    # Keep each photo around 3.5s but quantize to beats (closest)
    beats_per_photo = max(1, int(round(photo_duration_target / beat_sec)))
    photo_duration = beats_per_photo * beat_sec
    log(f"timing: beat_sec={beat_sec:.3f} beats_per_photo={beats_per_photo} photo_duration={photo_duration:.3f}")

    vignette = build_vignette_mask(width, height, strength=0.45)

    # Precompute plans
    plans: list[KenBurnsPlan] = []
    for i in range(total):
        plans.append(make_plan(rng))

    # Render functions
    def render_photo(i: int, t: float, dur: float):
        rgb = render_kenburns_frame(photos[i], plans[i], width, height, t, dur)
        rgb = apply_vignette(rgb, vignette)

        # overall progress (0..1) across the whole film, including within-photo
        frac = ((i + (t / max(1e-6, dur))) / float(total))
        overlay = draw_overlay(width, height, i + 1, total, frac)
        rgb = alpha_composite_rgb(rgb, overlay)
        return rgb

    # Build clips list
    intro_dur = 3.6
    outro_dur = 2.6

    intro_clip = VideoClip(
        make_frame=lambda t: render_intro_frame(width, height, video_title, subtitle, float(t), intro_dur),
        duration=intro_dur,
    )

    clips = [intro_clip]

    # Photo clips and transitions (alternating)
    transition_kinds = ["crossfade", "slide_left", "slide_right", "zoom_explosion", "whip_pan"]

    photo_clips: list[VideoClip] = []
    for i in range(total):
        c = VideoClip(make_frame=lambda t, i=i: render_photo(i, float(t), photo_duration), duration=photo_duration)
        photo_clips.append(c)

    for i in range(total):
        clips.append(photo_clips[i])
        if i < total - 1:
            kind = transition_kinds[i % len(transition_kinds)]

            # Capture end/start frames once (fast transitions)
            a_tail = render_photo(i, photo_duration * 0.999, photo_duration)
            b_head = render_photo(i + 1, 0.0, photo_duration)

            tr_clip = VideoClip(
                make_frame=lambda t, kind=kind, a=a_tail, b=b_head: transition_frame(
                    kind, a, b, width, height, float(t), trans_dur
                ),
                duration=trans_dur,
            )
            clips.append(tr_clip)

    # Outro based on last photo end
    last_frame = render_photo(total - 1, photo_duration * 0.999, photo_duration)

    outro_clip = VideoClip(
        make_frame=lambda t: render_outro_frame(width, height, last_frame, video_title, date, float(t), outro_dur),
        duration=outro_dur,
    )
    clips.append(outro_clip)

    final = concatenate_videoclips(clips, method="compose")

    # Music
    if music_path:
        try:
            audio = AudioFileClip(music_path)
            # loop/cut to fit
            if audio.duration and audio.duration > 0:
                if audio.duration < final.duration:
                    # loop by concatenation (simple)
                    loops = int(math.ceil(final.duration / audio.duration))
                    audio = audio.fx(lambda a: a, )  # keep reference
                    from moviepy.editor import concatenate_audioclips

                    audio = concatenate_audioclips([audio] * loops)

                audio = audio.subclip(0, final.duration)

                # volume + fades
                audio = audio.volumex(0.25).audio_fadein(min(1.0, intro_dur)).audio_fadeout(min(1.2, outro_dur))
                final = final.set_audio(audio)
        except Exception as ex:
            log(f"music_attach_failed: {ex}")

    # Export
    out_path.parent.mkdir(parents=True, exist_ok=True)

    log(f"write: {out_path} (fps={fps}, size={width}x{height})")

    try:
        final.write_videofile(
            str(out_path),
            fps=fps,
            codec="libx264",
            audio_codec="aac",
            preset="fast",
            threads=2,
            ffmpeg_params=[
                "-pix_fmt",
                "yuv420p",
                "-profile:v",
                "baseline",
                "-level",
                "3.0",
                "-movflags",
                "+faststart",
            ],
            verbose=False,
            logger=None,
        )
    except Exception as ex:
        fail("generation_failed", safe_str(ex))

    print(json.dumps({"ok": True, "title": video_title, "mood": mood, "output": str(out_path)}, ensure_ascii=False))


if __name__ == "__main__":
    try:
        main()
    except SystemExit:
        raise
    except Exception as ex:
        # last-resort JSON error
        log(f"fatal: {ex}")
        print(json.dumps({"ok": False, "error": "generation_failed", "message": safe_str(ex)}, ensure_ascii=False))
        raise SystemExit(2)
