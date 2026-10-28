# Oturum günlüğü

En yeni kayıt en altta. Her kayıt: istenen → yapılan → sonuç → açık kalan.

## 2026-10-27 · 1 · Skill kurulumu ve ilk sürüm

**İstenen:** Tarayıcı uygulamalarının ücretli özelliklerini ücretsiz sunan, belgeleri şifreli
tutan, uygulama içinde şifreli paylaşan, PDF/DOCX girdi-çıktılı açık kaynak Android
uygulamasını planla ve yap.

**Yapılan:**
- Özellik listesi ve plan → [PLAN.md](../PLAN.md).
- Uygulama (~5.300 satır): kasa, kilit, kamera + OpenCV, filtreler, kimlik/kitap modu, OCR,
  arama, PDF/DOCX/görüntü içe-dışa aktarma, parolalı PDF, `.btz` paylaşımı (parola + HPKE),
  imza, kalem, filigran, sayfa araçları, ayarlar.
- Emülatör testlerinde düzeltilenler: kamera izninde çökme (eski fragment sürümü), kırpma
  köşelerinin geri hareketini tetiklemesi, saydam PNG'nin siyah kaydedilmesi, içe aktarmada
  otomatik OCR olmaması, PDF metin katmanında kelime boşlukları, R8'de Tink uyarısı.

**Sonuç:** Debug ve release derleniyor, 7 birim testi geçiyor, ana akışlar emülatörde doğrulandı.

## 2026-10-27 · 2 · Git ve GitHub

`git init`, ilk commit `a94f64d`; özel repo `b11s3c/BelgeTarayici` açıldı ve pushlandı.

## 2026-10-27 · 3 · Kalan özelliklerin testi

**İstenen:** İmza, kalem, kimlik kartı, kitap modu, sıralama/ayıklama/birleştirme, DOCX + filigran
ve ayarları test et, hataları düzelt.

**Bulunan ve düzeltilen 8 hata** (commit `469595c`): kenardan çizimde kayıp (çıkış onayı +
hareket dışlama), yeniden kırpmada imza/kalem kaybı (K-11), kitap modunda bölmeme (K-12),
küçük sıralama butonları, PIN alanının açık görünmesi ve ana iş parçacığında PBKDF2, hızlı
geçişte kilidin atlanması ve muafiyetin birikmesi (K-10), açılışta kasanın gereksiz açılması,
imza renk seçiminin görünmemesi.

