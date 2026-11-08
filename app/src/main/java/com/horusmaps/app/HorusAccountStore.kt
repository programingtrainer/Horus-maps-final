package com.horusmaps.app

import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class HorusSavedAccount(
    val email: String,
    val firstName: String,
    val lastName: String,
    val phone: String,
    val imageUri: String? = null
) {
    val displayName: String
        get() = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")
            .ifBlank { email.substringBefore("@") }
}

object HorusAccountStore {
    private const val ACCOUNTS_KEY = "saved_accounts"
    private const val KEY_ALIAS = "horus_account_session_key"

    fun accounts(prefs: SharedPreferences): List<HorusSavedAccount> {
        val raw = prefs.getString(ACCOUNTS_KEY, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val email = item.optString("email").trim().lowercase()
                if (email.isBlank()) return@mapNotNull null
                HorusSavedAccount(
                    email = email,
                    firstName = item.optString("firstName"),
                    lastName = item.optString("lastName"),
                    phone = item.optString("phone"),
                    imageUri = item.optString("imageUri").takeIf { it.isNotBlank() }
                )
            }
        }.getOrDefault(emptyList())
    }

    fun saveAccount(prefs: SharedPreferences, account: HorusSavedAccount, token: String?) {
        val updated = accounts(prefs).filterNot { it.email.equals(account.email, true) } + account
        val array = JSONArray()
        updated.forEach {
            array.put(JSONObject()
                .put("email", it.email)
                .put("firstName", it.firstName)
                .put("lastName", it.lastName)
                .put("phone", it.phone)
                .put("imageUri", it.imageUri ?: ""))
        }
        prefs.edit().putString(ACCOUNTS_KEY, array.toString()).apply()
        if (!token.isNullOrBlank()) {
            runCatching { encrypt(token) }.onSuccess { encrypted ->
                prefs.edit().putString(tokenKey(account.email), encrypted).apply()
            }
        }
    }

    fun updateImage(prefs: SharedPreferences, email: String, imageUri: String?) {
        val existing = accounts(prefs).firstOrNull { it.email.equals(email, true) } ?: return
        saveAccount(prefs, existing.copy(imageUri = imageUri), token(prefs, email))
    }

    fun token(prefs: SharedPreferences, email: String): String? {
        val encrypted = prefs.getString(tokenKey(email), null) ?: return null
        return runCatching { decrypt(encrypted) }.getOrNull()
    }

    fun clearToken(prefs: SharedPreferences, email: String) {
        prefs.edit().remove(tokenKey(email)).apply()
    }

    fun removeAccount(prefs: SharedPreferences, email: String) {
        val remaining = accounts(prefs).filterNot { it.email.equals(email, true) }
        val array = JSONArray()
        remaining.forEach {
            array.put(JSONObject()
                .put("email", it.email)
                .put("firstName", it.firstName)
                .put("lastName", it.lastName)
                .put("phone", it.phone)
                .put("imageUri", it.imageUri ?: ""))
        }
        prefs.edit().putString(ACCOUNTS_KEY, array.toString()).remove(tokenKey(email)).apply()
    }

    private fun tokenKey(email: String) = "encrypted_session_" + email.lowercase()

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val combined = cipher.iv + encrypted
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val combined = Base64.decode(value, Base64.NO_WRAP)
        require(combined.size > 12)
        val iv = combined.copyOfRange(0, 12)
        val encrypted = combined.copyOfRange(12, combined.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }
}
