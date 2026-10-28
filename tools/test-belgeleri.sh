#!/bin/zsh
# Creates the emulator test documents (macOS tools only) and pushes them to /sdcard/Download.
# Usage: tools/test-belgeleri.sh [output-dir]
set -e
out=${1:-$(mktemp -d)}
here=${0:A:h}
cat > $out/kira.txt <<'TXT'
KİRA SÖZLEŞMESİ

Kiraya veren: Ayşe Yılmaz
Kiracı: Mehmet Öztürk
Adres: Çağlayan Mahallesi, Şişli / İstanbul

Aylık kira bedeli 25.000 TL olup her ayın beşinde ödenecektir.
Depozito iki aylık kira tutarıdır. Sözleşme süresi bir yıldır.
TXT
textutil -convert docx -output $out/kira.docx $out/kira.txt
cupsfilter -i text/plain -m application/pdf $out/kira.txt > $out/kira.pdf 2>/dev/null
sips -s format png -Z 2000 $out/kira.pdf --out $out/kira.png >/dev/null
python3 -I $here/mkpdf.py $out/basvuru.pdf
adb=~/Library/Android/sdk/platform-tools/adb
for f in kira.docx kira.pdf kira.png basvuru.pdf; do $adb push $out/$f /sdcard/Download/ >/dev/null; done
echo "pushed from $out"
