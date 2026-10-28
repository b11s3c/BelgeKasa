# Kararlar

Her karar: bağlam → karar → gerekçe. Yeni kararlar sona eklenir; değişen karar silinmez,
"Yerine geçen: K-x" notu düşülür.

## K-1 · Yerel Kotlin + Compose (2026-10-27)
Flutter/React Native yerine yerel Android. OpenCV, Tesseract, PdfBox, Tink, SQLCipher hepsi
Java/Android kütüphanesi; köprü katmanı olmadan kullanılır. Yalnızca Android hedefleniyor.

## K-2 · Bağımsız geliştirme (2026-10-27)
Özellikler kullanıcı ihtiyacından tasarlanır; başka uygulamaların kodu, modeli, görseli
kullanılmaz. Yalnızca lisansı uygun açık kaynak kütüphaneler.

## K-3 · İnternet izni yok (2026-10-27)
Belge uygulamaları genellikle internet + reklam/izleme SDK'sı taşıyor. En güçlü fark gizlilik.
OCR dil verisi APK'da gelir; paylaşım sunucusuz.

## K-4 · Kasa: Tink AEAD + Keystore, SQLCipher (2026-10-27)
Sayfa başına AES-256-GCM, AAD = `page:<id>:<rol>` (dosyalar yer değiştirilemez). Anahtar seti
Keystore'daki `belgetarayici_master` ile sarılı. DB parolası rastgele, kasa anahtarıyla şifreli.

## K-5 · PIN yalnızca arayüz kilidi (2026-10-27)
Kasa anahtarı cihaza bağlı, PIN'den türetilmiyor. PIN unutulursa veri kaybı olmasın; biyometrik
ile anahtar bağlama 2. faza bırakıldı (bkz. bilinen-sorunlar).

## K-6 · `.btz` paylaşım biçimi (2026-10-27)
`BTZ1 | mod | başlık | wrap | ChunkedGcm(gövde)`. Başlık okunur ama AAD olarak doğrulanır.
Parola modu PBKDF2-SHA256 310k; alıcı modu Tink HPKE X25519 (QR ile anahtar). Salt görüntüleme
ve süre sınırı uygulama tarafından uygulanır — kriptografik garanti değil, arayüzde söylenir.

## K-7 · DOCX'i kendimiz yazıp okuyoruz (2026-10-27)
Apache POI Android'de ağır ve sorunlu. OOXML'in ihtiyacımız olan alt kümesi (paragraf, tablo
metni, gömülü görüntü) elle yazıldı; JVM birim testli.

## K-8 · ABI split (2026-10-27)
Evrensel debug APK 228 MB, emülatöre sığmadı. Mimari başına APK; Play için AAB zaten böler.

## K-9 · Debug'da ekran görüntüsüne izin (2026-10-27)
`FLAG_SECURE` test ekran görüntülerini karartıyordu. `BuildConfig.ALLOW_SCREENSHOTS` yalnızca
debug'da true; release'te engel her zaman açık.

## K-10 · Otomatik kilit Activity yaşam döngüsüyle (2026-10-27)
`ProcessLifecycleOwner` durdurmayı ~700 ms geç bildiriyor; hızlı geçişte kilit atlanıyordu.
`MainActivity.onStart/onStop` (+ `isChangingConfigurations`). Uygulamanın kendi açtığı dış
ekranlar için `expectExternalActivity()` muafiyeti 5 sn ile sınırlı.

## K-11 · Düzenlemeler orijinale de işlenir (2026-10-27)
Kalem/imza/döndürme işlenmiş sayfaya çizilir; sayfanın yeniden kırpma için tuttuğu orijinal
fotoğraf da bu sonuçla (tam çerçeve, "original" filtre) değiştirilir. Aksi halde "Kırp/filtre"
düzenlemeleri siliyordu.

## K-12 · Kitap modu her zaman böler (2026-10-27)
Kullanıcı kitap modunu seçtiyse görüntü uzun kenardan ikiye bölünür (dik tutulan telefonda
çift sayfa döndürülmüş gelir).

