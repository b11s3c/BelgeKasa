"""Writes a small test PDF without dependencies: 3 pages, outline, AcroForm (text field + checkbox).

Usage: python3 -I tools/mkpdf.py out.pdf
"""
import sys

objs = {}


def obj(n, s):
    objs[n] = s


texts = [
    ["Basvuru Formu", "Bu sayfa form icerir.", "Ad Soyad:", "Onayliyorum:"],
    ["Bolum 2: Kosullar", "Kira bedeli her ayin besinde odenir.", "Depozito iki aylik kiradir."],
    ["Bolum 3: Imza", "Taraflar asagida imzalar."],
]
obj(1, "<< /Type /Catalog /Pages 2 0 R /Outlines 20 0 R /AcroForm << /Fields [30 0 R 31 0 R] "
       "/DA (/Helv 0 Tf 0 g) /DR << /Font << /Helv 9 0 R >> >> >> >>")
obj(2, "<< /Type /Pages /Kids [3 0 R 4 0 R 5 0 R] /Count 3 >>")
for i, n in enumerate([3, 4, 5]):
    annots = " /Annots [30 0 R 31 0 R]" if i == 0 else ""
    obj(n, f"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 9 0 R >> >> "
           f"/Contents {10 + i} 0 R{annots} >>")
    body = "BT /F1 24 Tf 60 770 Td (%s) Tj ET\n" % texts[i][0]
    y = 720
    for line in texts[i][1:]:
        body += "BT /F1 14 Tf 60 %d Td (%s) Tj ET\n" % (y, line)
        y -= 40
    obj(10 + i, f"<< /Length {len(body)} >>\nstream\n{body}endstream")
obj(9, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>")
obj(20, "<< /Type /Outlines /First 21 0 R /Last 23 0 R /Count 3 >>")
obj(21, "<< /Title (Basvuru) /Parent 20 0 R /Next 22 0 R /Dest [3 0 R /Fit] >>")
obj(22, "<< /Title (Kosullar) /Parent 20 0 R /Prev 21 0 R /Next 23 0 R /Dest [4 0 R /Fit] >>")
obj(23, "<< /Title (Imza) /Parent 20 0 R /Prev 22 0 R /Dest [5 0 R /Fit] >>")
obj(30, "<< /Type /Annot /Subtype /Widget /FT /Tx /T (AdSoyad) /TU (Ad Soyad) /Rect [160 676 460 700] "
        "/P 3 0 R /DA (/Helv 12 Tf 0 g) /F 4 /MK << /BC [0 0 0] >> >>")
obj(31, "<< /Type /Annot /Subtype /Widget /FT /Btn /T (Onay) /TU (Onayliyorum) /Rect [190 636 210 656] "
        "/P 3 0 R /V /Off /AS /Off /F 4 /MK << /BC [0 0 0] >> /AP << /N << /Yes 32 0 R /Off 33 0 R >> >> >>")
yes = "0 0 0 rg 4 4 12 12 re f"
obj(32, f"<< /Type /XObject /Subtype /Form /BBox [0 0 20 20] /Length {len(yes)} >>\nstream\n{yes}\nendstream")
obj(33, "<< /Type /XObject /Subtype /Form /BBox [0 0 20 20] /Length 0 >>\nstream\n\nendstream")

out = bytearray(b"%PDF-1.7\n%\xe2\xe3\xcf\xd3\n")
offsets = {}
for n in sorted(objs):
    offsets[n] = len(out)
    out += f"{n} 0 obj\n{objs[n]}\nendobj\n".encode("latin-1")
size = max(objs) + 1
xref = len(out)
out += f"xref\n0 {size}\n0000000000 65535 f \n".encode()
for n in range(1, size):
    out += (f"{offsets[n]:010d} 00000 n \n" if n in offsets else "0000000000 65535 f \n").encode()
out += f"trailer\n<< /Size {size} /Root 1 0 R >>\nstartxref\n{xref}\n%%EOF\n".encode()
open(sys.argv[1], "wb").write(out)
