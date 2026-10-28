# Güvenlik / Security

## Açık bildirme

Bir güvenlik açığı bulduysanız lütfen **herkese açık Issue açmayın**. GitHub'ın özel bildirim
özelliğini kullanın: depo sayfasında **Security → Report a vulnerability**. Bildirime etkilenen
sürümü, adımları ve mümkünse bir kavram kanıtını ekleyin. Düzeltme yayımlanana kadar ayrıntılar
gizli tutulur.

## Kapsam

- Kasadaki belgelerin uygulama dışından okunabilmesi
- `.btz` paylaşım paketlerinin parola ya da alıcı anahtarı olmadan açılabilmesi, kurcalanmış
  bir paketin fark edilmemesi
- Kilit ekranının atlanabilmesi
- Belgelerin istenmeden ortak depolamaya, galeriye ya da yedeklere düşmesi

Bilinen sınırlar (açık sayılmaz): salt görüntüleme ve süre sınırı alıcının uygulamasınca
uygulanır; PIN yalnızca arayüzü kilitler, kasa anahtarı cihaza bağlıdır. Ayrıntı:
[README](README.md#gizlilik-ve-güvenlik).

## Resmi sürümler

Resmi APK'lar yalnızca bu deponun Releases sayfasında yayımlanır ve şu sertifikayla imzalıdır
(SHA-256):

```
E6:01:76:28:4B:F9:FC:F9:FD:C1:0C:F1:09:A4:B3:0A:7B:76:9E:DE:98:95:AD:37:6C:48:78:D8:21:55:73:A9
```

GPL lisansı gereği başkaları kodu değiştirip kendi sürümlerini dağıtabilir; ama o sürümler bu
anahtarla imzalanamaz. Farklı imzalı bir "BelgeKasa" resmi sürüm değildir.

---

**English:** please report vulnerabilities privately via **Security → Report a vulnerability**,
not as public issues. Official APKs are published only on this repository's Releases page and
are signed with the certificate above; builds signed with any other key are not official.
