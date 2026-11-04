"""Draws the mock receipts the receipt tests use. Every item, store address and merchant
here is invented; the images are marked as mock.

    uv run --no-project --with pillow --with numpy python -I data/make-mock-receipts.py

Writes data/mock-receipt-indomaret.png (a paper till receipt, total Rp 52.300) and
data/mock-receipt-ewallet.png (a payment confirmation screen, total Rp 46.500).

Then draws three long receipts and photographs each one twice, as a hand would:

- `<receipt>-overlap`: three parts, each sharing a few rows with the next
- `<receipt>-gap`: two parts with a stretch of items missing between them

Every part is warped with a perspective tilt, a paper curl, a shadow across the paper and
JPEG noise, and saved as data/receipt-long/<set>_p<n>.jpg. The truth for each set goes
to app/src/test/resources/receipt/long/items.tsv (the items the joined photos should
give) and sets.tsv
(parts, where a part is missing, the total and the receipt discounts). The same seed
always draws the same images.
"""
import random
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

HERE = Path(__file__).resolve().parent
FONT = "/usr/share/fonts/jetbrains-mono-nl-fonts/JetBrainsMonoNL-Medium.ttf"
LONG = HERE / "receipt-long"
TRUTH = HERE.parent / "app/src/test/resources/receipt/long"
SEED = 20261031
WIDTH = 39


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
    image = render(rows, size, width, paper)
    image.save(out)


def render(rows, size, width, paper):
    font = ImageFont.truetype(FONT, size)
    pitch = int(size * 1.6)
    image = Image.new("RGB", (width, pitch * (len(rows) + 2)), paper)
    draw = ImageDraw.Draw(image)
    for i, (text, align) in enumerate(rows, start=1):
        w = draw.textlength(text, font=font)
        x = (width - w) / 2 if align == "center" else 24
        draw.text((x, i * pitch), text, fill=(20, 20, 20), font=font)
    return image


# ---- Long receipts -------------------------------------------------------------------

def rp(amount):
    return f"{amount:,}".replace(",", ".")


def line(label, value):
    return f"{label:<{WIDTH - len(value)}}{value}"


class Receipt:
    """Rows to print, and for each item the rows it spans and its truth."""

    def __init__(self, name):
        self.name = name
        self.rows = []
        self.items = []  # (first_row, last_row, name, qty, unit, price, discount)
        self.total = 0
        self.receipt_discount = 0

    def text(self, text, align="left"):
        self.rows.append((text, align))

    def one_row(self, name, qty, unit, discount=0, label="DISKON"):
        first = len(self.rows)
        self.text(f"{name:<17}{qty:>3}{rp(unit):>9}{rp(qty * unit):>10}")
        if discount:
            self.text(line(f"  {label} :", f"({rp(discount)})"))
        self.items.append((first, len(self.rows) - 1, name, qty, unit, qty * unit, discount))

    def two_row(self, name, qty, unit, discount=0):
        first = len(self.rows)
        self.text(name)
        self.text(line(f"   {qty} x {rp(unit)}", rp(qty * unit)))
        if discount:
            self.text(line("   HEMAT", f"-{rp(discount)}"))
        self.items.append((first, len(self.rows) - 1, name, qty, unit, qty * unit, discount))


