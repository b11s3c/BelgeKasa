@file:OptIn(ExperimentalMaterial3Api::class)

package com.belgetarayici.ui

import com.belgetarayici.i18n.L
import com.belgetarayici.i18n.tr
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.belgetarayici.crypto.KeyManager
import com.belgetarayici.data.Contact
import com.belgetarayici.imaging.Bitmaps
import com.belgetarayici.io.ExportFormat
import com.belgetarayici.io.ExportOptions
import com.belgetarayici.share.Btz
import com.belgetarayici.share.SharedDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class ShareKind(private val labelTr: String) {
    BTZ("Şifreli paket"), PDF_PW("Parolalı PDF"), PLAIN("Şifresiz");

    val label: String get() = tr(labelTr)
}
private enum class Expiry(private val labelTr: String, val days: Int?) {
    NONE("Süresiz", null), DAY("1 gün", 1), WEEK("7 gün", 7), MONTH("30 gün", 30);

    val label: String get() = tr(labelTr)
}

@Composable
fun ShareScreen(vm: AppViewModel, target: ShareTarget) {
    val context = LocalContext.current
    // Original PDFs travel as files; watermark and format choices apply to scans only.
    val isPdf = target == ShareTarget.Pdf
    var kind by remember { mutableStateOf(ShareKind.BTZ) }
    var usePassword by remember { mutableStateOf(true) }
    var password by remember { mutableStateOf("") }
    var password2 by remember { mutableStateOf("") }
    var contact by remember { mutableStateOf<Contact?>(null) }
    var viewOnly by remember { mutableStateOf(false) }
    var expiry by remember { mutableStateOf(Expiry.NONE) }
    var watermark by remember { mutableStateOf("") }
    var plainFormat by remember { mutableStateOf(ExportFormat.PDF) }
    var scanning by remember { mutableStateOf(false) }
    var contactsVersion by remember { mutableStateOf(0) }
    var newContactKey by remember { mutableStateOf<String?>(null) }
    val contacts by produceState<List<Contact>>(emptyList(), contactsVersion) { value = withContext(Dispatchers.IO) { vm.c.repo.contacts() } }

    if (scanning) {
        QrScannerScreen(onCancel = { scanning = false }) { text ->
            scanning = false
            if (KeyManager.isValidPublicKey(text)) newContactKey = text else vm.toast(tr("Bu QR bir BelgeKasa anahtarı değil"))
        }
        return
    }

    val passwordOk = password.length >= 6 && password == password2
    val ready = when (kind) {
        ShareKind.BTZ -> if (usePassword) passwordOk else contact != null
        ShareKind.PDF_PW -> passwordOk
        ShareKind.PLAIN -> true
    }

    fun launch(file: java.io.File, mime: String) {
        vm.c.lock.expectExternalActivity()
        context.startActivity(vm.c.share.shareIntent(file, mime))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr("Paylaş")) },
                navigationIcon = { IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Geri")) } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ShareKind.entries.forEachIndexed { i, k ->
                    SegmentedButton(kind == k, { kind = k }, SegmentedButtonDefaults.itemShape(i, ShareKind.entries.size)) { Text(k.label) }
                }
            }
            Spacer(Modifier.height(12.dp))
            val info = when (kind) {
                ShareKind.BTZ -> tr("Uçtan uca şifreli .btz paketi. Yalnızca BelgeKasa ile, parola veya alıcının anahtarıyla açılabilir. Belge hiçbir sunucuya gitmez.")
                ShareKind.PDF_PW -> tr("AES-256 ile parolalanmış PDF. Herhangi bir PDF okuyucuyla parola girilerek açılır.")
                ShareKind.PLAIN -> tr("Dikkat: dosya şifrelenmeden paylaşılır. Alıcı uygulama kopyasını saklayabilir.")
            }
            Card(colors = CardDefaults.cardColors(containerColor = if (kind == ShareKind.PLAIN) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer)) {
                Text(info, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(12.dp))

            if (kind == ShareKind.BTZ) {
                Text(tr("Koruma"), fontWeight = FontWeight.Bold)
                RadioRow(tr("Parola ile"), usePassword) { usePassword = true }
                RadioRow(tr("Kişinin anahtarı ile (parolasız)"), !usePassword) { usePassword = false }
            }
            if ((kind == ShareKind.BTZ && usePassword) || kind == ShareKind.PDF_PW) {
                OutlinedTextField(password, { password = it }, label = { Text(tr("Parola (en az 6 karakter)")) }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(password2, { password2 = it }, label = { Text(tr("Parola (tekrar)")) }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
                    isError = password2.isNotEmpty() && password != password2)
                Text(tr("Parolayı alıcıya belgeyi gönderdiğiniz kanaldan farklı bir yoldan iletin."), style = MaterialTheme.typography.bodySmall)
            }
            if (kind == ShareKind.BTZ && !usePassword) {
                if (contacts.isEmpty()) Text(tr("Henüz kişi yok. Alıcının “Paylaşım anahtarım” ekranındaki QR kodunu okutun."), style = MaterialTheme.typography.bodySmall)
                contacts.forEach { c -> RadioRow("${c.name}  ·  ${KeyManager.fingerprint(c.key).take(9)}", contact?.id == c.id) { contact = c } }
                OutlinedButton(onClick = { scanning = true }) { Icon(Icons.Default.QrCodeScanner, null); Text(tr("  QR ile kişi ekle")) }
            }
            if (kind == ShareKind.BTZ) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(viewOnly, { viewOnly = it })
                    Column {
                        Text(tr("Salt görüntüleme"))
                        Text(tr("Alıcı kaydedemez, dışa aktaramaz, ekran görüntüsü alamaz."), style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text(tr("Geçerlilik"), fontWeight = FontWeight.Bold)
                Row { Expiry.entries.forEach { e -> Row(verticalAlignment = Alignment.CenterVertically) { androidx.compose.material3.RadioButton(expiry == e, { expiry = e }); Text(e.label) } } }
            }
            if (kind == ShareKind.PLAIN && !isPdf) {
                Text(tr("Biçim"), fontWeight = FontWeight.Bold)
                ExportFormat.entries.forEach { f -> RadioRow(f.label, plainFormat == f) { plainFormat = f } }
            }
            if (!isPdf && (kind != ShareKind.PLAIN || plainFormat != ExportFormat.TXT)) {
                OutlinedTextField(watermark, { watermark = it }, label = { Text(tr("Filigran (isteğe bağlı)")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(16.dp))
            Button(enabled = ready, modifier = Modifier.fillMaxWidth(), onClick = {
                vm.work(tr("Paylaşım hazırlanıyor")) {
                    val session = vm.pdf
                    val pdfId = if (isPdf) {
                        val ss = session ?: error(tr("PDF kapalı"))
                        if (ss.id == null) vm.savePdf(ss, ss.bytes)
                        ss.id!!
                    } else null
                    val docId = (target as? ShareTarget.Scan)?.docId
                    val (file, mime) = if (pdfId != null) when (kind) {
                        ShareKind.BTZ -> {
                            val header = Btz.Header(System.currentTimeMillis(), viewOnly, expiry.days?.let { System.currentTimeMillis() + it * 86_400_000L })
                            val protection = if (usePassword) Btz.Protection.Password(password.toCharArray()) else Btz.Protection.Recipient(KeyManager.recipient(contact!!.key))
                            vm.c.share.createPdfPackage(pdfId, session?.password, header, protection) to Btz.MIME
                        }
                        ShareKind.PDF_PW -> vm.c.share.createPdfExport(pdfId, session?.password, password) to "application/pdf"
                        ShareKind.PLAIN -> vm.c.share.createPdfExport(pdfId, session?.password, null) to "application/pdf"
                    } else when (kind) {
                        ShareKind.BTZ -> {
                            val header = Btz.Header(System.currentTimeMillis(), viewOnly, expiry.days?.let { System.currentTimeMillis() + it * 86_400_000L })
                            val protection = if (usePassword) Btz.Protection.Password(password.toCharArray()) else Btz.Protection.Recipient(KeyManager.recipient(contact!!.key))
                            vm.c.share.createPackage(docId!!, header, protection, watermark.ifBlank { null }) to Btz.MIME
                        }
                        ShareKind.PDF_PW -> vm.c.share.createExport(docId!!, ExportOptions(ExportFormat.PDF, password = password, watermark = watermark.ifBlank { null })) to "application/pdf"
                        ShareKind.PLAIN -> {
                            val opts = ExportOptions(plainFormat, watermark = watermark.ifBlank { null })
                            val n = vm.c.repo.pages(docId!!).size
                            vm.c.share.createExport(docId, opts) to vm.c.exporter.mimeFor(opts, n)
                        }
                    }
                    withContext(Dispatchers.Main) { launch(file, mime) }
                }
            }) { Icon(Icons.Default.Lock, null); Text(tr("  Paylaş")) }
        }
    }

    newContactKey?.let { key ->
        TextInputDialog(tr("Kişiyi kaydet"), "", tr("Ad"), onDismiss = { newContactKey = null }) { name ->
            newContactKey = null
            vm.work(tr("Kaydediliyor")) {
                vm.c.repo.saveContact(name, key)
                withContext(Dispatchers.Main) { contactsVersion++ }
            }
        }
    }
}

@Composable
fun ReceiveScreen(vm: AppViewModel, uri: Uri) {
    val scope = rememberCoroutineScope()
    var envelope by remember { mutableStateOf<Btz.Envelope?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var doc by remember { mutableStateOf<SharedDocument?>(null) }
    var password by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }

    fun decrypt(pw: CharArray?) {
        working = true
        scope.launch {
            val r = withContext(Dispatchers.Default) { runCatching { vm.c.share.decrypt(uri, pw) } }
            working = false
            r.onSuccess { d ->
                val pdfBytes = d.pdf
                if (pdfBytes != null) {
                    // Shared PDFs open in the PDF viewer; view-only packages stay read-only there.
                    vm.back()
                    vm.openPdfBytes(d.name.ifBlank { tr("Paylaşılan PDF") }, pdfBytes, readOnly = envelope?.header?.viewOnly == true)
                } else {
                    doc = d; error = null
                }
            }
                .onFailure { error = if (envelope?.mode == Btz.Mode.PASSWORD) tr("Parola yanlış veya paket bozuk") else tr("Bu paket size gönderilmemiş ya da bozuk") }
        }
    }

    LaunchedEffect(uri) {
        val env = withContext(Dispatchers.IO) { runCatching { vm.c.share.envelope(uri) } }
        env.onFailure { error = tr("Geçerli bir BelgeKasa paketi değil") }
        env.onSuccess {
            envelope = it
            when {
                it.header.isExpired() -> error = tr("Bu paketin geçerlilik süresi dolmuş")
                it.mode == Btz.Mode.RECIPIENT -> decrypt(null)
            }
        }
    }

    val env = envelope
    val d = doc
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(d?.name ?: tr("Paylaşılan belge")) },
                navigationIcon = { IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Kapat")) } },
                actions = {
                    if (d != null && env?.header?.viewOnly == false) {
                        Button(onClick = {
                            vm.work(tr("Kasaya kaydediliyor")) {
                                val id = vm.c.share.saveToVault(d)
                                withContext(Dispatchers.Main) { vm.changed(); vm.replaceTop(Screen.Document(id)) }
                            }
                        }, modifier = Modifier.padding(end = 8.dp)) { Text(tr("Kasaya kaydet")) }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (env != null) Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip({}, { Text(if (env.mode == Btz.Mode.PASSWORD) tr("Parolalı") else tr("Size özel")) }, leadingIcon = { Icon(Icons.Default.Lock, null) })
                if (env.header.viewOnly) AssistChip({}, { Text(tr("Salt görüntüleme")) }, leadingIcon = { Icon(Icons.Default.Visibility, null) })
                env.header.expiresAt?.let {
                    AssistChip({}, { Text(tr("Son: ") + SimpleDateFormat("d MMM HH:mm", L.locale).format(Date(it))) })
                }
            }
            when {
                d != null -> LazyColumn(Modifier.fillMaxSize().background(Color(0xFF303030)), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(d.pages) { _, p ->
                        val img = remember(p) { runCatching { Bitmaps.decode(p.jpeg, 2000).asImageBitmap() }.getOrNull() }
                        if (img != null) Image(img, null, Modifier.fillMaxWidth().aspectRatio(img.width.toFloat() / img.height), contentScale = ContentScale.Fit)
                    }
                }
                working -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                error != null && (env == null || env.header.isExpired() || env.mode == Btz.Mode.RECIPIENT) ->
                    Text(error!!, Modifier.padding(24.dp), color = MaterialTheme.colorScheme.error)
                env?.mode == Btz.Mode.PASSWORD -> Column(Modifier.padding(24.dp)) {
                    Text(tr("Bu belge parola ile şifrelenmiş."))
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(password, { password = it }, label = { Text(tr("Parola")) }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { decrypt(password.toCharArray()) }, enabled = password.isNotEmpty()) { Text(tr("Aç")) }
                }
            }
            if (d != null) HorizontalDivider()
        }
    }
}
