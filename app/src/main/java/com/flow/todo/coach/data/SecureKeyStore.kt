package com.flow.todo.coach.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.flow.todo.coach.LlmProvider
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores BYOK API keys encrypted with a non-exportable AES-256-GCM key held in the Android
 * Keystore. Ciphertext lives in private SharedPreferences. If the Keystore key is lost (e.g. the
 * prefs were restored from a backup onto a new device) [get] returns null and the user simply
 * re-enters the key. Keys are never stored in plaintext.
 */
class SecureKeyStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun has(provider: LlmProvider): Boolean = prefs.contains(provider.name)

    fun get(provider: LlmProvider): String? {
        val stored = prefs.getString(provider.name, null) ?: return null
        return runCatching {
            val bytes = Base64.decode(stored, Base64.NO_WRAP)
            val iv = bytes.copyOfRange(0, IV_BYTES)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
            String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
        }.getOrNull()
    }

    /** @return false if the key could not be encrypted (nothing is stored in that case). */
    fun put(provider: LlmProvider, apiKey: String): Boolean {
        val trimmed = apiKey.trim()
        if (trimmed.isEmpty()) return false
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            val ciphertext = cipher.doFinal(trimmed.toByteArray(Charsets.UTF_8))
            val packed = cipher.iv + ciphertext
            prefs.edit().putString(provider.name, Base64.encodeToString(packed, Base64.NO_WRAP)).apply()
            true
        }.getOrDefault(false)
    }

    fun remove(provider: LlmProvider) {
        prefs.edit().remove(provider.name).apply()
    }

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val PREFS = "flow_coach_keys"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "flow_coach_api_keys_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
