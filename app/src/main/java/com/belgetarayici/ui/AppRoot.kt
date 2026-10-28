package com.belgetarayici.ui

import com.belgetarayici.i18n.tr
import com.belgetarayici.i18n.trf
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity

private val Blue = Color(0xFF1F5FAD)

@Composable
fun BelgeTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = if (dark) {
        darkColorScheme(primary = Color(0xFF9CC2F5), onPrimary = Color(0xFF0A2C57), primaryContainer = Color(0xFF15457F), secondary = Color(0xFFB4C8E6))
    } else {
        lightColorScheme(primary = Blue, onPrimary = Color.White, primaryContainer = Color(0xFFD6E3F8), onPrimaryContainer = Color(0xFF0A2C57), secondary = Color(0xFF4F617E))
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
fun AppRoot(vm: AppViewModel, activity: FragmentActivity) {
    BelgeTheme {
        val locked by vm.c.lock.locked.collectAsState()
        val snackbar = remember { SnackbarHostState() }
        LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize()) {
                if (locked) {
                    LockScreen(vm.c.lock, activity, onUnlocked = { vm.c.lock.unlock(); vm.onUnlocked() })
                } else {
                    BackHandler(enabled = vm.backStack.size > 1) { vm.back() }
                    when (val s = vm.current) {
                        Screen.Library -> HomeScreen(vm)
                        Screen.Settings -> SettingsScreen(vm)
                        Screen.Keys -> KeysScreen(vm)
                        is Screen.Camera -> CameraScreen(vm)
                        Screen.ScanReview -> ReviewScreen(vm)
                        is Screen.Document -> DocumentScreen(vm, s.id)
                        is Screen.Page -> PageScreen(vm, s.pageId)
                        is Screen.Recrop -> RecropScreen(vm, s.pageId)
                        is Screen.Sign -> SignScreen(vm, s.pageId)
                        is Screen.Annotate -> AnnotateScreen(vm, s.pageId)
                        is Screen.Share -> ShareScreen(vm, s.target)
                        Screen.PdfViewer -> PdfViewerScreen(vm)
                        is Screen.PdfAnnotate -> PdfAnnotateScreen(vm, s.page)
                        Screen.PdfForm -> PdfFormScreen(vm)
                        Screen.PdfPages -> PdfPagesScreen(vm)
                        is Screen.PdfReflow -> PdfReflowScreen(vm, s.page)
                        is Screen.Receive -> ReceiveScreen(vm, s.uri)
                    }
                    vm.pdfPasswordRequest?.let {
                        PasswordDialog(
                            title = tr("Parolalı PDF"),
                            message = trf("“%s” parola ile korunuyor.", it.name),
                            onDismiss = { vm.answerPdfPassword(null) },
                            onConfirm = { pw -> vm.answerPdfPassword(pw) },
                        )
                    }
                    vm.importPasswordRequest?.let {
                        PasswordDialog(
                            title = tr("Parolalı PDF"),
                            message = tr("Bu PDF parola ile korunuyor. İçe aktarmak için parolayı girin."),
                            onDismiss = { vm.answerImportPassword(null) },
                            onConfirm = { vm.answerImportPassword(it) },
                        )
                    }
                }
                vm.busy?.let { BusyOverlay(it) }
                SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp)) { Snackbar(it) }
            }
        }
    }
}

@Composable
fun BusyOverlay(label: String) {
    Box(Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.Center) {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 6.dp) {
            Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text(label)
            }
        }
    }
}

@Composable
fun PasswordDialog(title: String, message: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var pw by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(message)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(pw, { pw = it }, label = { Text(tr("Parola")) }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(pw) }, enabled = pw.isNotEmpty()) { Text(tr("Tamam")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Vazgeç")) } },
    )
}

@Composable
fun TextInputDialog(title: String, initial: String, label: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value, { value = it }, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onClick = { onConfirm(value.trim()) }, enabled = value.isNotBlank()) { Text(tr("Tamam")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Vazgeç")) } },
    )
}

@Composable
fun ConfirmDialog(title: String, message: String, confirm: String = tr("Sil"), onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = { onDismiss(); onConfirm() }) { Text(confirm, color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Vazgeç")) } },
    )
}
