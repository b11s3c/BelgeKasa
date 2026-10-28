@file:OptIn(ExperimentalMaterial3Api::class)

package com.belgetarayici.ui

import androidx.compose.ui.platform.LocalConfiguration
import com.belgetarayici.i18n.L
import com.belgetarayici.i18n.tr
import com.belgetarayici.i18n.trf
import androidx.biometric.BiometricManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.belgetarayici.imaging.Filter
import kotlin.system.exitProcess

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val s = vm.c.settings
    val lock = vm.c.lock
    var uiLang by remember { mutableStateOf(s.uiLanguage) }
    val systemLocale = LocalConfiguration.current.locales[0]
    var langs by remember { mutableStateOf(s.ocrLanguages) }
    var speech by remember { mutableStateOf(s.speechLanguage) }
    var autoOcr by remember { mutableStateOf(s.autoOcr) }
    var filter by remember { mutableStateOf(Filter.of(s.defaultFilter)) }
    var biometric by remember { mutableStateOf(lock.biometricEnabled) }
    var autoLock by remember { mutableStateOf(lock.autoLockSeconds) }
    var changePin by remember { mutableStateOf(false) }
    var wipe by remember { mutableStateOf(false) }
    val canBio = BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr("Ayarlar")) },
                navigationIcon = { IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Geri")) } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            Section(tr("Uygulama dili"))
            Row(Modifier.padding(horizontal = 16.dp).horizontalScroll(rememberScrollState())) {
                listOf("system" to tr("Sistem"), "tr" to "Türkçe", "en" to "English").forEach { (v, label) ->
                    FilterChip(uiLang == v, { uiLang = v; s.uiLanguage = v; L.apply(v, systemLocale) }, label = { Text(label) }, modifier = Modifier.padding(end = 6.dp))
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Section(tr("Metin tanıma (OCR)"))
            Row(Modifier.padding(horizontal = 16.dp).horizontalScroll(rememberScrollState())) {
                listOf("tur" to tr("Türkçe"), "tur+eng" to tr("Türkçe + İngilizce"), "eng" to tr("İngilizce")).forEach { (v, label) ->
                    FilterChip(langs == v, { langs = v; s.ocrLanguages = v }, label = { Text(label) }, modifier = Modifier.padding(end = 6.dp))
                }
            }
            ListItem(
                headlineContent = { Text(tr("Taramadan sonra metni otomatik tanı")) },
                trailingContent = { Switch(autoOcr, { autoOcr = it; s.autoOcr = it }) },
            )
            Section(tr("Sesli okuma"))
            Row(Modifier.padding(horizontal = 16.dp).horizontalScroll(rememberScrollState())) {
                SPEECH_LANGS.forEach { (v, key) ->
                    val label = tr(key)
                    FilterChip(speech == v, { speech = v; s.speechLanguage = v }, label = { Text(label) }, modifier = Modifier.padding(end = 6.dp))
                }
            }
            Text(tr("Otomatik: metin Türkçe harf içeriyorsa Türkçe, İngilizce ise İngilizce ses."), Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Section(tr("Tarama"))
            Text(tr("Varsayılan filtre"), Modifier.padding(horizontal = 16.dp))
            Row(Modifier.padding(horizontal = 16.dp).horizontalScroll(rememberScrollState())) {
                Filter.entries.forEach { f -> FilterChip(filter == f, { filter = f; s.defaultFilter = f.id }, label = { Text(f.label) }, modifier = Modifier.padding(end = 6.dp)) }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Section(tr("Güvenlik"))
            ListItem(
                headlineContent = { Text(tr("Parmak izi / yüz ile aç")) },
                supportingContent = { if (!canBio) Text(tr("Bu cihazda kayıtlı biyometrik yok")) },
                trailingContent = { Switch(biometric, { biometric = it; lock.biometricEnabled = it }, enabled = canBio) },
            )
            Text(tr("Uygulamadan çıkınca otomatik kilit"), Modifier.padding(horizontal = 16.dp))
            Row(Modifier.padding(horizontal = 16.dp)) {
                listOf(0 to tr("Hemen"), 30 to tr("30 sn"), 120 to tr("2 dk"), 600 to tr("10 dk")).forEach { (v, label) ->
                    FilterChip(autoLock == v, { autoLock = v; lock.autoLockSeconds = v }, label = { Text(label) }, modifier = Modifier.padding(end = 6.dp))
                }
            }
            ListItem(headlineContent = { Text(tr("PIN'i değiştir")) }, modifier = Modifier.clickable { changePin = true })
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Section(tr("Gizlilik"))
            Text(
                tr("• Belgeler yalnızca uygulamanın özel alanında AES-256-GCM ile şifreli tutulur; anahtar Android Keystore'dadır.\n") +
                    tr("• Uygulamanın internet izni yoktur; hiçbir veri dışarı gönderilmez.\n") +
                    tr("• Ekran görüntüsü ve son uygulamalar önizlemesi engellenir.\n") +
                    tr("• Yedeklemeye (bulut/cihaz aktarımı) hiçbir veri dahil edilmez."),
                Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall,
            )
            ListItem(
                headlineContent = { Text(tr("Tüm verileri sil"), color = MaterialTheme.colorScheme.error) },
                supportingContent = { Text(tr("Bütün belgeler, imzalar, kişiler ve anahtarlar geri dönüşsüz silinir.")) },
                modifier = Modifier.clickable { wipe = true },
            )
            Section(tr("Hakkında"))
            Text(trf("BelgeKasa %s — açık kaynak (GPL-3.0): belge tarayıcı + PDF okuyucu/düzenleyici. OpenCV, Tesseract, PdfBox-Android, Google Tink, SQLCipher ve ZXing kullanır.", com.belgetarayici.BuildConfig.VERSION_NAME),
                Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
        }
    }

    if (changePin) {
        var old by remember { mutableStateOf("") }
        var new by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        var checking by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        AlertDialog(
            onDismissRequest = { changePin = false },
            title = { Text(tr("PIN'i değiştir")) },
            text = {
                Column {
                    val pinField = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                    OutlinedTextField(old, { old = it.filter(Char::isDigit).take(8) }, label = { Text(tr("Mevcut PIN")) }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), keyboardOptions = pinField)
                    OutlinedTextField(new, { new = it.filter(Char::isDigit).take(8) }, label = { Text(tr("Yeni PIN (4–8 hane)")) }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), keyboardOptions = pinField)
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(enabled = new.length >= 4 && !checking, onClick = {
                    checking = true
                    // PBKDF2 with 210k rounds: keep it off the main thread.
                    scope.launch {
                        val ok = withContext(Dispatchers.Default) { lock.verify(old).also { if (it) lock.setPin(new) } }
                        checking = false
                        if (ok) { changePin = false; vm.toast(tr("PIN değiştirildi")) }
                        else error = lock.waitSeconds().let { if (it > 0) trf("Çok fazla hatalı deneme. %s sn bekleyin", it) else tr("Mevcut PIN hatalı") }
                    }
                }) { Text(tr("Kaydet")) }
            },
            dismissButton = { TextButton(onClick = { changePin = false }) { Text(tr("Vazgeç")) } },
        )
    }
    if (wipe) {
        var confirm by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { wipe = false },
            title = { Text(tr("Tüm verileri sil")) },
            text = {
                Column {
                    Text(tr("Bu işlem geri alınamaz. Onaylamak için SİL yazın."))
                    OutlinedTextField(confirm, { confirm = it }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(enabled = confirm.trim().uppercase(java.util.Locale.forLanguageTag("tr")) == tr("SİL"), onClick = {
                    vm.c.wipeEverything()
                    exitProcess(0)
                }) { Text(tr("Kalıcı olarak sil"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { wipe = false }) { Text(tr("Vazgeç")) } },
        )
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth().padding(16.dp, 12.dp, 16.dp, 4.dp))
}
