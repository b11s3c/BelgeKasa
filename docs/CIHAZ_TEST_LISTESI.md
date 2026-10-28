# BelgeKasa 0.2.0 — Fiziksel cihaz test listesi

Yalnızca emülatörde denenemeyen ya da gerçek cihazda sonucu farklı olabilecek testler. Geri
kalan özellikler emülatörde test edildi ([hafiza/test-gunlugu.md](hafiza/test-gunlugu.md)).

**Kurulum:** `dist/BelgeKasa-0.2.0-test-arm64-v8a.apk` (yüklenmezse `armeabi-v7a`; hata
ekran görüntüsü gerekirse `debug` sürümü). Paylaşım testleri (15–16) için ikinci bir telefonda
da kurulu olmalı.

**Gerekenler:** basılı A4 Türkçe metin, kimlik kartı boyutunda bir kart, açık bir kitap,
doldurulabilir bir PDF formu (ör. e-Devlet), 100+ sayfalık bir PDF.

Sonuç: ✅ geçti · ❌ hata · ⚠️ sorunlu. ❌/⚠️ ise Not'a ne olduğunu yazın.

| # | Test | Nasıl | Beklenen | Sonuç | Not |
| --- | --- | --- | --- | --- | --- |
| 1 | Parmak izi / yüz | Ayarlar → "Parmak izi / yüz ile aç"; uygulamayı kapatıp aç | Biyometrik istem gelir, PIN'siz açılır |✅| |
| 2 | Ekran görüntüsü engeli | Belge açıkken ekran görüntüsü al; son uygulamalar ekranına bak | Görüntü engellenir, önizleme boş |✅| |
| 3 | Canlı kenar algılama | Tara → A4'ü masaya koy, üstünden tut; farklı zemin ve açılarla dene | Kâğıdın çevresinde mavi çerçeve |❌| Sayfayı algılamıyor ve otomatik kırpmıyor. Yani bazen yapıyor bazen yapmıyor |
| 4 | Otomatik çekim | Belgeyi sabit tut, sonra sayfayı değiştir | ~1,5 sn'de kendiliğinden çeker; aynı sayfayı tekrar çekmez, yeni sayfayı çeker |❌|Otomatik çekim bazen çalışıyor bazen çalışmıyor. Çekildiği belli olmuyor. Fark etmek gerekiyor. Bir de telefonun navigazson çubuğu ekranın üstünde duruyor alt kısımlardaki butonlara basılmıyor. |
| 5 | Tarama kalitesi | 3–4 sayfa çek; gölgeli/loş ortamda "Gölge temizle" ve "S/B" filtreleri | Sayfa düzleşir, küçük yazılar okunaklı, gölge azalır |⚠️| Filtre çalışyıor ama çok sayfalı taramada diğer sayfalara geçip ayrı ayrı düzenleme yapamıyorum. Bir de otomatik kırpma yaparken fotoğrafın tamamını alabiliyor.|
| 6 | Flaş ve yakınlaştırma | Flaş düğmesi; 2×/3× düğmeleri (görünüyorsa) | Fener yanar; görüntü yakınlaşır |⚠️| Flaş yakınca sayfayı algıladı ama çok hassas. Yani bir algıladı bir algılamadı yapıyor.|
| 7 | Kimlik kartı ve kitap modu | Gerçek kartın ön/arka yüzü; açık kitabı tek kareyle çek | Kart tek A4 sayfada; kitap 2 ayrı sayfa |✅| |
| 8 | Türkçe OCR | Taranan sayfa → Metin; 10 sayfalık belgenin süresine bak | ç ğ ı İ ö ş ü doğru; süreyi not edin |⚠️| Türkçe karakterlerin bazılarını okudu. Times new roman bir kitapta bazı harfleri sembollere çevirdi. |
| 9 | Başka uygulamadan PDF açma | WhatsApp / Gmail / Dosyalar'da PDF → "BelgeKasa ile aç" | Açılır; "Kasaya kaydet" çalışır |✅| |
| 10 | Büyük PDF | 100+ sayfalık PDF'yi aç, hızlı kaydır, yakınlaştır | Akıcı, çökme yok |⚠️| Yakınlaştırınca sayfanın sağına soluna hareket etmiyor. |
| 11 | Gerçek form | e-Devlet formu → Doldur → Kaydet → Farklı kaydet, başka bir PDF uygulamasında aç | Türkçe karakterli değerler her iki uygulamada doğru |⚠️ | Forma metin ekleyeceğim eklediğim metinin yerini değiştiremiyorum. Taşıma olması lazım ve belgede yakınlaştıramıyorum.|
| 12 | Yorumlar başka uygulamada | Vurgu, not, kalem ekle → Farklı kaydet → başka bir PDF uygulamasında (ör. Google Drive) aç | Yorumlar orada da görünür |✅| |
| 13 | Dışa aktarılan dosyalar | Taramayı PDF ve DOCX olarak kaydet; başka bir PDF uygulamasında ve Word'de aç | PDF'de metin aranır/seçilir; DOCX açılır |✅| |
| 14 | Sesli okuma | PDF → Okuma → hoparlör simgesi | Türkçe sesle okur, Durdur çalışır |⚠️| Ayarlardan ingilizce seçeneği de olsa güzel olur. Uygulama sadece türkçe için olmasın ingilizce için de olsun.|
| 15 | QR ile kişi ekleme (2 telefon) | Tel.2: Araçlar → Paylaşım anahtarım. Tel.1: Paylaş → QR ile kişi ekle → Tel.2'nin ekranını okut | QR okunur, parmak izi kodu iki telefonda aynı |❌| Android 14 olan bir telefona telefonunuz ile uyumlu değil hatası verdi ve uygulama yüklenmedi. |
| 16 | Şifreli paylaşım (2 telefon) | Tel.1'den .btz'yi WhatsApp ile gönder: a) parolalı b) kişiye c) salt görüntüleme. Tel.2'de dosyaya dokun | a) parola sorar b) parolasız açılır c) kaydet/paylaş yok |❌| Android 14 olan bir telefona telefonunuz ile uyumlu değil hatası verdi ve uygulama yüklenmedi. |
| 17 | Kararlılık | Belge açıkken başka uygulamalara geç, 5 dk sonra dön; 15 dk yoğun tarama | Kilit ekranı gelir, çökme yok, aşırı ısınma yok |✅| |