## K-13 · Ad: BelgeKasa, çok araçlı uygulama (2026-10-27)
Kullanıcı seçimi. Tarayıcı + PDF okuyucu/düzenleyici tek uygulamada, ana ekranda araç merkezi.
Kod paketi (`com.belgetarayici`) değiştirilmedi; görünen ad, logo ve `applicationId` değişti.

## K-14 · Görüntüleme için yorumlar sayfaya işlenmiş kopya (2026-10-28)
Android `PdfRenderer` ek açıklamaları (vurgu, mürekkep, not) çizmiyor; başka okuyucularda eklenen
yorumlar da görünmezdi. Açılışta bellekte bir "görüntüleme kopyası" (`PdfTools.viewCopy`) üretilir: her
ek açıklamanın görünüm akışı sayfaya çizilir. Kasadaki dosya gerçek, düzenlenebilir ek
açıklamaları korur (PDFKit ile doğrulandı).

## K-15 · Form alanları için tam yazı tipi (2026-10-28)
PdfBox AcroForm görünümlerinde alt küme (subset) yazı tipini doğru kodlayamıyor; değer bozuk
görünüyordu. Form doldururken Noto Sans tam gömülür (`TextLayer.font(full = true)`, ~600 KB).

## K-16 · Açık PDF başına tek ekran yığını (2026-10-28)
Yeni PDF açılınca önceki PDF'ye ait ekranlar (görüntüleyici, yorum, form, sayfalar, okuma, PDF
paylaşımı) yığından çıkarılır; aksi halde eski ekran yeni belge üzerinde çalışıyordu. Salt
görüntülemeli oturumlar `savePdf` içinde ayrıca engellenir.

## K-17 · PDF'ler ayrı tabloda, orijinal haliyle (2026-10-28)
Taramalar sayfa görüntüsü olarak, PDF'ler tek dosya olarak saklanır (`pdfs` tablosu, DB v2).
Vektör metin, formlar ve yorumlar kaybolmasın diye PDF'ler rasterleştirilmez. Görüntüleme için
çözülen baytlar API 30+ `memfd`'de, API 28–29'da açılır açılmaz silinen uygulama içi dosyada.

## K-18 · PDF koordinatları her zaman /Rotate üzerinden (2026-10-28)
Arama vurguları glif metin matrisinden (kendi yön vektörüyle) kullanıcı uzayında kurulur ve
`fromUser` ile görüntülenen sayfaya çevrilir. OCR metin katmanı döndürülmüş sayfada her kelime
için `toUser` + dik okunur metin matrisiyle yazılır.

## K-19 · Kamera yakınlaştırması lense göre (2026-10-28)
Yakınlaştırma adımları `zoomState.maxZoomRatio`'dan gelir; 2×'in altındaysa satır gösterilmez.

## K-20 · Cihaz üstü testler `am instrument` ile (2026-10-28)
`connectedDebugAndroidTest` testten sonra uygulamayı kaldırıp kasayı siler. Test APK'sı ayrı
kurulur ve `adb shell am instrument -w com.belgekasa.test/androidx.test.runner.AndroidJUnitRunner`
ile çalıştırılır; uygulama verisi korunur.

## K-21 · Cihaz test APK'ları debug anahtarıyla, dist/ git dışı (2026-10-28)
Test dağıtımı için release derleme (gerçek davranış: R8, ekran görüntüsü engeli) debug
anahtarıyla imzalanır; böylece debug ve test sürümü birbirinin üstüne kurulabilir. Yayın için
ayrı yükleme anahtarı oluşturulacak (kullanıcı kararı). APK'lar `dist/`'te, repoya girmez.

