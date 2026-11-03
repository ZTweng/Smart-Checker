"""Generate in-app branding only; leave the original application icons unchanged."""

from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
source = Image.open(ROOT / "packaging/icons/emblem-original.png").convert("RGBA")


def logo(size):
    return source.resize((size, size), Image.Resampling.LANCZOS)


logo(2048).save(ROOT / "desktopApp/src/main/resources/samc-logo.png")
# Pre-filter for actual display densities instead of shrinking a 2048px bitmap at draw time.
for pixels in (112, 140, 168, 196, 224, 280, 336, 448):
    logo(pixels).save(ROOT / f"desktopApp/src/main/resources/samc-logo-{pixels}.png")
drawable = ROOT / "androidApp/src/main/res/drawable-nodpi"
drawable.mkdir(parents=True, exist_ok=True)
logo(1024).save(drawable / "samc_logo.png")
