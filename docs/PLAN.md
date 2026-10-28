# BelgeKasa — Ürün ve Teknik Plan

Açık kaynak, ücretsiz, reklamsız Android belge tarayıcı ve PDF aracı. Tarayıcı
uygulamalarında genellikle ücretli olan özellikleri ücretsiz sunar; belgeleri yalnızca
uygulamanın şifreli kasasında tutar ve uçtan uca şifreli olarak paylaşır.

## İlkeler

1. **Belge telefon hafızasına düşmez.** Taranan/içe aktarılan her sayfa uygulamanın özel
   dizininde (`filesDir/vault`) AES-256-GCM ile şifreli durur. Galeriye, `Downloads`'a ya da
   ortak depolamaya hiçbir şey yazılmaz. Dışa aktarma yalnızca kullanıcı açıkça "Farklı kaydet"
   seçerse yapılır.
2. **İnternet izni yok.** `AndroidManifest.xml` `INTERNET` içermez; OCR dil paketleri APK
   içinde gelir. Reklam, analitik, hesap yok.
3. **Sunucusuz paylaşım.** Paylaşım bir `.btz` paket dosyasıdır; her kanaldan (WhatsApp,
   e-posta, Bluetooth, Nearby Share) gönderilebilir, yalnızca BelgeKasa açabilir.
4. **Bağımsız geliştirme.** Başka uygulamaların kodu, modeli ya da görseli kullanılmaz; yalnızca açık kaynak kütüphaneler.

## Özellikler

| Özellik | BelgeKasa'da |
| --- | --- |
| Otomatik kenar tespiti, perspektif düzeltme | CameraX + OpenCV kontur tespiti, canlı çerçeve, köşe düzenleyici |
| Toplu tarama | Çoklu çekim modu |
| Sihirli renk / gölge temizleme | OpenCV filtreleri: Orijinal, Gri, S/B, Sihirli renk, Gölge temizle |
| Kimlik kartı modu | Ön + arka yüz tek A4 sayfada |
| Kitap modu | Çift sayfayı ortadan ikiye bölme |
| OCR, aranabilir PDF | Tesseract (tur+eng), görünmez metin katmanlı PDF |
| Belgelerde metin arama | OCR metni üzerinde tam metin arama |
| Word'e dönüştürme | DOCX dışa aktarma (görüntü + OCR metni) |
| PDF/Office içe aktarma | PDF (şifreli PDF dahil), DOCX, JPEG/PNG içe aktarma |
| PDF şifreleme | Parolalı PDF (AES-256) dışa aktarma |
| Şifreli paylaşım | `.btz`: parola veya alıcının açık anahtarı (QR) ile; salt görüntüleme + son kullanma tarihi |
| Birleştirme / bölme / sıralama | Belge birleştirme, sayfa çıkarma, sıralama, döndürme, silme |
| PDF sıkıştırma | Dışa aktarmada kalite/çözünürlük seçimi |
| İmza | Elle imza çizme, şifreli saklama, sayfaya yerleştirme |
| Filigran | İsteğe bağlı metin filigranı; uygulama kendi filigranını hiçbir zaman eklemez |
| İşaretleme | Serbest kalem ile işaretleme |
| Biyometrik kilit | PIN + parmak izi kilidi, otomatik kilit, ekran görüntüsü engeli |

## Mimari

Tek modüllü Kotlin + Jetpack Compose uygulaması (`app/`), paketler:

| Paket | Sorumluluk |
| --- | --- |
| `crypto` | `KeyManager` (Tink anahtar seti, Android Keystore ana anahtarı), `VaultCipher` (dosya/blob şifreleme), `ShareCrypto` (PBKDF2 + AES-GCM ve HPKE X25519), `Identity` (cihazın paylaşım anahtar çifti) |
| `data` | `VaultDb` (SQLCipher; belge/sayfa/OCR meta verisi), `DocumentRepository` (şifreli sayfa dosyaları) |
| `imaging` | `DocumentDetector` (OpenCV kenar tespiti), `Perspective`, `Filters`, `PageComposer` (kimlik kartı, kitap, imza, filigran) |
| `ocr` | `OcrEngine` (Tesseract4Android, dil paketleri `assets/tessdata`) |
| `io` | `PdfExporter`/`PdfImporter` (PdfBox-Android + `PdfRenderer`), `DocxExporter`/`DocxImporter` (OOXML), `ImageExporter`, `TextExporter` |
| `share` | `.btz` biçimi: `BtzWriter`, `BtzReader` |
| `security` | `AppLock` (PIN türetme, biyometrik), otomatik kilit |
| `ui` | Compose ekranları: kilit, kitaplık, kamera, kırpma/filtre, belge, sayfa düzenleyici, imza, dışa aktarma, paylaşım, paylaşım anahtarım, ayarlar |

### Şifreleme ayrıntıları

- **Kasa anahtarı**: Tink `AES256_GCM` (blob) ve `AES256_GCM_HKDF_4KB` (akış) anahtar setleri,
  Android Keystore'daki `belgetarayici_master` anahtarıyla sarılıp `SharedPreferences`'ta durur.
  Anahtar cihazdan çıkarılamaz; uygulama verisi kopyalansa da çözülemez.
- **Veritabanı**: SQLCipher; 32 baytlık rastgele parola kasa anahtarıyla şifreli saklanır.
- **Sayfa dosyaları**: `vault/<belgeId>/<sayfaId>.enc` (tam çözünürlük JPEG) ve `.thumb.enc`;
  ilişkili veri (AAD) olarak dosya yolu kullanılır, dosyalar yer değiştirilemez.
