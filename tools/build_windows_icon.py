"""Build lossless RGBA Windows icon frames from the official artwork (Pillow required)."""
from pathlib import Path
from PIL import Image
ROOT = Path(__file__).resolve().parents[1]
SIZES = (16, 24, 32, 48, 64, 128, 256)

def main():
    source = Image.open(ROOT / "professor-os-logo.png").convert("RGBA")
    if source.width != source.height:
        raise ValueError("Official artwork must be square; never stretch the logo")
    out = ROOT / "prototype-compose/src/main/resources/icons"
    out.mkdir(parents=True, exist_ok=True)
    for size in SIZES:
        source.resize((size, size), Image.Resampling.LANCZOS).save(out / f"professor-os-{size}.png")
    source.save(out / "professor-os.ico", format="ICO", sizes=[(n,n) for n in SIZES])
    icon = Image.open(out / "professor-os.ico")
    assert icon.ico.sizes() == {(n,n) for n in SIZES}
    for size in SIZES:
        frame = icon.ico.getimage((size,size)).convert("RGBA")
        assert frame.getextrema()[3][0] == 0 and frame.getextrema()[3][1] == 255
    print("Verified seven RGBA icon frames:", SIZES)

if __name__ == "__main__":
    main()
