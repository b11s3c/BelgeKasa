package com.belgetarayici.crypto

import com.belgetarayici.i18n.tr
import android.content.Context
import android.util.Base64
import com.belgetarayici.share.Btz
import com.google.crypto.tink.Aead
import com.google.crypto.tink.HybridDecrypt
import com.google.crypto.tink.HybridEncrypt
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.TinkProtoKeysetFormat
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.hybrid.HybridConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import java.security.MessageDigest

/**
 * All long-lived keys. Tink keysets are stored in SharedPreferences, wrapped by a non-exportable
 * AES key in Android Keystore, so copied app data cannot be decrypted on another device.
 */
class KeyManager(private val context: Context) {
    init {
        AeadConfig.register()
        HybridConfig.register()
    }

    private fun keyset(name: String, template: String): KeysetHandle =
        AndroidKeysetManager.Builder()
            .withSharedPref(context, name, PREFS)
            .withKeyTemplate(KeyTemplates.get(template))
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
            .keysetHandle

    /** Encrypts vault blobs (pages, signatures) and the database passphrase. */
    val vault: Aead by lazy {
        keyset("vault_aead", "AES256_GCM").getPrimitive(RegistryConfiguration.get(), Aead::class.java)
    }

    private val identity: KeysetHandle by lazy { keyset("identity_hpke", HPKE_TEMPLATE) }

    /** This device's public share key, as text for QR codes: `BTKEY1:<base64url>`. */
    val publicKeyText: String by lazy {
        val bytes = TinkProtoKeysetFormat.serializeKeysetWithoutSecret(identity.publicKeysetHandle)
        KEY_PREFIX + Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    val publicKeyFingerprint: String by lazy { fingerprint(publicKeyText) }

    val unwrapper = Btz.RecipientUnwrapper { wrapped, ctx ->
        identity.getPrimitive(RegistryConfiguration.get(), HybridDecrypt::class.java).decrypt(wrapped, ctx)
    }

    /** Database passphrase: random, stored encrypted under the vault key. */
    fun databasePassphrase(): ByteArray {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val aad = "db-passphrase".toByteArray()
        prefs.getString("db_pass", null)?.let { return vault.decrypt(Base64.decode(it, Base64.NO_WRAP), aad) }
        val pass = PasswordKdf.randomBytes(32)
        prefs.edit().putString("db_pass", Base64.encodeToString(vault.encrypt(pass, aad), Base64.NO_WRAP)).commit()
        return pass
    }

    companion object {
        private const val PREFS = "belgetarayici_keys"
        private const val MASTER_KEY_URI = "android-keystore://belgetarayici_master"
        private const val HPKE_TEMPLATE = "DHKEM_X25519_HKDF_SHA256_HKDF_SHA256_AES_256_GCM"
        const val KEY_PREFIX = "BTKEY1:"

        /** Parses a recipient key from QR/text and returns a wrapper for `.btz` packages. */
        fun recipient(keyText: String): Btz.RecipientWrapper {
            val handle = parsePublicKey(keyText)
            val encrypt = handle.getPrimitive(RegistryConfiguration.get(), HybridEncrypt::class.java)
            return object : Btz.RecipientWrapper {
                override fun wrap(contentKey: ByteArray, context: ByteArray) = encrypt.encrypt(contentKey, context)
            }
        }

        fun isValidPublicKey(keyText: String): Boolean = runCatching { parsePublicKey(keyText) }.isSuccess

        private fun parsePublicKey(keyText: String): KeysetHandle {
            val t = keyText.trim()
            require(t.startsWith(KEY_PREFIX)) { tr("Geçersiz anahtar") }
            val bytes = Base64.decode(t.removePrefix(KEY_PREFIX), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            return TinkProtoKeysetFormat.parseKeysetWithoutSecret(bytes)
        }

        /** Short, human-comparable fingerprint (first 10 bytes of SHA-256, grouped). */
        fun fingerprint(keyText: String): String =
            MessageDigest.getInstance("SHA-256").digest(keyText.trim().toByteArray()).take(10)
                .joinToString("") { "%02X".format(it) }.chunked(4).joinToString(" ")
    }
}
