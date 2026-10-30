"""Draws the two mock receipts the receipt-scan tests use. Every item, store address and
merchant here is invented; the images are marked as mock.

    uv run --no-project --with pillow python data/make-mock-receipts.py

Writes data/mock-receipt-indomaret.png (a paper till receipt, total Rp 52.300) and
data/mock-receipt-ewallet.png (a payment confirmation screen, total Rp 46.500).
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

HERE = Path(__file__).resolve().parent
FONT = "/usr/share/fonts/jetbrains-mono-nl-fonts/JetBrainsMonoNL-Medium.ttf"


def paper():
    rows = [
        ("INDOMARET", "center"),
        ("JL. CONTOH RAYA NO. 1", "center"),
        ("30/10/2026 08:12  KASIR 01", "center"),
        ("-" * 32, "left"),
        ("INDOMIE GRG SPCL  2  3.100  6.200", "left"),
        ("AQUA BTL 600ML    1  3.500  3.500", "left"),
        ("ROTI TAWAR        1 18.900 18.900", "left"),
        ("TELUR AYAM 10     1 23.700 23.700", "left"),
        ("-" * 32, "left"),
        ("TOTAL                     52.300", "left"),
        ("TUNAI                    100.000", "left"),
        ("KEMBALI                   47.700", "left"),
        ("", "left"),
        ("TERIMA KASIH", "center"),
        ("MOCK RECEIPT - NOT VALID", "center"),
    ]
    draw_rows(rows, HERE / "mock-receipt-indomaret.png", size=26, width=640, paper=(250, 248, 240))


def screen():
    rows = [
        ("Transaksi berhasil!", "center"),
        ("30 Okt 2026 • 13:33", "center"),
        ("", "left"),
        ("Pembayaran ke", "left"),
        ("Warung Kopi Contoh", "left"),
        ("", "left"),
        ("Total Bayar        Rp46.500", "left"),
        ("Metode Pembayaran     Saldo", "left"),
        ("", "left"),
        ("MOCK SCREEN - NOT VALID", "center"),
    ]
    draw_rows(rows, HERE / "mock-receipt-ewallet.png", size=30, width=720, paper=(255, 255, 255))


def draw_rows(rows, out, size, width, paper):
    font = ImageFont.truetype(FONT, size)
    pitch = int(size * 1.6)
    image = Image.new("RGB", (width, pitch * (len(rows) + 2)), paper)
    draw = ImageDraw.Draw(image)
    for i, (text, align) in enumerate(rows, start=1):
        w = draw.textlength(text, font=font)
        x = (width - w) / 2 if align == "center" else 24
        draw.text((x, i * pitch), text, fill=(20, 20, 20), font=font)
    image.save(out)


if __name__ == "__main__":
    paper()
    screen()
