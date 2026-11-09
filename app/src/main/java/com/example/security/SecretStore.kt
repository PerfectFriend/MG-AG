package com.example.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM secrets backed by a non-exportable Android Keystore key.
 *
 * When the Keystore provider is unavailable, encryption returns null and callers
 * must refuse to persist the value. Nothing is ever written in plaintext as a
 * fallback: a missing Keystore disables secret persistence, it does not weaken it.
 */
object SecretStore {
    private const val TAG = "SecretStore"
    private const val PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "madgammon_secret_store_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    private val keyStore: KeyStore? by lazy {
        runCatching { KeyStore.getInstance(PROVIDER).apply { load(null) } }
            .onFailure { Log.e(TAG, "Android Keystore provider is unavailable", it) }
            .getOrNull()
    }

    private fun secretKey(create: Boolean): SecretKey? {
        val store = keyStore ?: return null
        (store.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey?.let { return it }
        if (!create) return null
        return runCatching {
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER).apply {
                init(
                    KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .setRandomizedEncryptionRequired(true)
                        .build()
                )
            }.generateKey()
        }.onFailure { Log.e(TAG, "Could not create the secret-store key", it) }.getOrNull()
    }

    fun encrypt(plain: String): String? = runCatching {
        val key = secretKey(create = true) ?: return null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val body = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        Base64.encodeToString(cipher.iv + body, Base64.NO_WRAP)
    }.onFailure { Log.e(TAG, "Secret encryption failed; value will not be persisted", it) }.getOrNull()

    fun decrypt(blob: String): String? = runCatching {
        val key = secretKey(create = false) ?: return null
        val raw = Base64.decode(blob, Base64.NO_WRAP)
        if (raw.size <= IV_BYTES) return null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, raw, 0, IV_BYTES))
        String(cipher.doFinal(raw, IV_BYTES, raw.size - IV_BYTES), Charsets.UTF_8)
    }.onFailure { Log.e(TAG, "Secret decryption failed", it) }.getOrNull()
}

/**
 * SharedPreferences facade that keeps selected keys encrypted at rest.
 *
 * A legacy plaintext value under [key] is migrated on first read and then
 * deleted, so existing installs stop leaking the moment they are opened.
 */
class EncryptedPrefs(context: Context, name: String) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(name, Context.MODE_PRIVATE)

    fun getString(key: String, defaultValue: String = ""): String {
        prefs.getString(ENC_PREFIX + key, null)?.let { blob ->
            SecretStore.decrypt(blob)?.let { return it }
        }
        val legacy = prefs.getString(key, null) ?: return defaultValue
        return if (putString(key, legacy)) legacy else {
            prefs.edit().remove(key).apply()
            defaultValue
        }
    }

    fun putString(key: String, value: String): Boolean {
        val blob = SecretStore.encrypt(value) ?: return false
        prefs.edit().putString(ENC_PREFIX + key, blob).remove(key).apply()
        return true
    }

    fun remove(key: String) {
        prefs.edit().remove(ENC_PREFIX + key).remove(key).apply()
    }

    private companion object {
        const val ENC_PREFIX = "enc_v1_"
    }
}
