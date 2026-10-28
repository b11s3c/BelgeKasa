# Test günlüğü

## Nasıl test ediliyor

- **Birim (JVM):** `./gradlew :app:testDebugUnitTest` — `CoreFormatsTest` (parçalı GCM sınırları
  ve kesilme, `.btz` parola/yanlış parola/başlık kurcalama, DOCX gidiş-dönüş, Türkçe arama).
- **Emülatör:** debug APK kurulur, `adb shell input` + [tools/tap.sh](../../tools/tap.sh) ile gezilir,
  `adb exec-out screencap` ile ekran alınır. Ekran kenarından (x < ~70 px) başlayan kaydırmalar
  sistem geri hareketini tetikler — testte kenardan uzak durun.
- **Dışa aktarılan dosyalar:** `adb pull` + macOS PDFKit (swift betiği) ile şifre ve metin
  katmanı; `textutil` ile DOCX.
- **Cihaz üstü (instrumented):** `./gradlew :app:assembleDebugAndroidTest`, iki APK'yı kur,
  `adb shell am instrument -w com.belgekasa.test/androidx.test.runner.AndroidJUnitRunner` (K-20).
  `DeviceTests`: eğik belge tespiti, dolgulu+döndürülmüş kamera karesinde tespit, perspektif
  düzeltme, otomatik çekim zamanlaması, QR çözme, siyah-beyaz filtre, Türkçe OCR, döndürülmüş
  sayfada arama vurgusu ve OCR katmanı, yorumların görüntüleme kopyasına işlenmesi.
- **Kamera sahnesi:** `adb emu virtualscene-image wall|table <png>` poster değiştirir ama posterler
  varsayılan bakış açısında görünmüyor; arka kamerada yakınlaştırma yok.
- **Şifreleme:** `run-as com.belgetarayici` ile kasa dosyalarının ilk baytları (JPEG/SQLite
  imzası olmamalı).

## 2026-10-27 · İlk sürüm

