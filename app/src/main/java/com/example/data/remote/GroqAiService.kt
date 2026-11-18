package com.example.data.remote

import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class GroqGptModel(
    val id: String,
    val displayName: String,
    val description: String
)

data class AiChatMessage(
    val id: Long = System.nanoTime(),
    val isUser: Boolean,
    val content: String,
    val modelId: String? = null,
    val isError: Boolean = false,
    val timestampMs: Long = System.currentTimeMillis()
)

sealed interface GroqAiResult {
    data class Success(val reply: String, val modelId: String) : GroqAiResult
    data class Failure(val errorMessage: String) : GroqAiResult
}

class GroqAiService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        const val ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"
        const val DEFAULT_MODEL_ID = "openai/gpt-oss-120b"
        const val FAST_MODEL_ID = "openai/gpt-oss-20b"

        val SUPPORTED_MODELS = listOf(
            GroqGptModel(
                id = DEFAULT_MODEL_ID,
                displayName = "GPT-OSS 120B",
                description = "نموذج GPT المتقدم لتحليل الحسابات والأرباح والتوصيات"
            ),
            GroqGptModel(
                id = FAST_MODEL_ID,
                displayName = "GPT-OSS 20B",
                description = "نموذج GPT السريع للاستفسارات اليومية الفورية"
            )
        )

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val PLACEHOLDER_KEY = "YOUR_GROQ_API_KEY"
    }

    fun hasConfiguredKey(apiKey: String = BuildConfig.GROQ_API_KEY): Boolean {
        val trimmed = apiKey.trim()
        return trimmed.isNotEmpty() && trimmed != PLACEHOLDER_KEY && trimmed != "null"
    }

    fun buildRequestJson(
        modelId: String,
        systemPrompt: String,
        history: List<AiChatMessage>,
        userMessage: String
    ): String {
        val safeModel = SUPPORTED_MODELS.firstOrNull { it.id == modelId }?.id ?: DEFAULT_MODEL_ID
        val messagesArray = JSONArray()
        messagesArray.put(
            JSONObject().apply {
                put("role", "system")
                put("content", systemPrompt)
            }
        )
        history
            .filter { !it.isError && it.content.isNotBlank() }
            .takeLast(8)
            .forEach { msg ->
                messagesArray.put(
                    JSONObject().apply {
                        put("role", if (msg.isUser) "user" else "assistant")
                        put("content", msg.content)
                    }
                )
            }
        messagesArray.put(
            JSONObject().apply {
                put("role", "user")
                put("content", userMessage)
            }
        )

        return JSONObject().apply {
            put("model", safeModel)
            put("messages", messagesArray)
            put("temperature", 0.35)
            put("max_tokens", 1200)
        }.toString()
    }

    fun parseAssistantReply(rawJson: String): String {
        val root = JSONObject(rawJson)
        val choices = root.optJSONArray("choices")
            ?: throw IllegalStateException("استجابة غير متوقعة من خادم Groq")
        if (choices.length() == 0) {
            throw IllegalStateException("لم يُرجع النموذج أي رد")
        }
        val messageObj = choices.getJSONObject(0).optJSONObject("message")
            ?: throw IllegalStateException("محتوى الرد مفقود")
        val content = messageObj.optString("content", "").trim()
        if (content.isEmpty()) {
            throw IllegalStateException("النموذج أعاد رداً فارغاً")
        }
        return content
    }

    fun formatHttpError(statusCode: Int, rawBody: String): String {
        val apiMsg = runCatching {
            JSONObject(rawBody).optJSONObject("error")?.optString("message").orEmpty()
        }.getOrDefault("")
        return when (statusCode) {
            401, 403 -> "مفتاح Groq API غير صالح أو غير مصرح له. تأكد من قيمة GROQ_API_KEY في لوحة Secrets."
            404 -> "نموذج GPT المطلوب غير متاح حالياً ($apiMsg)."
            429 -> "تم تجاوز حد الطلبات المؤقت على Groq. يرجى الانتظار بضع ثوانٍ ثم إعادة المحاولة."
            in 500..599 -> "خطأ مؤقت في خوادم Groq ($statusCode). حاول مجدداً بعد قليل."
            else -> if (apiMsg.isNotBlank()) "تعذر إتمام الطلب ($statusCode): $apiMsg" else "تعذر الاتصال بنموذج GPT (رمز الخطأ $statusCode)."
        }
    }

    suspend fun sendChatCompletion(
        modelId: String,
        systemPrompt: String,
        history: List<AiChatMessage>,
        userMessage: String,
        apiKey: String = BuildConfig.GROQ_API_KEY
    ): GroqAiResult = withContext(Dispatchers.IO) {
        if (!hasConfiguredKey(apiKey)) {
            return@withContext GroqAiResult.Failure(
                "مفتاح GROQ_API_KEY غير مضبوط بعد. يرجى إضافة GROQ_API_KEY في لوحة Secrets داخل Google AI Studio ثم إعادة بناء التطبيق."
            )
        }
        val payload = buildRequestJson(modelId, systemPrompt, history, userMessage)
        val request = Request.Builder()
            .url(ENDPOINT)
            .addHeader("Authorization", "Bearer ${apiKey.trim()}")
            .addHeader("Content-Type", "application/json")
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext GroqAiResult.Failure(formatHttpError(response.code, bodyStr))
                }
                val reply = parseAssistantReply(bodyStr)
                val safeModel = SUPPORTED_MODELS.firstOrNull { it.id == modelId }?.id ?: DEFAULT_MODEL_ID
                GroqAiResult.Success(reply = reply, modelId = safeModel)
            }
        } catch (e: IOException) {
            GroqAiResult.Failure("تعذر الاتصال بالإنترنت أو خادم Groq: ${e.localizedMessage ?: "خطأ شبكة"}")
        } catch (e: Exception) {
            GroqAiResult.Failure(e.localizedMessage ?: "حدث خطأ أثناء معالجة رد الذكاء الاصطناعي")
        }
    }

    suspend fun sendStructuredCompletion(
        modelId: String = DEFAULT_MODEL_ID,
        systemPrompt: String,
        userPrompt: String,
        apiKey: String = BuildConfig.GROQ_API_KEY
    ): GroqAiResult = withContext(Dispatchers.IO) {
        if (!hasConfiguredKey(apiKey)) {
            return@withContext GroqAiResult.Failure("مفتاح GROQ_API_KEY غير مضبوط")
        }
        val safeModel = SUPPORTED_MODELS.firstOrNull { it.id == modelId }?.id ?: DEFAULT_MODEL_ID
        val messagesArray = JSONArray().apply {
            put(JSONObject().apply { put("role", "system"); put("content", systemPrompt) })
            put(JSONObject().apply { put("role", "user"); put("content", userPrompt) })
        }
        val payload = JSONObject().apply {
            put("model", safeModel)
            put("messages", messagesArray)
            put("temperature", 0.1)
            put("max_tokens", 1600)
        }.toString()

        val request = Request.Builder()
            .url(ENDPOINT)
            .addHeader("Authorization", "Bearer ${apiKey.trim()}")
            .addHeader("Content-Type", "application/json")
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext GroqAiResult.Failure(formatHttpError(response.code, bodyStr))
                }
                GroqAiResult.Success(reply = parseAssistantReply(bodyStr), modelId = safeModel)
            }
        } catch (e: IOException) {
            GroqAiResult.Failure("تعذر الاتصال بخادم Groq: ${e.localizedMessage ?: "خطأ شبكة"}")
        } catch (e: Exception) {
            GroqAiResult.Failure(e.localizedMessage ?: "تعذر تحليل الاستجابة")
        }
    }
}
