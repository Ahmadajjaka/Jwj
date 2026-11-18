package com.example.data.remote

import com.example.utils.ArabicFormatters
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class GroqAiServiceTest {

    private val service = GroqAiService()

    @Test
    fun hasConfiguredKey_rejectsBlankPlaceholderAndNull() {
        assertFalse(service.hasConfiguredKey(""))
        assertFalse(service.hasConfiguredKey("   "))
        assertFalse(service.hasConfiguredKey("YOUR_GROQ_API_KEY"))
        assertFalse(service.hasConfiguredKey("null"))
        assertTrue(service.hasConfiguredKey("gsk_live_test_token_12345"))
    }

    @Test
    fun buildRequestJson_buildsValidOpenAiCompatiblePayloadForGroqGptModels() {
        val history = listOf(
            AiChatMessage(isUser = true, content = "كم مبيعات اليوم؟"),
            AiChatMessage(isUser = false, content = "مبيعات اليوم 500 د.ع"),
            AiChatMessage(isUser = false, content = "خطأ مؤقت", isError = true)
        )
        val payload = service.buildRequestJson(
            modelId = GroqAiService.FAST_MODEL_ID,
            systemPrompt = "أنت مساعد محاسبي",
            history = history,
            userMessage = "حلل الأرباح"
        )
        val json = JSONObject(payload)
        assertEquals(GroqAiService.FAST_MODEL_ID, json.getString("model"))
        val messages = json.getJSONArray("messages")
        // system + 2 non-error history items + current user message = 4
        assertEquals(4, messages.length())
        assertEquals("system", messages.getJSONObject(0).getString("role"))
        assertEquals("user", messages.getJSONObject(1).getString("role"))
        assertEquals("assistant", messages.getJSONObject(2).getString("role"))
        assertEquals("user", messages.getJSONObject(3).getString("role"))
        assertEquals("حلل الأرباح", messages.getJSONObject(3).getString("content"))
    }

    @Test
    fun parseAssistantReply_extractsTrimmedChoiceMessageContent() {
        val raw = """
            {
              "choices": [
                {
                  "message": {
                    "role": "assistant",
                    "content": "  صافي الربح ممتاز اليوم.  "
                  }
                }
              ]
            }
        """.trimIndent()
        assertEquals("صافي الربح ممتاز اليوم.", service.parseAssistantReply(raw))
    }

    @Test
    fun formatHttpError_mapsStatusCodesToArabicMessages() {
        val authErr = service.formatHttpError(401, """{"error":{"message":"Invalid API Key"}}""")
        assertTrue(authErr.contains("GROQ_API_KEY"))

        val rateErr = service.formatHttpError(429, """{"error":{"message":"Rate limit"}}""")
        assertTrue(rateErr.contains("حد الطلبات"))
    }

    @Test
    fun sendChatCompletion_returnsHelpfulFailureWhenKeyIsPlaceholder() = runBlocking {
        val result = service.sendChatCompletion(
            modelId = GroqAiService.DEFAULT_MODEL_ID,
            systemPrompt = "system",
            history = emptyList(),
            userMessage = "مرحباً",
            apiKey = "YOUR_GROQ_API_KEY"
        )
        assertTrue(result is GroqAiResult.Failure)
        val err = (result as GroqAiResult.Failure).errorMessage
        assertTrue(err.contains("GROQ_API_KEY"))
        assertTrue(err.contains("Secrets"))
    }

    @Test
    fun arabicFormatters_formatsMoneyAndPaymentLabelsCleanly() {
        assertEquals("نقدي", ArabicFormatters.paymentMethodLabel("CASH"))
        assertEquals("آجل", ArabicFormatters.paymentMethodLabel("CREDIT"))
        assertEquals("جزئي", ArabicFormatters.paymentMethodLabel("PARTIAL"))
        assertTrue(ArabicFormatters.formatMoney(1250.5, "د.ع").contains("د.ع"))
    }
}
