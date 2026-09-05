package com.example.data.sync

import android.content.Context
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class SupabaseSyncException(message: String) : Exception(message)

data class SupabaseSession(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val obtainedAtMillis: Long,
    val expiresInSeconds: Long
)

class SupabaseRestClient(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("supabase_session", Context.MODE_PRIVATE)
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val isConfigured: Boolean
        get() = BuildConfig.SUPABASE_URL.isNotBlank() &&
                BuildConfig.SUPABASE_ANON_KEY.isNotBlank() &&
                BuildConfig.SUPABASE_EMAIL.isNotBlank() &&
                BuildConfig.SUPABASE_PASSWORD.isNotBlank()

    fun storagePublicUrl(storagePath: String): String {
        return "${BuildConfig.SUPABASE_URL.trimEnd('/')}/storage/v1/object/public/question-assets/$storagePath"
    }

    suspend fun fetchTable(table: String, params: Map<String, String> = emptyMap()): JSONArray {
        val session = ensureSession()
        val urlBuilder = "${BuildConfig.SUPABASE_URL.trimEnd('/')}/rest/v1/$table".toHttpUrl().newBuilder()
        urlBuilder.addQueryParameter("select", "*")
        params.forEach { (key, value) -> urlBuilder.addQueryParameter(key, value) }
        val request = baseRequest(urlBuilder.build().toString(), session).get().build()
        return JSONArray(executeWithRetry(request))
    }

    suspend fun upsertProgress(rows: JSONArray) {
        if (rows.length() == 0) return
        val session = ensureSession()
        val url = "${BuildConfig.SUPABASE_URL.trimEnd('/')}/rest/v1/progress".toHttpUrl().newBuilder()
            .addQueryParameter("on_conflict", "user_id,question_id")
            .build()
        val request = baseRequest(url.toString(), session)
            .header("Prefer", "resolution=merge-duplicates,return=minimal")
            .post(rows.toString().toRequestBody(jsonMediaType))
            .build()
        executeWithRetry(request)
    }

    suspend fun ensureSession(): SupabaseSession {
        if (!isConfigured) {
            throw SupabaseSyncException("Supabase URL 或 anon key 还没有写入 local.properties")
        }
        val cached = loadSession()
        if (cached == null) return login()
        val ageSeconds = (System.currentTimeMillis() - cached.obtainedAtMillis) / 1000
        return if (ageSeconds >= cached.expiresInSeconds - 60) refresh(cached) else cached
    }

    private suspend fun login(): SupabaseSession {
        val body = JSONObject()
            .put("email", BuildConfig.SUPABASE_EMAIL)
            .put("password", BuildConfig.SUPABASE_PASSWORD)
            .toString()
            .toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url("${BuildConfig.SUPABASE_URL.trimEnd('/')}/auth/v1/token?grant_type=password")
            .header("apikey", BuildConfig.SUPABASE_ANON_KEY)
            .header("Content-Type", "application/json")
            .post(body)
            .build()
        return parseAndSaveSession(executeWithRetry(request))
    }

    private suspend fun refresh(session: SupabaseSession): SupabaseSession {
        val body = JSONObject()
            .put("refresh_token", session.refreshToken)
            .toString()
            .toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url("${BuildConfig.SUPABASE_URL.trimEnd('/')}/auth/v1/token?grant_type=refresh_token")
            .header("apikey", BuildConfig.SUPABASE_ANON_KEY)
            .header("Content-Type", "application/json")
            .post(body)
            .build()
        return try {
            parseAndSaveSession(executeWithRetry(request))
        } catch (error: Exception) {
            login()
        }
    }

    private fun baseRequest(url: String, session: SupabaseSession): Request.Builder {
        return Request.Builder()
            .url(url)
            .header("apikey", BuildConfig.SUPABASE_ANON_KEY)
            .header("Authorization", "Bearer ${session.accessToken}")
            .header("Content-Type", "application/json")
    }

    private suspend fun executeWithRetry(request: Request): String = withContext(Dispatchers.IO) {
        var lastError: Exception? = null
        repeat(3) { attempt ->
            try {
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (response.isSuccessful) return@withContext body
                    throw SupabaseSyncException("Supabase 请求失败 ${response.code}: ${body.take(240)}")
                }
            } catch (error: IOException) {
                lastError = error
                if (attempt < 2) kotlinx.coroutines.delay((attempt + 1) * 2000L)
            }
        }
        throw SupabaseSyncException("网络请求连续失败，请稍后重试：${lastError?.message ?: "unknown"}")
    }

    private fun parseAndSaveSession(raw: String): SupabaseSession {
        val obj = JSONObject(raw)
        val session = SupabaseSession(
            accessToken = obj.getString("access_token"),
            refreshToken = obj.optString("refresh_token"),
            userId = obj.getJSONObject("user").getString("id"),
            obtainedAtMillis = System.currentTimeMillis(),
            expiresInSeconds = obj.optLong("expires_in", 3600L)
        )
        prefs.edit()
            .putString("access_token", session.accessToken)
            .putString("refresh_token", session.refreshToken)
            .putString("user_id", session.userId)
            .putLong("obtained_at", session.obtainedAtMillis)
            .putLong("expires_in", session.expiresInSeconds)
            .apply()
        return session
    }

    private fun loadSession(): SupabaseSession? {
        val accessToken = prefs.getString("access_token", "").orEmpty()
        val refreshToken = prefs.getString("refresh_token", "").orEmpty()
        val userId = prefs.getString("user_id", "").orEmpty()
        if (accessToken.isBlank() || refreshToken.isBlank() || userId.isBlank()) return null
        return SupabaseSession(
            accessToken = accessToken,
            refreshToken = refreshToken,
            userId = userId,
            obtainedAtMillis = prefs.getLong("obtained_at", 0L),
            expiresInSeconds = prefs.getLong("expires_in", 3600L)
        )
    }
}
