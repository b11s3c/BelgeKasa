# Bilinen sorunlar ve sıradaki işler

## Test edilmemiş
- Fiziksel cihaz tekrar testleri R1–R10: [../CIHAZ_TEST_LISTESI.md](../CIHAZ_TEST_LISTESI.md) (0.3.0).
- Paylaşım (15, 16) ikinci telefonda hiç denenemedi (kurulum sorunu, R10).

## Bilinen kısıtlar
- PDF yorumları eklenebilir ama mevcut yorumlar uygulama içinde seçilip silinemez/düzenlenemez.
- PDF düzenlemeleri dosyanın tamamını belleğe alır (150 MB sınırı).
- Görüntüleme kopyası (K-14) yorumlu büyük PDF'lerde açılışı yavaşlatabilir.
- Kod paketi hâlâ `com.belgetarayici`; görünen ad ve `applicationId` BelgeKasa.
- PIN kasa anahtarını korumaz, yalnızca arayüzü kilitler (K-5).
- Salt görüntüleme / süre sınırı istemci tarafında uygulanır (K-6).
- Alınan `.btz` ve içe aktarılan dosyalar belleğe tamamen yüklenir; çok büyük dosyada bellek yetmeyebilir.
- Arayüz Türkçe + İngilizce (K-25); üçüncü dil için `En` gibi bir tablo ve `L` seçimi eklenmeli.
- Kayıtlı belge adları ve mevcut belge içerikleri dil değişince çevrilmez.
- Kaydedilmiş PDF metinleri/imzaları sonradan taşınamaz; yalnız Kaydet'ten önce sürüklenir.
- Karanlık tema ve erişilebilirlik denetlenmedi.
- Emülatör verisi 2026-10-28'de kendiliğinden silindi; yeniden kurulum + `tools/test-belgeleri.sh` gerekir.

## Sıradaki işler
- Yayın anahtarının yedeği kullanıcıda olmalı (K-26).
- PDF: mevcut yorumları seçip silme/düzenleme, imza alanı (sertifikalı imza), çoklu sekme.
- Biyometrik + Keystore ile kasa anahtarını kullanıcı doğrulamasına bağlama.
- Klasör/etiket, genel QR okuyucu, uzun görüntü birleştirme, akıllı silgi, ek OCR dilleri,
  XLSX/PPTX dışa aktarma, sertifikalı e-imza (PAdES).
- Yayın: yükleme anahtarı + AAB, gizlilik politikası, mağaza metni,
  GitHub Actions CI (birim + cihaz üstü testler).
