package com.belgetarayici.security

import android.content.Context
import android.util.Base64
import com.belgetarayici.crypto.PasswordKdf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** PIN/biometric gate for the UI, with attempt throttling and auto-lock on background. */
class AppLock(context: Context) {
    private val prefs = context.getSharedPreferences("belgetarayici_lock", Context.MODE_PRIVATE)

    private val _locked = MutableStateFlow(true)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private var backgroundedAt = 0L

    // Grace for activities the app opens itself (file picker, share sheet, permission dialog).
    private var externalLaunchAt = 0L
    private var inExternalActivity = false

    val hasPin get() = prefs.contains("pin_hash")

    var biometricEnabled: Boolean
        get() = prefs.getBoolean("biometric", false)
        set(v) { prefs.edit().putBoolean("biometric", v).apply() }

    /** Auto-lock delay in seconds after leaving the app (0 = immediately). */
    var autoLockSeconds: Int
        get() = prefs.getInt("auto_lock", 30)
        set(v) { prefs.edit().putInt("auto_lock", v).apply() }

    fun setPin(pin: String) {
        val salt = PasswordKdf.randomBytes(16)
        val hash = PasswordKdf.derive(pin.toCharArray(), salt, PasswordKdf.PIN_ITERATIONS)
        prefs.edit()
            .putString("pin_salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("pin_hash", Base64.encodeToString(hash, Base64.NO_WRAP))
            .putInt("failures", 0)
            .commit()
    }

    /** Seconds to wait before another attempt is allowed (0 = allowed). */
    fun waitSeconds(): Long {
        val until = prefs.getLong("blocked_until", 0)
        return ((until - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
    }

    fun verify(pin: String): Boolean {
        if (waitSeconds() > 0) return false
        val salt = Base64.decode(prefs.getString("pin_salt", null) ?: return false, Base64.NO_WRAP)
        val expected = Base64.decode(prefs.getString("pin_hash", null) ?: return false, Base64.NO_WRAP)
        val ok = PasswordKdf.constantTimeEquals(PasswordKdf.derive(pin.toCharArray(), salt, PasswordKdf.PIN_ITERATIONS), expected)
        if (ok) {
            prefs.edit().putInt("failures", 0).putLong("blocked_until", 0).apply()
        } else {
            val failures = prefs.getInt("failures", 0) + 1
            val block = if (failures >= 5) 30_000L shl minOf(failures - 5, 6) else 0L
            prefs.edit().putInt("failures", failures).putLong("blocked_until", System.currentTimeMillis() + block).apply()
        }
        return ok
    }

    fun unlock() { _locked.value = false }

    fun lockNow() { _locked.value = true }

    /** Call right before launching another app's screen so returning from it does not lock. */
    fun expectExternalActivity() { externalLaunchAt = System.currentTimeMillis() }

    fun onBackground() {
        val now = System.currentTimeMillis()
        backgroundedAt = now
        // Only a stop that immediately follows our own launch counts; a stale grace never lingers.
        inExternalActivity = now - externalLaunchAt < 5_000
        externalLaunchAt = 0
    }

    fun onForeground() {
        if (inExternalActivity) { inExternalActivity = false; return }
        if (!hasPin) return
        if (backgroundedAt > 0 && System.currentTimeMillis() - backgroundedAt >= autoLockSeconds * 1000L) _locked.value = true
    }
}
