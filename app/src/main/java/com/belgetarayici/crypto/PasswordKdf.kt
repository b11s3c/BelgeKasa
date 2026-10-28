package com.belgetarayici.crypto

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PasswordKdf {
    const val SHARE_ITERATIONS = 310_000
    const val PIN_ITERATIONS = 210_000

    fun derive(password: CharArray, salt: ByteArray, iterations: Int, bytes: Int = 32): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, bytes * 8)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun randomBytes(n: Int): ByteArray = ByteArray(n).also { SecureRandom().nextBytes(it) }

    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean = MessageDigest.isEqual(a, b)
}
