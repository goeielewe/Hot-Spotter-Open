package com.benchfirewatch.alert

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Tapo password, Tasmota password, and the Home Assistant token.
 * The ciphertext sits in a private preferences file. The key stays in the
 * Android keystore and does not go into the settings JSON.
 */
object PlugSecrets {
    private const val PREFS = "plug-secrets"
    private const val ALIAS = "hotspotter-plug"
    private const val PASSWORD = "password"
    private const val TOKEN = "token"

    fun password(context: Context): String = read(context, PASSWORD)

    fun token(context: Context): String = read(context, TOKEN)

    fun hasPassword(context: Context): Boolean = password(context).isNotEmpty()

    fun hasToken(context: Context): Boolean = token(context).isNotEmpty()

    fun setPassword(context: Context, value: String) = write(context, PASSWORD, value)

    fun setToken(context: Context, value: String) = write(context, TOKEN, value)

    private fun read(context: Context, name: String): String {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(name, null) ?: return ""
        return try {
            val raw = Base64.decode(stored, Base64.NO_WRAP)
            if (raw.size < 13) return ""
            val iv = raw.copyOfRange(0, 12)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            cipher.doFinal(raw.copyOfRange(12, raw.size)).toString(Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }

    private fun write(context: Context, name: String, value: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (value.isEmpty()) {
            prefs.edit().remove(name).apply()
            return
        }
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val iv = cipher.iv
            val body = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            val packed = ByteArray(iv.size + body.size)
            iv.copyInto(packed)
            body.copyInto(packed, iv.size)
            prefs.edit().putString(name, Base64.encodeToString(packed, Base64.NO_WRAP)).apply()
        } catch (_: Exception) {
            // A phone without a keystore still must not write the secret in the clear.
        }
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = store.getKey(ALIAS, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }
}
