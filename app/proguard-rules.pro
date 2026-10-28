# Tesseract (JNI)
-keep class com.googlecode.tesseract.android.** { *; }
# OpenCV (JNI)
-keep class org.opencv.** { *; }
# SQLCipher (JNI)
-keep class net.zetetic.database.** { *; }
# PdfBox-Android uses reflection for fonts/filters
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
-dontwarn com.gemalto.jp2.**
-dontwarn org.bouncycastle.**
# Tink
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
# Tink optionally uses platform HPKE (newer Android only) when present.
-dontwarn android.crypto.hpke.**