## K-22 · Kenar algılama çok yollu, canlı görüntü izleyiciyle (2026-10-28)
Cihaz testinde algılama "bir var bir yok" idi. `DocumentDetector` artık normal Canny, düşük eşikli
Canny ve Otsu parlaklık ayrımından aday dörtgen toplar; dışbükey zarf + kademeli
`approxPolyDP`, köşe açısı (≈53°–127°) ve doluluk (≥0,75) denetimi; puan = alan × doluluk².
Çerçeve boyutundaki adaylar atılır. `QuadTracker` canlı önizlemede 700 ms kayıp karelere dayanır,
yeni çerçeveyi iki karede onaylar, köşeleri yumuşatır. Otomatik çekim sabitliği her karenin bir
öncekine değil, belgenin oturduğu konuma göre ölçer (%4). Çekilen fotoğrafta kenar bulunamazsa ya da
çok küçük çıkarsa önizlemedeki çerçeve kullanılır.

## K-23 · Türkçe OCR tessdata_best, ön işlemle (2026-10-28)
Serif kitap yazısında `tessdata_fast` harfleri simgeye çeviriyordu. Türkçe modeli `tessdata_best`
(7,4 MB, SHA-256 e0c3338d…) oldu, İngilizce `fast` kaldı. OCR öncesi gri görüntü kâğıt parlaklığına
bölünür (gölge/flaş düzeltme), küçük görüntüler 2000 px'e büyütülür, Tesseract Sauvola eşiklemesi.
`tur+eng` karışımı Türkçe harfleri bozduğu için varsayılan dil `tur`. Kurulu model `version`
damgasıyla yenilenir.

## K-24 · Test APK'sı ARM evrensel (2026-10-28)
Android 14 bir telefon 64-bit APK'yı "uyumlu değil" diye reddetti (32-bit kullanıcı alanı, ör.
Android Go). `-ParmUniversal` ile arm64-v8a + armeabi-v7a tek APK (≈89 MB) üretilir; varsayılan
derleme ABI başına bölünmüş kalır (mağaza için AAB zaten böler).

## K-25 · İngilizce arayüz: Türkçe metin anahtar, `En` tablosu (2026-10-28)
Kullanıcı arayüzün İngilizcesini istedi. ~380 metin `strings.xml`'e taşınmadı: Türkçe metin kodda
anahtar olarak kalır, `tr()` İngilizce etkinse `En.map`'ten karşılığını verir. Böylece metinler
composable olmayan yerlerde de (ViewModel mesajları, hata metinleri) aynı yolla çevrilir ve kod
okunur kalır. `L.lang` Compose durumu olduğundan Ayarlar'da dil değişince ekranlar anında yenilenir.
Ayar: Sistem (telefon Türkçe ise Türkçe, değilse İngilizce) / Türkçe / English. Tarih biçimleri
`L.locale` ile. Kayıtlı belge adları (ör. "Tarama 28 Eki") değişmez. Dönüştürme betikle yapıldı,
yanlış sarmalananlar (XML/MIME/anahtar) elle geri alındı; `EnTranslationsTest` korur.

## K-26 · Herkese açık depo, temiz geçmiş, yayın anahtarı (2026-10-28)
Depo herkese açıldı (GPL-3.0). Kullanıcı isteğiyle GitHub'daki geçmiş tek bir "ilk açık kaynak
sürüm" commit'iyle değiştirildi (force push); eski geçmiş yalnızca yerelde `ozel-gecmis` dalında,
asla gönderilmez. Başka uygulamaların adları depoda geçmez. Resmi APK'lar yeni yayın anahtarıyla
imzalanır: `~/.belgekasa/belgekasa-release.jks` + `signing.properties` (depo dışında, 600 izin).
Sertifika SHA-256 `E6:01:76:28:…:73:A9` README ve SECURITY.md'de yayımlanır. Anahtar kaybolursa
mevcut kullanıcılar güncelleme alamaz; yedeği kullanıcıda. CI (GitHub Actions) yalnızca birim
testleri ve imzasız derleme yapar, anahtar GitHub'a konmaz. Dışarıdan katkı fork + PR ile.
