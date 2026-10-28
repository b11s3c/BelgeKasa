# Katkıda bulunma / Contributing

BelgeKasa'ya katkı vermek istediğiniz için teşekkürler! *(English below.)*

## Nasıl çalışır?

Bu depoya yalnızca bakımcı doğrudan yazabilir. Siz değişikliğinizi **kendi kopyanızda** yaparsınız:

1. Sağ üstteki **Fork** düğmesiyle depoyu kendi hesabınıza kopyalayın.
2. Kopyanızda bir dal açın: `git checkout -b duzeltme/kamera-flas`
3. Değişikliğinizi yapın, testleri çalıştırın: `./gradlew :app:testDebugUnitTest`
4. Kendi kopyanıza gönderin ve buraya bir **Pull Request** açın.
5. Otomatik testler (CI) çalışır, bakımcı kodu inceler. Uygun görülürse ana koda eklenir;
   gerekirse değişiklik istenir.

Büyük bir özellik düşünüyorsanız önce bir **Issue** açıp konuşalım; böylece emeğiniz boşa gitmez.

## Kurallar

- **İnternet izni eklenmez.** Uygulama ağa bağlanmayan bir uygulama olarak kalır; ağ gerektiren
  kütüphane, analitik, reklam ya da çökme raporlayıcı kabul edilmez.
- Belgeler yalnızca şifreli kasada durur; ortak depolamaya yalnızca kullanıcı "Farklı kaydet"
  derse yazılır.
- Yeni bağımlılık eklemeden önce lisansının GPL-3.0 ile uyumlu olduğundan emin olun.
- Ekranda görünen her metin `tr("Türkçe metin")` / `trf("… %s …", x)` ile yazılır ve İngilizcesi
  [`app/src/main/java/com/belgetarayici/i18n/En.kt`](app/src/main/java/com/belgetarayici/i18n/En.kt)
  dosyasına eklenir. `EnTranslationsTest` eksik çeviriyi yakalar.
- Kod stili: çevredeki kod gibi (Kotlin, Jetpack Compose); kısa, amaca odaklı commit'ler.
- Başka uygulamaların kodunu, görselini ya da modellerini kopyalamayın.

## Hata bildirimi

[Issues](../../issues) altında "Hata bildirimi" şablonunu kullanın: telefon modeli, Android sürümü,
BelgeKasa sürümü ve adım adım nasıl oluştuğu. **Kişisel belgelerinizin ekran görüntüsünü
eklemeyin.** Güvenlik açıkları için Issue açmayın, [SECURITY.md](SECURITY.md)'ye bakın.

## Çeviri

Yeni bir dil eklemek için `i18n/En.kt` örneğini izleyen bir tablo ve `L` içinde dil seçimi
eklenmesi gerekir; önce bir Issue açın.

---

## English

Only the maintainer can push to this repository. To contribute: **fork** it, create a branch in
your fork, make your change, run `./gradlew :app:testDebugUnitTest`, push to your fork and open a
**pull request** here. CI runs automatically and the maintainer reviews every change before it
is merged. For larger features, please open an issue first.

Ground rules: no internet permission or network libraries (no analytics, ads or crash
reporters); documents stay in the encrypted vault; dependencies must be GPL-3.0 compatible;
every on-screen text goes through `tr()`/`trf()` with an English entry in `i18n/En.kt`; do not
copy code or assets from other apps. Report security issues privately as described in
[SECURITY.md](SECURITY.md).
