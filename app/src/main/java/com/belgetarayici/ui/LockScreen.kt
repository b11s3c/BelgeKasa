package com.belgetarayici.ui

import com.belgetarayici.i18n.tr
import com.belgetarayici.i18n.trf
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.belgetarayici.security.AppLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LockScreen(lock: AppLock, activity: FragmentActivity, onUnlocked: () -> Unit) {
    val creating = !lock.hasPin
    var pin by remember { mutableStateOf("") }
    var firstPin by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf(if (creating) tr("Belgelerinizi korumak için 4–8 haneli bir PIN belirleyin") else tr("PIN'inizi girin")) }
    var checking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun biometric() {
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onUnlocked()
        })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(tr("BelgeKasa kilidini aç"))
                .setNegativeButtonText(tr("PIN kullan"))
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK)
                .build(),
        )
    }

    val biometricAvailable = !creating && lock.biometricEnabled &&
        BiometricManager.from(activity).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
    LaunchedEffect(Unit) { if (biometricAvailable) biometric() }

    fun submit() {
        if (pin.length < 4) return
        if (creating) {
            val first = firstPin
            when {
                first == null -> { firstPin = pin; pin = ""; message = tr("PIN'i tekrar girin") }
                first == pin -> {
                    checking = true
                    scope.launch {
                        withContext(Dispatchers.Default) { lock.setPin(pin) }
                        onUnlocked()
                    }
                }
                else -> { firstPin = null; pin = ""; message = tr("PIN'ler eşleşmedi, baştan girin") }
            }
        } else {
            val wait = lock.waitSeconds()
            if (wait > 0) { message = trf("Çok fazla hatalı deneme. %s sn bekleyin", wait); pin = ""; return }
            checking = true
            val attempt = pin
            scope.launch {
                val ok = withContext(Dispatchers.Default) { lock.verify(attempt) }
                checking = false
                if (ok) onUnlocked() else { pin = ""; message = tr("Hatalı PIN") }
            }
        }
    }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.Lock, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text(tr("BelgeKasa"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        Text(if (pin.isEmpty()) " " else "●".repeat(pin.length), fontSize = 28.sp, letterSpacing = 6.sp)
        Spacer(Modifier.height(24.dp))
        val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("bio", "0", "del"))
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { key ->
                    when (key) {
                        "bio" -> IconButton(onClick = { biometric() }, enabled = biometricAvailable, modifier = Modifier.size(72.dp)) {
                            if (biometricAvailable) Icon(Icons.Default.Fingerprint, tr("Biyometrik"), Modifier.size(32.dp))
                        }
                        "del" -> IconButton(onClick = { pin = pin.dropLast(1) }, modifier = Modifier.size(72.dp)) {
                            Icon(Icons.AutoMirrored.Filled.Backspace, tr("Sil"))
                        }
                        else -> FilledTonalButton(
                            onClick = { if (pin.length < 8 && !checking) pin += key },
                            shape = CircleShape,
                            modifier = Modifier.size(72.dp),
                        ) { Text(key, fontSize = 24.sp) }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        Spacer(Modifier.height(8.dp))
        FilledTonalButton(onClick = { submit() }, enabled = pin.length >= 4 && !checking) {
            Text(if (creating) tr("Devam") else tr("Kilidi aç"))
        }
    }
}