- **Geçici dosyalar**: Paylaşım için oluşan dosyalar `cache/outbox` altında yalnızca şifreli
  `.btz` olarak durur ve uygulama açılışında temizlenir. Düz PDF/DOCX yalnızca "Farklı kaydet"
  ile kullanıcının seçtiği konuma doğrudan akıtılır.
- **Uygulama kilidi**: PIN, PBKDF2-HMAC-SHA256 (210.000 tur) ile doğrulanır; biyometrik
  isteğe bağlı. `FLAG_SECURE` ile ekran görüntüsü/son uygulamalar önizlemesi engellenir.
  `allowBackup=false`, veri çıkarma kuralları tüm alanları dışarıda bırakır.

### `.btz` paylaşım biçimi (sürüm 1)

```
"BTZ1" | mod (1 bayt: 1=parola, 2=alıcı anahtarı) | başlık uzunluğu (4 bayt) | başlık JSON
       | şifreli gövde
```

- Parola modu: PBKDF2-HMAC-SHA256 (310.000 tur, 16 bayt tuz) → AES-256-GCM.
- Alıcı modu: Tink HPKE (X25519, HKDF-SHA256, AES-256-GCM); alıcı açık anahtarını
  "Paylaşım anahtarım" ekranındaki QR ile verir, gönderen kamerayla okutur.
- Başlık şifrelenmez ama AAD olarak doğrulanır: sürüm, oluşturma zamanı, `viewOnly`,
  `expiresAt`. Gövde: belge adı, sayfalar (JPEG), OCR metinleri (ZIP).
- `viewOnly` ve `expiresAt` uygulama tarafından uygulanır (alıcı kasaya kaydedemez, dışa
  aktaramaz, ekran görüntüsü alamaz). Kötü niyetli değiştirilmiş bir istemciye karşı kriptografik
  garanti değildir; arayüzde bu açıkça yazılır.

## Fazlar

| Faz | Kapsam | Durum |
| --- | --- | --- |
| 1 — Çekirdek | Kasa, kilit, kamera + kenar tespiti, kırpma, filtreler, toplu tarama, kimlik/kitap modu, OCR, arama, PDF/DOCX/görüntü/TXT içe-dışa aktarma, parolalı PDF, `.btz` paylaşımı (parola + QR anahtar), sayfa araçları, imza, filigran, işaretleme | Bu sürümde |
| 2 — Genişletme | Klasör/etiket, QR kod okuyucu (genel), uzun görüntü birleştirme, akıllı silgi, el yazısı iyileştirme, ek OCR dilleri (dosyadan içe aktarma), XLSX/PPTX dışa aktarma | Sonra |
| 3 — Yayın | F-Droid ve Google Play listesi, imzalama anahtarı, gizlilik politikası, çeviriler, erişilebilirlik denetimi | Sonra |

## Bağımlılıklar (hepsi açık kaynak)

| Kütüphane | Lisans | Kullanım |
| --- | --- | --- |
| Jetpack Compose, CameraX, Biometric | Apache-2.0 | Arayüz, kamera, biyometrik |
| OpenCV 4.12.0 | Apache-2.0 | Kenar tespiti, perspektif, filtreler |
| Tesseract4Android 4.8.0 + tessdata_best (tur), tessdata_fast (eng) | Apache-2.0 | OCR |
| PdfBox-Android 2.0.27.0 | Apache-2.0 | PDF yazma/şifreleme/okuma |
| Google Tink 1.24.0 | Apache-2.0 | Kasa ve paylaşım şifrelemesi |
| SQLCipher for Android 4.x | BSD-3 | Şifreli veritabanı |
| ZXing core 3.5.4 | Apache-2.0 | QR üretme/okuma |

Uygulama lisansı: GPL-3.0-or-later (fork'ların da açık kalması için).

## Test

- Birim testleri (JVM): `.btz` biçimi gidiş-dönüş, parola yanlışlığı, başlık kurcalama,
  DOCX yazıcı/okuyucu gidiş-dönüş, PIN doğrulama.
- Cihaz testi (emülatör): tarama → OCR → arama → PDF dışa aktarma → `.btz` paylaşım → içe aktarma.

## Faz 1b — PDF okuyucu/düzenleyici ve çok araçlı yapı (2026-10-27)

Uygulama **BelgeKasa** adıyla üç sekmeli bir araç merkezine dönüşür: **Taramalar** (mevcut
tarayıcı kitaplığı), **PDF** (şifreli PDF kitaplığı ve okuyucu), **Araçlar** (birleştir,
sıkıştır, koru, dışa aktar, taramayı PDF'ye çevir, paylaşım anahtarı, ayarlar).

- PDF'ler orijinal haliyle (vektör metin, formlar, ek açıklamalar korunur) kasada Tink AEAD ile
  şifreli tutulur; görüntüleme için çözülen baytlar bellek dosyasına (`memfd`, API 30+) ya da
  açılır açılmaz silinen uygulama içi geçici dosyaya (API 28–29) yazılır.
- Yorumlar standart PDF ek açıklamaları olarak kaydedilir, böylece diğer PDF okuyucularda da
  düzenlenebilir kalır; yazılı metin ve imza sayfaya işlenir ("doldur ve imzala").