def indomaret_long():
    r = Receipt("indomaret")
    for text in ["INDOMARET", "PT. CONTOH RETAIL MAKMUR", "JL. CONTOH SELATAN NO. 17", "NPWP 00.000.000.0-000.000"]:
        r.text(text, "center")
    r.text("29.10.26-19:41  2C11-01-0042  KSR01")
    r.text("-" * WIDTH)
    r.one_row("INDOMIE GRG SPCL", 5, 3_100)
    r.one_row("AQUA BTL 1500ML", 2, 6_200)
    r.one_row("TEH BOTOL 450ML", 1, 4_500)
    r.one_row("TEH BOTOL 450ML", 1, 4_500)
    r.one_row("ROTI TAWAR GANDUM", 1, 18_900)
    r.one_row("TELUR AYAM 10", 1, 23_700)
    r.one_row("GULA PASIR 1KG", 1, 17_500)
    r.one_row("KECAP MANIS 520ML", 1, 24_900, discount=3_000)
    r.one_row("SAOS SAMBAL 340ML", 1, 12_400)
    r.one_row("KOPI SACHET 10S", 2, 13_900)
    r.one_row("BISKUIT KELAPA", 3, 8_200)
    r.one_row("MIE KERING 200G", 2, 5_600)
    r.one_row("SUSU KENTAL MNS", 1, 11_300)
    r.one_row("SABUN MANDI CAIR", 1, 27_800)
    r.one_row("SHAMPO 170ML", 1, 25_400, discount=2_500)
    r.one_row("PASTA GIGI 190G", 2, 14_200)
    r.one_row("TISU WAJAH 250S", 1, 16_900)
    r.one_row("PEMBERSIH LANTAI", 1, 19_800)
    r.one_row("SPONS CUCI 3S", 1, 9_900)
    r.one_row("KANTONG PLASTIK", 1, 500)
    r.text("-" * WIDTH)
    net = sum(price - discount for *_, price, discount in r.items)
    r.total = net
    r.text(line("TOTAL ITEM :", str(sum(item[3] for item in r.items))))
    r.text(line("HARGA JUAL :", rp(net)))
    r.text(line("TOTAL :", rp(net)))
    r.text(line("TUNAI :", rp(400_000)))
    r.text(line("KEMBALI :", rp(400_000 - net)))
    r.text(line("PPN :", rp(net * 11 // 111)))
    r.text("")
    r.text("TERIMA KASIH", "center")
    r.text("MOCK RECEIPT - NOT VALID", "center")
    return r


def superindo_long():
    r = Receipt("superindo")
    for text in ["SUPERINDO", "CABANG CONTOH BARAT", "JL. CONTOH UTARA NO. 8"]:
        r.text(text, "center")
    r.text("NO: 0331-02-118  28/10/2026 10:05")
    r.text("=" * WIDTH)
    r.two_row("MINYAK GORENG 2L", 2, 38_900)
    r.two_row("BERAS PULEN 5KG", 1, 69_500)
    r.two_row("DAGING AYAM FILLET", 1, 34_750)
    r.two_row("BAWANG MERAH 250G", 2, 9_900)
    r.two_row("TOMAT MERAH 500G", 1, 7_450)
    r.two_row("WORTEL LOKAL 500G", 1, 6_900)
    r.two_row("TAHU PUTIH 10S", 2, 5_500, discount=1_000)
    r.two_row("TEMPE PAPAN", 3, 6_000)
    r.two_row("APEL FUJI 1KG", 1, 42_900)
    r.two_row("PISANG CAVENDISH", 1, 24_800)
    r.two_row("YOGURT STRAWBERRY", 4, 7_900)
    r.two_row("KEJU CHEDDAR 165G", 1, 21_300)
    r.two_row("SOSIS AYAM 500G", 1, 38_400, discount=4_000)
    r.two_row("MENTEGA 200G", 1, 15_600)
    r.text("=" * WIDTH)
    net = sum(price - discount for *_, price, discount in r.items)
    r.total = net
    r.text(line("SUBTOTAL", rp(net)))
    r.text(line("TOTAL BELANJA", rp(net)))
    r.text(line("DEBIT BCA", rp(net)))
    r.text(line("DPP", rp(net * 100 // 111)))
    r.text(line("PPN", rp(net - net * 100 // 111)))
    r.text("")
    r.text("TERIMA KASIH ATAS KUNJUNGAN ANDA", "center")
    r.text("MOCK RECEIPT - NOT VALID", "center")
    return r


def card_discount():
    """The alignment page example (31 Oct 2026): six items, a card discount, 206.100."""
    r = Receipt("diskon")
    for text in ["INDOMARET", "PT. CONTOH RETAIL MAKMUR", "JL. CONTOH TIMUR NO. 3", "NPWP 00.000.000.0-000.000"]:
        r.text(text, "center")
    r.text("30.10.26-18:20  2C11-01-0077  KSR02")
    r.text("-" * WIDTH)
    r.one_row("INDOMIE GRG SPCL", 5, 3_100)
    r.one_row("MINYAK GORENG 2L", 1, 38_900)
    r.one_row("BERAS PREMIUM 5KG", 1, 72_500)
    r.one_row("SUSU UHT 1L", 2, 18_200, discount=4_000)
    r.one_row("SABUN CUCI 800ML", 1, 21_900)
    r.one_row("DETERJEN 1.8KG", 1, 44_900)
    r.text("-" * WIDTH)
    r.receipt_discount = 20_000
    r.total = 206_100
    r.text(line("TOTAL ITEM :", "11"))
    r.text(line("HARGA JUAL :", rp(226_100)))
    r.text(line("DISKON KARTU :", f"({rp(20_000)})"))
    r.text(line("TOTAL :", rp(206_100)))
    r.text(line("KARTU DEBIT :", rp(206_100)))
    r.text(line("PPN :", rp(206_100 * 11 // 111)))
    r.text("")
    r.text("TERIMA KASIH", "center")
    r.text("MOCK RECEIPT - NOT VALID", "center")
    return r


def overlap_parts(r):
    """Three parts, cut between items, each sharing two items with the next."""
    starts = [first for first, *_ in r.items]
    third = len(r.items) // 3
    a_end = r.items[third + 1][1] + 1
    b_start = starts[third]
    b_end = r.items[2 * third + 1][1] + 1
    c_start = starts[2 * third]
    return [(0, a_end), (b_start, b_end), (c_start, len(r.rows))], []


def gap_parts(r, missing):
    """Two parts with the named items missing between them."""
    names = [item[2] for item in r.items]
    first = names.index(missing[0])
    last = names.index(missing[-1])
    end_a = r.items[first][0]
    start_b = r.items[last][1] + 1
    return [(0, end_a), (start_b, len(r.rows))], [1]


def truth(r, parts):
    """The items the joined parts should give: whole items only, same name and unit merged."""
    seen = set()
    for start, end in parts:
        seen.update(range(start, end))
    merged = []
    for first, last, name, qty, unit, price, discount in r.items:
        if not all(i in seen for i in range(first, last + 1)):
            continue
        same = next((m for m in merged if m[0] == name and m[2] == unit and m[4] == 0 and discount == 0), None)
        if same:
            same[1] += qty
            same[3] += price
        else:
            merged.append([name, qty, unit, price, discount])
    return merged


def photo(paper_image, rng, nprng):
    """Warps a flat paper crop into a hand-held photo: curl, tilt, shadow, noise, JPEG."""
    src = np.asarray(paper_image, dtype=np.float32)
    h, w = src.shape[:2]

    # Curl: the paper bows, so rows sag towards the edges and the edges are foreshortened.
    bow = rng.uniform(6, 14)
    ys, xs = np.mgrid[0:h, 0:w].astype(np.float32)
    u = xs / (w - 1) * 2 - 1
    sy = ys - bow * (u ** 2) + bow / 2
    sx = (np.arcsin(u * 0.9) / np.arcsin(0.9) + 1) / 2 * (w - 1)
    sy = np.clip(sy, 0, h - 1).astype(np.int32)
    sx = np.clip(sx, 0, w - 1).astype(np.int32)
    curled = src[sy, sx]
    out_of_paper = (ys - bow * (u ** 2) + bow / 2 < 0) | (ys - bow * (u ** 2) + bow / 2 > h - 1)
    curled[out_of_paper] = (60, 52, 46)
    del src, ys, xs, u, sy, sx

    # Place on a dark table with a margin, then tilt in perspective.
    margin = 70
    canvas = Image.new("RGB", (w + 2 * margin, h + 2 * margin), (58, 50, 44))
    canvas.paste(Image.fromarray(curled.astype(np.uint8)), (margin, margin))
    del curled
    cw, ch = canvas.size
    jitter = lambda: (rng.uniform(-28, 28), rng.uniform(-22, 22))  # noqa: E731
    narrow = rng.uniform(20, 45)
    corners = [(0, 0), (cw, 0), (cw, ch), (0, ch)]
    moved = [
        (0 + narrow + jitter()[0], 0 + jitter()[1]),
        (cw - narrow + jitter()[0], 0 + jitter()[1]),
        (cw + jitter()[0] / 2, ch + jitter()[1] / 2),
        (0 + jitter()[0] / 2, ch + jitter()[1] / 2),
    ]
    coeffs = perspective_coeffs(moved, corners)
    tilted = canvas.transform(canvas.size, Image.PERSPECTIVE, coeffs, Image.BICUBIC, fillcolor=(58, 50, 44))
    tilted = tilted.rotate(rng.uniform(-1.6, 1.6), Image.BICUBIC, expand=False, fillcolor=(58, 50, 44))
    del canvas

    # Shadow: a soft dark band from one side, as a hand or phone casts it.
    arr = np.asarray(tilted, dtype=np.float32)
    th, tw = arr.shape[:2]
    gx = np.linspace(0, 1, tw, dtype=np.float32)[None, :]
    gy = np.linspace(0, 1, th, dtype=np.float32)[:, None]
    angle = rng.uniform(0, 2 * np.pi)
    ramp = np.cos(angle) * gx + np.sin(angle) * gy
    ramp = (ramp - ramp.min()) / (ramp.max() - ramp.min())
    shade = 1 - rng.uniform(0.25, 0.4) * np.clip((ramp - 0.45) * 2.5, 0, 1)
    arr *= shade[..., None]
    arr *= rng.uniform(0.85, 0.97)
    arr += nprng.normal(0, 6, arr.shape[:2])[..., None]
    image = Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8))
    del arr, ramp, shade
    image = image.filter(ImageFilter.GaussianBlur(rng.uniform(0.5, 0.9)))
    scale = 900 / image.width
    return image.resize((900, int(image.height * scale)), Image.LANCZOS)


def perspective_coeffs(src, dst):
    """Coefficients for PIL's PERSPECTIVE transform that map dst corners to src corners."""
    matrix = []
    for (x, y), (u, v) in zip(dst, src):
        matrix.append([x, y, 1, 0, 0, 0, -u * x, -u * y])
        matrix.append([0, 0, 0, x, y, 1, -v * x, -v * y])
    a = np.array(matrix, dtype=np.float64)
    b = np.array([c for point in src for c in point], dtype=np.float64)
    return np.linalg.solve(a, b).tolist()


def long_receipts():
    LONG.mkdir(exist_ok=True)
    rng = random.Random(SEED)
    nprng = np.random.default_rng(SEED)
    size, width = 26, 640
    pitch = int(size * 1.6)
    items_tsv = ["set\tname\tqty\tprice\tdiscount"]
    sets_tsv = ["set\tparts\tgaps\ttotal\treceipt_discount"]
    plans = [
        (indomaret_long(), ["KOPI SACHET 10S", "SUSU KENTAL MNS"]),
        (superindo_long(), ["APEL FUJI 1KG", "YOGURT STRAWBERRY"]),
        (card_discount(), ["DETERJEN 1.8KG"]),
    ]
    for receipt, missing in plans:
        flat = render(receipt.rows, size, width, (247, 245, 238))
        for kind, (parts, gaps) in [("overlap", overlap_parts(receipt)), ("gap", gap_parts(receipt, missing))]:
            name = f"{receipt.name}-{kind}"
            for n, (start, end) in enumerate(parts, start=1):
                top = max(0, (start + 1) * pitch - pitch // 3)
                bottom = min(flat.height, (end + 1) * pitch - pitch // 4)
                photo(flat.crop((0, top, width, bottom)), rng, nprng).save(
                    LONG / f"{name}_p{n}.jpg", quality=72, optimize=True,
                )
            for item in truth(receipt, parts):
                items_tsv.append(f"{name}\t{item[0]}\t{item[1]}\t{item[3]}\t{item[4]}")
            gap_text = ",".join(map(str, gaps)) or "-"
            sets_tsv.append(f"{name}\t{len(parts)}\t{gap_text}\t{receipt.total}\t{receipt.receipt_discount}")
        del flat
    TRUTH.mkdir(parents=True, exist_ok=True)
    (TRUTH / "items.tsv").write_text("\n".join(items_tsv) + "\n")
    (TRUTH / "sets.tsv").write_text("\n".join(sets_tsv) + "\n")


if __name__ == "__main__":
    paper()
    screen()
    long_receipts()
