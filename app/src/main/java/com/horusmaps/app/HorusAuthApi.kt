package com.horusmaps.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val HORUS_API_BASE = "https://horus-maps-final.horus-egypt-company.workers.dev"

data class EmailCheckResult(val available: Boolean, val message: String)
data class RegisterResult(val success: Boolean, val email: String? = null, val sessionToken: String? = null, val message: String)
data class LoginResult(
    val success: Boolean,
    val email: String? = null,
    val firstName: String = "",
    val lastName: String = "",
    val phone: String = "",
    val sessionToken: String? = null,
    val message: String
)

object HorusAuthApi {
    suspend fun checkEmail(username: String): EmailCheckResult = withContext(Dispatchers.IO) {
        val response = post("/v1/auth/email-availability", JSONObject().put("username", username))
        val root = response.second
        if (response.first in 200..299 && root.optBoolean("ok")) {
            if (root.optBoolean("available")) EmailCheckResult(true, "اسم البريد متاح ويمكنك استخدامه.")
            else EmailCheckResult(false, "هذا الاسم مستخدم بالفعل. جرّب اسمًا آخر.")
        } else EmailCheckResult(false, root.optString("error", "تعذر التحقق من البريد الآن."))
    }

    suspend fun register(firstName: String, lastName: String, phone: String?, username: String, password: String): RegisterResult =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("firstName", firstName)
                .put("lastName", lastName)
                .put("phone", phone ?: "")
                .put("username", username)
                .put("password", password)
            val response = post("/v1/auth/register", body)
            val root = response.second
            if (response.first in 200..299 && root.optBoolean("ok")) {
                RegisterResult(true, root.optJSONObject("user")?.optString("email"), root.optString("sessionToken").takeIf { it.isNotBlank() }, root.optString("message", "تم إنشاء الحساب بنجاح."))
            } else RegisterResult(false, message = root.optString("error", "تعذر إنشاء الحساب. حاول مرة أخرى."))
        }

    suspend fun login(email: String, password: String): LoginResult = withContext(Dispatchers.IO) {
        val body = JSONObject().put("email", email.trim().lowercase()).put("password", password)
        val response = post("/v1/auth/login", body)
        val root = response.second
        val user = root.optJSONObject("user")
        if (response.first in 200..299 && root.optBoolean("ok") && user != null) {
            LoginResult(
                success = true,
                email = user.optString("email"),
                firstName = user.optString("firstName"),
                lastName = user.optString("lastName"),
                phone = user.optString("phone"),
                sessionToken = root.optString("sessionToken").takeIf { it.isNotBlank() },
                message = root.optString("message", "تم تسجيل الدخول بنجاح.")
            )
        } else {
            LoginResult(false, message = root.optString("error", "البريد الإلكتروني أو كلمة المرور غير صحيحة."))
        }
    }

    suspend fun currentUser(token: String): LoginResult = withContext(Dispatchers.IO) {
        val response = post("/v1/auth/me", JSONObject(), token)
        val root = response.second
        val user = root.optJSONObject("user")
        if (response.first in 200..299 && root.optBoolean("ok") && user != null) {
            LoginResult(
                success = true,
                email = user.optString("email"),
                firstName = user.optString("firstName"),
                lastName = user.optString("lastName"),
                phone = user.optString("phone"),
                sessionToken = token,
                message = "تم التحقق من الحساب."
            )
        } else LoginResult(false, message = root.optString("error", "الجلسة غير صالحة."))
    }

    suspend fun logout(token: String): Boolean = withContext(Dispatchers.IO) {
        val response = post("/v1/auth/logout", JSONObject(), token)
        response.first in 200..299 && response.second.optBoolean("ok")
    }

    suspend fun deleteAccount(token: String): Boolean = withContext(Dispatchers.IO) {
        val response = post("/v1/auth/delete-account", JSONObject(), token)
        response.first in 200..299 && response.second.optBoolean("ok")
    }

    private fun post(path: String, body: JSONObject, bearerToken: String? = null): Pair<Int, JSONObject> {
        val connection = (URL(HORUS_API_BASE + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 12_000
            readTimeout = 20_000
            doOutput = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            if (!bearerToken.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $bearerToken")
        }
        return try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            status to runCatching { JSONObject(text) }.getOrElse {
                JSONObject().put("error", "استجابة غير مفهومة من السيرفر.")
            }
        } finally {
            connection.disconnect()
        }
    }
}
