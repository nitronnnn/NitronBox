package com.nitronbox.app.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface CredentialStore {
    fun put(alias: String, secret: CharArray)
    fun get(alias: String): CharArray?
    fun delete(alias: String)
}

/**
 * Small encrypted credential vault. Ciphertext lives in private SharedPreferences while the
 * non-exportable AES key lives in Android Keystore. Callers should clear returned CharArrays.
 */
class SecureCredentialStore(context: Context) : CredentialStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    override fun put(alias: String, secret: CharArray) {
        require(ALIAS_PATTERN.matches(alias)) { "Credential alias contains unsupported characters" }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        }
        val plaintext = secret.concatToString().encodeToByteArray()
        try {
            val ciphertext = cipher.doFinal(plaintext)
            val encoded = listOf(cipher.iv, ciphertext)
                .joinToString(SEPARATOR) { Base64.encodeToString(it, Base64.NO_WRAP) }
            check(preferences.edit().putString(alias, encoded).commit()) {
                "Unable to persist encrypted credential"
            }
        } finally {
            plaintext.fill(0)
        }
    }

    override fun get(alias: String): CharArray? {
        val encoded = preferences.getString(alias, null) ?: return null
        val components = encoded.split(SEPARATOR, limit = 2)
        if (components.size != 2) return null
        return runCatching {
            val iv = Base64.decode(components[0], Base64.NO_WRAP)
            val ciphertext = Base64.decode(components[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            }
            val plaintext = cipher.doFinal(ciphertext)
            try {
                plaintext.decodeToString().toCharArray()
            } finally {
                plaintext.fill(0)
            }
        }.getOrNull()
    }

    override fun delete(alias: String) {
        preferences.edit().remove(alias).apply()
    }

    private fun getOrCreateKey(): SecretKey {
        (keyStore.getKey(MASTER_KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    MASTER_KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val MASTER_KEY_ALIAS = "nitronbox.credentials.master.v1"
        const val PREFERENCES_NAME = "encrypted_provider_credentials"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
        const val SEPARATOR = "."
        val ALIAS_PATTERN = Regex("[a-zA-Z0-9._-]{1,128}")
    }
}