**Açık kalan:** biyometrik test edilmedi (emülatörde sistem koşullarının kabulü gerekiyordu;
o sırada emülatöre yanlışlıkla konan ekran kilidi PIN'i geri kaldırıldı).

## 2026-10-27 · 4 · Hafıza yapısı, PDF okuyucu/düzenleyici, BelgeKasa

**İstenen:** Repoda hafıza yapısı kur ve her şeyi not et; uygulamaya tam bir PDF
okuyucu/düzenleyici ekle; uygulama çok araçlı olsun; adı ve logosu değişsin
(kullanıcı seçimi: BelgeKasa).

**Yapılan:**
- Hafıza yapısı: `CLAUDE.md` + `docs/hafiza/` (bu dosyalar), test yardımcıları `tools/tap.sh`,
  `tools/test-belgeleri.sh`, `tools/mkpdf.py` (formlu/ana hatlı test PDF'si).
- Plan → [PLAN.md](../PLAN.md) Faz 1b.
- Yeni `pdf` paketi: `PdfStore` (kasada şifreli orijinal PDF), `PdfView` (memfd üzerinden
  PdfRenderer), `PdfSession`, `PdfTools` (yorumlar, form, sayfa düzenleme, birleştir, sıkıştır,
  parola, arama, ana hatlar, OCR katmanı, görüntüleme kopyası), `PdfExport` (görüntü/DOCX'ten PDF, PDF'den DOCX).
- Ekranlar: üç sekmeli ana ekran (Taramalar / PDF / Araçlar), PDF kitaplığı, araçlar, PDF
  görüntüleyici (arama, içindekiler, sayfaya git, gece modu, iki parmakla yakınlaştırma),
  yorum ekranı, form doldurma, sayfa düzenleyici, okuma modu + sesli okuma; `.btz` ile PDF paylaşımı.
- Ad **BelgeKasa**, `applicationId` `com.belgekasa`, sürüm 0.2.0, uyarlanabilir + tek renkli ikon.
- `TextLayer` `Exporter`'dan ayrıldı (tarama dışa aktarma ve PDF OCR ortak kullanıyor).

**Testte bulunup düzeltilenler:** form alanı değeri bozuk görünüyordu (alt küme yazı tipi →
K-15); PdfRenderer yorumları çizmiyordu (K-14); yorum ekranı ipucu sistem çubuğunun altında
kalıyordu; düzenleme sonrası okuma modu eski metni gösteriyordu; alınan salt görüntülemeli PDF'den
geri dönünce önceki paylaşım ekranı onu kasaya kaydedebiliyordu (K-16); döndürülen küçük resim
hücreden taşıyordu.

**Not:** Oturum ortasında emülatör kapandı ve verisi silindi; scratchpad de temizlendi. Test
belgeleri artık `tools/test-belgeleri.sh` ile yeniden üretiliyor.

## 2026-10-28 · 5 · Test edilmeyenlerin testi

**İstenen:** Test edilmemiş kalanları test et.

**Yapılan:**
- Emülatörde: taramayı PDF'ye çevir, PDF'yi Word'e çevir (DOCX'te OCR metni + sayfa görüntüsü),
  PDF'de üstünü çiz ve imza, döndürülmüş sayfada arama ve OCR.
- Kamera mantığı test edilebilir birimlere ayrıldı: `AutoCaptureTrigger` (enjekte saat),
  `DocumentDetector.detectLuminance` (satır dolgulu, döndürülmüş kamera karesi), `QrCodes`
  (üretim + çözme). Kameraya yakınlaştırma (1×/2×/3×) eklendi; yalnızca lens destekliyorsa görünür.
- İlk cihaz üstü (instrumented) testler: `app/src/androidTest/.../DeviceTests.kt` (10 test) ve
  JVM `QrCodesTest`.
- Klasör kullanıcı tarafından `BelgeTarayici` → `BelgeKasa` olarak yeniden adlandırıldı.

**Bulunan ve düzeltilen hatalar:** döndürülmüş sayfalarda arama vurguları yanlış yerdeydi;
döndürülmüş sayfaya OCR metin katmanı yanlış yönde/yerde yazılıyordu (K-18); kamera
yakınlaştırması desteklemeyen kamerada etkisiz düğmeler (K-19).

**Test edilemeyen:** emülatörün arka kamerası sabit bir sanal oda gösteriyor (belge yeterince
büyük değil, yakınlaştırma yok, poster görüntüleri görüş alanında değil) → canlı kenar çizgisi,
otomatik çekim ve kamerayla QR okuma uçtan uca gerçek telefonda denenmeli; parçaları cihaz
üstü testlerle doğrulandı. Biyometrik için emülatörde parmak izi kaydını kullanıcının yapması gerekiyor.

## 2026-10-28 · 6 · Fiziksel cihaz testi hazırlığı

**İstenen:** Fiziksel cihazda yapılacak testlerin listesi ve test APK'sı.

**Yapılan:** [../CIHAZ_TEST_LISTESI.md](../CIHAZ_TEST_LISTESI.md) — kullanıcı isteğiyle yalnızca
emülatörde denenemeyen 17 test (kamera, biyometrik, gerçek uygulamalarla dosya alışverişi, iki
telefonla paylaşım, performans) ve hata tablosu. `dist/` altına (git dışı) test APK'ları: release arm64-v8a ve
armeabi-v7a (R8, debug anahtarıyla imzalı — yalnız test için), ekran görüntüsüne izin veren
debug arm64-v8a. İzinler: yalnız CAMERA ve biyometrik; minSdk 28 (Android 9).

**Açık kalan:** Kullanıcının cihaz test sonuçları; yayın için gerçek yükleme anahtarı.

## 2026-10-28 · 7 · Fiziksel cihaz sonuçlarına göre düzeltmeler (0.3.0)

**İstenen:** Kullanıcının doldurduğu cihaz test listesini incele, hataları düzelt.

**Yapılan:** Kenar algılama çok yollu + canlı izleyici, otomatik çekim sabitliği ve görünür
geri bildirim (K-22); gezinme çubuğu boşlukları (kamera, imza/kalem alt çubukları, gözden geçirme);
gözden geçirmede sayfa küçük resimleri; PDF'de yakınlaşınca yatay kaydırma; yorum ekranında iki
parmakla yakınlaştırma ve eklenen metin/not/imzayı sürükleme; Türkçe OCR modeli ve ön işlem (K-23);
sesli okuma dili Otomatik/Türkçe/English; ARM evrensel APK (K-24); sürüm 0.3.0 (3). Tekrar test
listesi R1–R10 [../CIHAZ_TEST_LISTESI.md](../CIHAZ_TEST_LISTESI.md) sonuna eklendi.

**Açık kalan:** Arayüzün İngilizce çevirisi (kullanıcı isteği); cihazda R1–R10.

## 2026-10-28 · 8 · İngilizce arayüz

**İstenen:** Uygulama yalnızca Türkçe olmasın, İngilizcesi de olsun.

**Yapılan:** `i18n` paketi (`L`, `tr`/`trf`, `En`), ~380 metin betikle `tr()`'ye sarıldı, sabit
etiketli enum'lar getter'a çevrildi, çekirdekteki kullanıcıya görünen hata metinleri de çevrildi.
Ayarlar → Uygulama dili (Sistem / Türkçe / English). `EnTranslationsTest` eklendi (K-25).

## 2026-10-28 · 9 · GitHub yayını

**İstenen:** GitHub'ı ayarla, uygulamayı güzelce tanıt (başka uygulamalardan bahsetmeden), açık
kaynak olsun; herkes APK'yı Releases'ten indirebilsin.

**Yapılan:** README (Türkçe + English, ekran görüntüleri, indirme/doğrulama, güvenlik modeli),
CONTRIBUTING, SECURITY, Issue/PR şablonları, CI iş akışı; başka uygulama adları ve analiz notları
depodan çıkarıldı (yerel `yerel/`'de); yayın imza anahtarı; temiz tek commit geçmişi; depo herkese
açık, `main` korumalı; v0.3.0 sürümü APK ile yayımlandı (K-26).
