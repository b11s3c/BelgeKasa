# BelgeKasa (eski adı BelgeTarayıcı) — proje hafızası

Açık kaynak, şifreli, çok araçlı Android uygulaması: belge tarayıcı + PDF okuyucu/düzenleyici.
Kotlin + Jetpack Compose, tek modül (`app/`). Kod paketi tarihsel nedenle `com.belgetarayici`.

## Her oturumda

1. Başlarken oku: [docs/hafiza/README.md](docs/hafiza/README.md) (dizin), sonra
   [oturum günlüğü](docs/hafiza/oturum-gunlugu.md) ve [bilinen sorunlar](docs/hafiza/bilinen-sorunlar.md).
2. Bitirirken güncelle: oturum günlüğüne yeni kayıt, alınan kararlar `kararlar.md`'ye,
   test edilenler `test-gunlugu.md`'ye, açık kalanlar `bilinen-sorunlar.md`'ye.
   Hafıza dosyaları kodla aynı commit'e girer.

## Değişmez ilkeler

- Belgeler yalnızca uygulamanın şifreli kasasında (`filesDir/vault`, Tink AES-256-GCM,
  anahtar Android Keystore'da; meta veri SQLCipher). Ortak depolamaya yalnızca kullanıcı
  "Farklı kaydet" derse yazılır.
- **INTERNET izni yok.** Ağ gerektiren özellik eklenmez.
- Depo herkese açık: başka (ticari) uygulamaların adları depoda, commit mesajlarında, sürüm
  notlarında geçmez; kodları/görselleri kullanılmaz. Rakip analizi notları yalnızca bu
  bilgisayarda, git dışı `yerel/` klasöründe (varsa oku, asla commit'leme).
- Arayüz Türkçe + İngilizce (K-25): ekranda görünen her metin `tr("Türkçe metin")` /
  `trf("… %s …", x)` ile yazılır ve İngilizcesi `i18n/En.kt`'ye eklenir. Sabit etiketli enum'lar
  ham Türkçeyi `labelTr`'de tutar, `label` getter'ı `tr()` çağırır. `EnTranslationsTest` eksik
  çeviriyi ve `tr()`'siz Türkçe metni yakalar. Çoğul İngilizce "page(s)" yazılır.

## Komutlar

```bash
./gradlew :app:testDebugUnitTest     # JVM birim testleri
./gradlew :app:assembleDebug         # app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
./gradlew :app:assembleRelease       # R8 release; ~/.belgekasa/signing.properties varsa imzalı (K-26)
./gradlew -ParmUniversal :app:assembleRelease   # cihaz testi için tek ARM APK (K-24)
./gradlew :app:assembleDebugAndroidTest   # cihaz üstü testler; çalıştırma: docs/hafiza/test-gunlugu.md (K-20)
```

Emülatör: AVD `Pixel_9` (arm64, 16 KB sayfa). Paket `com.belgekasa`, etkinlik
`com.belgetarayici.MainActivity`. Debug derlemede ekran görüntüsüne izin var
(`BuildConfig.ALLOW_SCREENSHOTS`). UI testi: [tools/tap.sh](tools/tap.sh) (metne dokunur),
test belgeleri: [tools/test-belgeleri.sh](tools/test-belgeleri.sh).
Ortam ayrıntıları: [docs/hafiza/ortam.md](docs/hafiza/ortam.md).

## Kod haritası

| Paket | İçerik |
| --- | --- |
| `crypto` | `ChunkedGcm` (akış AES-GCM), `PasswordKdf`, `KeyManager` (Tink + Keystore, HPKE kimlik) |
| `data` | `VaultDb` (SQLCipher), `DocumentRepository` (şifreli sayfa dosyaları), `TextNorm` |
| `imaging` | `DocumentDetector` (OpenCV, çok yollu), `QuadTracker`, `AutoCaptureTrigger`, `Filters`, `PageComposer`, `Bitmaps` |
| `ocr` | `OcrEngine` (Tesseract tur+eng) |
| `io` | `Exporter`, `Importer`, `Docx`, `TextLayer`, `QrCodes` |
| `pdf` | PDF okuyucu/düzenleyici |
| `share` | `.btz` biçimi (`Btz`, `SharedDocument`, `ShareService`) |
| `security` | `AppLock` |
| `i18n` | `L` (etkin dil, Compose durumu), `tr`/`trf`, `En` (İngilizce metinler) |
| `ui` | Compose ekranları, `AppViewModel` (gezinme yığını) |
