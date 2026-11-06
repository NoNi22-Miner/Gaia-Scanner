#!/usr/bin/env python3
"""Creates the small files the device tests read from app/src/androidTest/assets.

Needs: pip install pypdf pillow pillow-heif cryptography
Run:   python3 scripts/make-test-fixtures.py
"""
import io
import pathlib

import pillow_heif
from PIL import Image, ImageDraw
from pypdf import PdfReader, PdfWriter

OUT = pathlib.Path(__file__).resolve().parent.parent / "app/src/androidTest/assets"
PASSWORD = "secret123"  # Also in PdfToImagesTest.


def one_page_pdf() -> bytes:
    img = Image.new("RGB", (595, 842), "white")
    ImageDraw.Draw(img).rectangle([50, 50, 300, 300], fill=(200, 30, 30))
    buf = io.BytesIO()
    img.save(buf, format="PDF", resolution=72.0)
    return buf.getvalue()


def encrypted_pdf(path: pathlib.Path) -> None:
    writer = PdfWriter()
    for page in PdfReader(io.BytesIO(one_page_pdf())).pages:
        writer.add_page(page)
    writer.encrypt(user_password=PASSWORD, owner_password=PASSWORD + "-owner", algorithm="AES-128")
    with path.open("wb") as f:
        writer.write(f)


def heic(path: pathlib.Path) -> None:
    img = Image.new("RGB", (64, 48), (20, 120, 220))
    heif = pillow_heif.from_pillow(img)
    heif.save(str(path), quality=80)


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    encrypted_pdf(OUT / "encrypted.pdf")
    heic(OUT / "sample.heic")
    for p in sorted(OUT.iterdir()):
        print(f"{p.name}: {p.stat().st_size} bytes")


if __name__ == "__main__":
    main()
