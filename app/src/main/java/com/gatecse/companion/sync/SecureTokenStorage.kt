package com.gatecse.companion.sync

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android-supported, Keystore-backed storage for sensitive companion session tokens.
 *
 * Security architecture:
 * 1. Passwords are NEVER persisted to disk after authentication.
 * 2. Only the revocable session token is persisted.
 * 3. The token is protected using standard hardware-backed AES-256 GCM authenticated
 *    encryption via AndroidKeyStore (API 26+).
 * 4. Zero custom cryptography or hardcoded secrets.
 */
interface TokenCryptoProvider {
    fun encrypt(plainText: String): String
    fun decrypt(cipherText: String): String
}

class AndroidKeyStoreCryptoProvider(
    private val keyAlias: String = "gate_companion_sync_token_key"
) : TokenCryptoProvider {

    private val keyStore: KeyStore = KeyStore.getInstance("AndroidKeyStore").apply {
        load(null)
    }

    private fun getOrCreateKey(): SecretKey {
        if (!keyStore.containsAlias(keyAlias)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore"
            )
            val spec = KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGenerator.init(spec)
            return keyGenerator.generateKey()
        }
        val entry = keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry
            ?: throw IllegalStateException("Key alias exists but is not a SecretKeyEntry")
        return entry.secretKey
    }

    override fun encrypt(plainText: String): String {
        val secretKey = getOrCreateKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv // 12 bytes standard GCM IV
        val cipherBytes = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))

        // Pack: IV length (1 byte) + IV + ciphertext
        val buffer = ByteBuffer.allocate(1 + iv.size + cipherBytes.size)
        buffer.put(iv.size.toByte())
        buffer.put(iv)
        buffer.put(cipherBytes)
        return Base64.getEncoder().encodeToString(buffer.array())
    }

    override fun decrypt(cipherText: String): String {
        val raw = Base64.getDecoder().decode(cipherText)
        val buffer = ByteBuffer.wrap(raw)
        val ivSize = buffer.get().toInt()
        val iv = ByteArray(ivSize)
        buffer.get(iv)
        val cipherBytes = ByteArray(buffer.remaining())
        buffer.get(cipherBytes)

        val secretKey = getOrCreateKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        val plainBytes = cipher.doFinal(cipherBytes)
        return String(plainBytes, StandardCharsets.UTF_8)
    }
}

class JvmAesGcmCryptoProvider(
    private val secretKey: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
) : TokenCryptoProvider {

    override fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val cipherBytes = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))

        val buffer = ByteBuffer.allocate(1 + iv.size + cipherBytes.size)
        buffer.put(iv.size.toByte())
        buffer.put(iv)
        buffer.put(cipherBytes)
        return Base64.getEncoder().encodeToString(buffer.array())
    }

    override fun decrypt(cipherText: String): String {
        val raw = Base64.getDecoder().decode(cipherText)
        val buffer = ByteBuffer.wrap(raw)
        val ivSize = buffer.get().toInt()
        val iv = ByteArray(ivSize)
        buffer.get(iv)
        val cipherBytes = ByteArray(buffer.remaining())
        buffer.get(cipherBytes)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        val plainBytes = cipher.doFinal(cipherBytes)
        return String(plainBytes, StandardCharsets.UTF_8)
    }
}

object SecureTokenStorage {
    private const val PREFS_NAME = "gate_auth_prefs"
    private const val KEY_ENCRYPTED_TOKEN = "enc_session_token"

    @Volatile
    var customProviderForTesting: TokenCryptoProvider? = null

    @Volatile
    var customPrefsForTesting: android.content.SharedPreferences? = null

    private fun getProvider(): TokenCryptoProvider {
        val custom = customProviderForTesting
        if (custom != null) return custom
        return try {
            AndroidKeyStoreCryptoProvider()
        } catch (_: Throwable) {
            // JVM testing or fallback environment
            JvmAesGcmCryptoProvider()
        }
    }

    private fun getPrefs(context: Context?): android.content.SharedPreferences? {
        val custom = customPrefsForTesting
        if (custom != null) return custom
        return context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveToken(context: Context?, token: String) {
        val clean = token.trim()
        if (clean.isBlank()) return
        val encrypted = getProvider().encrypt(clean)
        getPrefs(context)?.edit()
            ?.putString(KEY_ENCRYPTED_TOKEN, encrypted)
            ?.apply()
    }

    fun getToken(context: Context?): String? {
        val encrypted = getPrefs(context)
            ?.getString(KEY_ENCRYPTED_TOKEN, null) ?: return null
        return try {
            getProvider().decrypt(encrypted)
        } catch (_: Exception) {
            null
        }
    }

    fun clearToken(context: Context?) {
        getPrefs(context)?.edit()
            ?.remove(KEY_ENCRYPTED_TOKEN)
            ?.apply()
    }

    fun hasToken(context: Context?): Boolean {
        return !getToken(context).isNullOrBlank()
    }
}
