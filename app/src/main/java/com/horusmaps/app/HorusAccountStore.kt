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
    val email: String, val firstName: String, val lastName: String,
    val phone: String, val imageUri: String? = null
) {
    val displayName: String
        get() = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")
            .ifBlank { email.substringBefore("@") }
}

object HorusAccountStore {
    private const val ACCOUNTS_KEY = "saved_accounts"
    private const val PROFILES_KEY = "account_profiles"
    private const val KEY_ALIAS = "horus_account_session_key"
    private fun normalized(email: String) = email.trim().lowercase()
    private fun profiles(prefs: SharedPreferences) =
        runCatching { JSONObject(prefs.getString(PROFILES_KEY, "{}") ?: "{}") }.getOrDefault(JSONObject())

    fun profileImage(prefs: SharedPreferences, email: String): String? =
        profiles(prefs).optString(normalized(email)).takeIf { it.isNotBlank() }

    private fun saveProfileImage(prefs: SharedPreferences, email: String, uri: String?) {
        val data = profiles(prefs)
        if (uri.isNullOrBlank()) data.remove(normalized(email)) else data.put(normalized(email), uri)
        prefs.edit().putString(PROFILES_KEY, data.toString()).apply()
    }

    fun accounts(prefs: SharedPreferences): List<HorusSavedAccount> {
        val raw = prefs.getString(ACCOUNTS_KEY, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val email = normalized(item.optString("email"))
                if (email.isBlank()) return@mapNotNull null
                val legacy = item.optString("imageUri").takeIf { it.isNotBlank() }
                if (legacy != null && profileImage(prefs, email) == null) saveProfileImage(prefs, email, legacy)
                HorusSavedAccount(email, item.optString("firstName"), item.optString("lastName"),
                    item.optString("phone"), profileImage(prefs, email) ?: legacy)
            }
        }.getOrDefault(emptyList())
    }

    fun saveAccount(prefs: SharedPreferences, account: HorusSavedAccount, token: String?) {
        val updated = accounts(prefs).filterNot { it.email.equals(account.email, true) } + account
        val array = JSONArray()
        updated.forEach { array.put(JSONObject().put("email", it.email).put("firstName", it.firstName)
            .put("lastName", it.lastName).put("phone", it.phone)) }
        prefs.edit().putString(ACCOUNTS_KEY, array.toString()).apply()
        if (account.imageUri != null) saveProfileImage(prefs, account.email, account.imageUri)
        if (!token.isNullOrBlank()) runCatching { encrypt(token) }.onSuccess {
            prefs.edit().putString(tokenKey(account.email), it).apply()
        }
    }

    fun updateImage(prefs: SharedPreferences, email: String, imageUri: String?) {
        saveProfileImage(prefs, email, imageUri)
        val updated = accounts(prefs).map { if (it.email.equals(email, true)) it.copy(imageUri = imageUri) else it }
        val array = JSONArray()
        updated.forEach { array.put(JSONObject().put("email", it.email).put("firstName", it.firstName)
            .put("lastName", it.lastName).put("phone", it.phone)) }
        prefs.edit().putString(ACCOUNTS_KEY, array.toString()).apply()
    }

    fun token(prefs: SharedPreferences, email: String): String? =
        prefs.getString(tokenKey(email), null)?.let { runCatching { decrypt(it) }.getOrNull() }

    fun clearToken(prefs: SharedPreferences, email: String) { prefs.edit().remove(tokenKey(email)).apply() }

    // Logout removes the account from the switcher but preserves its local photo.
    // Permanent deletion removes both the switcher entry and its photo metadata.
    fun removeAccount(prefs: SharedPreferences, email: String, deleteProfile: Boolean = false) {
        val array = JSONArray()
        accounts(prefs).filterNot { it.email.equals(email, true) }.forEach {
            array.put(JSONObject().put("email", it.email).put("firstName", it.firstName)
                .put("lastName", it.lastName).put("phone", it.phone))
        }
        val editor = prefs.edit().putString(ACCOUNTS_KEY, array.toString()).remove(tokenKey(email))
        if (deleteProfile) {
            val data = profiles(prefs); data.remove(normalized(email)); editor.putString(PROFILES_KEY, data.toString())
        }
        editor.apply()
    }

    private fun tokenKey(email: String) = "encrypted_session_" + normalized(email)
    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).build())
        return generator.generateKey()
    }
    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        return Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }
    private fun decrypt(value: String): String {
        val combined = Base64.decode(value, Base64.NO_WRAP); require(combined.size > 12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, combined.copyOfRange(0, 12)))
        return String(cipher.doFinal(combined.copyOfRange(12, combined.size)), Charsets.UTF_8)
    }
}