## Bulunan hatalar

| Test # | Ne oldu | Nasıl tekrarlanır |
| --- | --- | --- |
| | | |

## 0.3.0 düzeltmeleri — tekrar test edilecekler

APK: `dist/BelgeKasa-0.3.0-test-arm.apk` (64 ve 32-bit ARM telefonların hepsi için tek dosya).
Eski sürümün üstüne kurulabilir; veriler korunur.

| # | Eski test | Ne değişti | Tekrar dene | Sonuç | Not |
| --- | --- | --- | --- | --- | --- |
| R1 | 3, 6 | Kenar algılama üç yöntemle (normal / düşük kontrast / parlak kâğıt) çalışıyor; çerçeve kısa kesintilerde kaybolmuyor, titremiyor | Masada, beyaz zeminde, flaşla A4 çerçevesi | | |
| R2 | 4 | Otomatik çekim el titremesinde baştan başlamıyor; yeşil çerçeve + deklanşörde dolan halka geri sayımı gösteriyor; çekimde ses, titreşim, beyaz flaş ve "Sayfa N çekildi" | 3 sayfa otomatik çek | | |
| R3 | 4 | Alt düğmeler gezinme çubuğunun üstünde; "Bitti" ilk çekimde deklanşörü kaydırmıyor | 3 düğmeli gezinmede kamera ve gözden geçirme ekranları | | |
| R4 | 5 | Gözden geçirmede tüm sayfalar küçük resim; dokunulan sayfa ayrı kırpılıp filtrelenir | 3 sayfaya farklı filtre ver, kaydet | | |
| R5 | 5 | Çekilen fotoğrafta kenar bulunamazsa önizlemede görünen çerçeve kullanılıyor (tüm fotoğraf yerine) | Algılandı yazarken çek → kırpma çerçevesine bak | | |
| R6 | 8 | Türkçe için daha doğru OCR modeli + gölge/ışık düzeltme; varsayılan dil yalnızca Türkçe | Times New Roman kitap sayfası | | |
| R7 | 10, 11 | PDF yakınlaştırılınca tek parmakla sağa sola kaydırma; Yorum ekranında iki parmakla yakınlaştırma | 100+ sayfalık PDF ve form | | |
| R8 | 11 | Yorum ekranında eklenen metin, not ve imza sürüklenerek taşınır (Kaydet'ten önce) | Forma metin ekle, taşı, kaydet | | |
| R9 | 14 | Sesli okuma dili: Otomatik / Türkçe / English (Okuma modunda üstteki düğme ve Ayarlar → Sesli okuma) | İngilizce PDF'yi Otomatik ve English ile oku | | |
| R10 | 15, 16 | Android 14 telefonda "uyumlu değil": telefon 32-bit olduğu için 64-bit APK reddedildi; yeni APK ikisini birden içeriyor | İkinci telefona kur, 15 ve 16'yı yap | | |
| R11 | 14 | Arayüz İngilizce de olabilir: Ayarlar → Uygulama dili (Sistem / Türkçe / English) | English seç, ekranları gez, Türkçe'ye dön | | |