| Alan | Sonuç |
| --- | --- |
| PIN kurma/açma, kitaplık | Geçti |
| Kamera, çekim, köşe sürükleme, perspektif, "Sihirli renk" | Geçti |
| Kasa dosyaları şifreli (Tink başlığı; DB'de SQLite imzası yok) | Geçti |
| PDF / DOCX / PNG içe aktarma, PDF metin katmanını koruma | Geçti (PNG saydamlık hatası düzeltildi) |
| OCR (Tesseract tur+eng) | Geçti; küçük hatalar ("Caglayan") |
| Aksan duyarsız arama ("sisli" → "Şişli") | Geçti |
| `.btz` parola: yanlış reddedildi, doğru açtı, salt görüntülemede "Kasaya kaydet" yok | Geçti |
| `.btz` alıcı anahtarı (QR yerine metinle eklenen kişi) | Geçti |
| Parolalı PDF (PDFKit: AESV3 V5 R6, doğru/yanlış parola, Türkçe metin katmanı) | Geçti |
| Release (R8) derleme: kilit, `.btz`, parolalı PDF | Geçti |
| 16 KB hizalama (`zipalign -c -P 16`) | Geçti |

## 2026-10-27 · Kalan özellikler

| Alan | Sonuç |
| --- | --- |
| Kalem, fosforlu | Geçti (kenar hareketi hatası düzeltildi) |
| İmza çiz/kırp/yerleştir | Geçti |
| Kimlik kartı (ön+arka tek A4) | Geçti |
| Kitap modu | Hata → düzeltildi, 2 sayfaya bölüyor |
| Birleştir, sırala, ayıkla | Geçti (ok butonları büyütüldü) |
| DOCX dışa aktarma + filigran | Geçti |
| Otomatik kilit "Hemen", PIN değiştir, tüm verileri sil | Geçti (PIN alanı maskelendi) |
| Yeniden kırpma sonrası imza/kalem korunuyor | Geçti |
| Biyometrik açma | **Test edilmedi** (emülatörde parmak izi kaydı Pixel Imprint koşullarını kabul gerektiriyor) |
| QR'ı kamerayla okutma, otomatik çekim, canlı kenar çizgisi | **Test edilmedi** (emülatör sahnesi belge değil) |

## 2026-10-28 · PDF okuyucu/düzenleyici ve BelgeKasa

Test belgeleri: `tools/test-belgeleri.sh` (kira.pdf/docx/png, formlu ve ana hatlı basvuru.pdf).

| Alan | Sonuç |
| --- | --- |
| Yeni ad/ikon (başlatıcıda), üç sekmeli ana ekran | Geçti |
| PDF ekle (kasaya), görüntüleyici, sayfa göstergesi | Geçti |
| Form doldurma (metin + onay kutusu) | Hata → düzeltildi (K-15) |
| Yorum: vurgula, altını çiz, kalem, not, metin | Kayıt doğru (PDFKit'te 4 ek açıklama + görünüm); ekranda görünmüyordu → düzeltildi (K-14) |
| Arama ("kira" → 2. sayfa, büyük/küçük harf duyarsız), içindekiler (3 giriş) | Geçti |
| Okuma modu, sesli okuma (Google TTS ses çaldı) | Geçti |
| Sayfa döndür + sırala (yorumlar sayfayla taşındı) | Geçti |
| PDF'yi salt görüntülemeli `.btz` ile paylaş → aç | Geçti; geri dönüş güvenlik hatası düzeltildi (K-16) |
| Birleştir (3+1 → 4 sayfa), görüntülerden PDF, Word'den PDF | Geçti |
| Görüntü PDF'sinde OCR → "sisli" araması "Şişli"yi buldu | Geçti |
| Sıkıştır (86 kB → 50 kB), parola ekle → açarken sor → doğru parolayla aç → parolayı kaldır | Geçti |
| Başka uygulamadan PDF açma (VIEW), "Kasaya kaydet" | Geçti |
| Release (R8): aç, vurgula, kaydet, ara | Geçti |
| Taramayı PDF'ye çevir, PDF'yi Word'e çevir, üstünü çiz, PDF'ye imza, döndürülmüş sayfada OCR | **Test edilmedi** |

## 2026-10-28 · Kalanların testi

| Alan | Sonuç |
| --- | --- |
| Taramayı PDF'ye çevir | Geçti |
| PDF'yi Word'e çevir (OCR metni + görüntü, `textutil` ile okundu) | Geçti |
| PDF'de üstünü çiz, imza ekle | Geçti |
| Döndürülmüş sayfada arama | Hata → düzeltildi (K-18), 5 eşleşme doğru yerde |
| Döndürülmüş sayfada OCR + arama | Hata → düzeltildi (K-18) |
| Cihaz üstü testler (10) + JVM testleri (9) | Geçti |
| Kamera yakınlaştırma | Emülatör arka kamerasında desteklenmiyor → satır gizleniyor (K-19) |
| Canlı kenar çizgisi, otomatik çekim, kamerayla QR | **Uçtan uca test edilemedi** (emülatör sahnesi); parçaları cihaz üstü testlerle doğrulandı |
| Biyometrik | **Test edilmedi** (emülatörde parmak izi kaydı kullanıcıda) |

## 2026-10-28 · Fiziksel cihaz sonuçları (0.2.0) ve düzeltmeler (0.3.0)

Kullanıcının sonuçları: [../CIHAZ_TEST_LISTESI.md](../CIHAZ_TEST_LISTESI.md). Geçen: biyometrik,
ekran görüntüsü engeli, kimlik/kitap modu, başka uygulamadan PDF, yorumların başka okuyucuda
görünmesi, PDF/DOCX dışa aktarma, kararlılık. Hatalı/sorunlu: kenar algılama ve otomatik çekim
(kararsız, çekim belli olmuyor), gezinme çubuğu alt düğmeleri örtüyor, çok sayfada sayfalar arası
geçiş yok, kırpma bazen tüm fotoğraf, serif kitapta OCR, yakınlaştırınca yatay kaydırma yok,
eklenen metin taşınamıyor, İngilizce ses isteği, Android 14 telefona kurulum.

| 0.3.0 kontrolü (emülatör) | Sonuç |
| --- | --- |
| Cihaz üstü testler (16; yeni: düşük kontrast, gölge, boş masa, köşe sırası, izleyici, serif OCR) | Geçti |
| JVM testleri | Geçti |
| 3 düğmeli gezinmede kamera ve gözden geçirme düğmeleri | Geçti (gezinme çubuğunun üstünde) |
| Çekim geri bildirimi ("Çekiliyor…", "Sayfa 1 çekildi") | Geçti |
| Gözden geçirmede küçük resimle 3. sayfaya geçiş | Geçti |
| Yorum ekranında eklenen metni sürükleyerek taşıma | Geçti |
| Release (R8, ARM evrensel APK): tara → kaydet | Geçti |
| İki parmakla yakınlaştırma, canlı algılama, otomatik çekim, İngilizce ses | **Emülatörde denenemez** (adb çoklu dokunma yok, sahne sabit) — cihazda R1–R9 |

## 2026-10-28 · İngilizce arayüz

| Alan | Sonuç |
| --- | --- |
| `EnTranslationsTest` (eksik çeviri, `%s` sayısı, `tr()`'siz Türkçe metin) | Geçti |
| Emülatör (İngilizce telefon, ayar "Sistem") açılışta İngilizce: kilit, Araçlar, Ayarlar, kamera, PDF görüntüleyici | Geçti |
| Ayarlar'da Türkçe ↔ Sistem geçişi anında, yeniden başlatmadan | Geçti |
