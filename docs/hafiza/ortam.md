# Geliştirme ortamı

| Bileşen | Değer |
| --- | --- |
| Makine | macOS (arm64) |
| JDK | Homebrew OpenJDK 17 (`JAVA_HOME=/opt/homebrew/opt/openjdk@17`) |
| Android SDK | `~/Library/Android/sdk`, platform 36 / 36.1, build-tools 37.0.0 ve 36.1.0; `cmdline-tools` yok |
| Gradle | 9.5.0 (sarmalayıcı); AGP 9.3.1 Gradle ≥ 9.5 ister |
| Kotlin | 2.2.21 (Compose derleyici eklentisi) |
| Emülatör | AVD `Pixel_9`, Android 37 Play Store imajı, arm64, 16 KB sayfa boyutu; `/data` küçük (~640 MB boş) |
| Yerel klasör | `BelgeKasa` (eski adı `BelgeTarayici`) |
| GitHub | `b11s3c/BelgeKasa` (herkese açık; eski adı BelgeTarayici), `gh` ile oturum açık |

## Test girdileri (yerel, repoda değil)

- Emülatör `/sdcard/Download/kira.{pdf,docx,png}` — Türkçe metinli test belgeleri
  (macOS `textutil` ve `cupsfilter` ile üretildi).

## Notlar

- APK büyük olduğundan ABI split açık (arm64-v8a, armeabi-v7a, x86_64); emülatöre
  `app-arm64-v8a-debug.apk` kurulur.
- Yayın imzası: `~/.belgekasa/signing.properties` varsa `assembleRelease` otomatik imzalar (K-26).
- Eski yöntem (yalnız test): release APK'yı debug anahtarıyla imzalamak: `zipalign -p 4` + `apksigner sign --ks ~/.android/debug.keystore`.
- Cihaz testi APK'sı (her ARM telefon): `./gradlew -ParmUniversal :app:assembleRelease`, sonra imzala → `dist/BelgeKasa-<sürüm>-test-arm.apk` (K-24).
- 3 düğmeli gezinmeyi denemek: `adb shell cmd overlay enable com.android.internal.systemui.navbar.threebutton` (geri: `...navbar.gestural`).
