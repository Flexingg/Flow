package com.flow.todo.coach.data

import com.flow.todo.coach.LlmProvider
import com.flow.todo.coach.WireFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class LlmException(val kind: Kind, message: String) : Exception(message) {
    enum class Kind { NO_KEY, CONFIG, AUTH, RATE_LIMIT, SERVER, NETWORK, BAD_RESPONSE }
}

data class LlmRequest(
    val provider: LlmProvider,
    val baseUrl: String,
    val model: String,
    val apiKey: String,
    val system: String,
    val user: String,
    val maxOutputTokens: Int = 400
)

/**
 * Minimal bring-your-own-key client. No SDKs: three small adapters over HttpURLConnection so
 * the app stays light and the user's key only ever goes to the endpoint they chose.
 * The API key is sent only in a header and is never logged or included in error messages.
 */
object LlmClient {
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 45_000
    private const val ANTHROPIC_VERSION = "2023-06-01"

    suspend fun complete(request: LlmRequest): String = withContext(Dispatchers.IO) {
        if (request.apiKey.isBlank()) throw LlmException(LlmException.Kind.NO_KEY, "No API key saved for ${request.provider.label}")
        if (request.model.isBlank()) throw LlmException(LlmException.Kind.CONFIG, "Model name is empty")
        val base = request.baseUrl.trim().trimEnd('/')
        if (!base.startsWith("https://")) throw LlmException(LlmException.Kind.CONFIG, "Base URL must start with https://")

        val text = when (request.provider.wireFormat) {
            WireFormat.ANTHROPIC -> callAnthropic(request, base)
            WireFormat.GEMINI -> callGemini(request, base)
            WireFormat.OPENAI_COMPATIBLE -> callOpenAiCompatible(request, base)
        }
        if (text.isBlank()) {
            throw LlmException(
                LlmException.Kind.BAD_RESPONSE,
                "The model returned an empty reply (reasoning models can spend the whole budget thinking - try a non-reasoning model)"
            )
        }
        text.trim()
    }

    // ---------------- Anthropic Messages API ----------------

    private fun callAnthropic(r: LlmRequest, base: String): String {
        val body = JSONObject()
            .put("model", r.model)
            .put("max_tokens", r.maxOutputTokens)
            .put("system", r.system)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", r.user)))
        val json = post(
            "$base/v1/messages",
            mapOf("x-api-key" to r.apiKey, "anthropic-version" to ANTHROPIC_VERSION),
            body
        )
        val content = json.optJSONArray("content") ?: throw badResponse()
        return buildString {
            for (i in 0 until content.length()) {
                val block = content.optJSONObject(i) ?: continue
                if (block.optString("type") == "text") append(block.optString("text"))
            }
        }
    }

    // ---------------- Google Gemini generateContent ----------------

    private fun callGemini(r: LlmRequest, base: String): String {
        val generationConfig = JSONObject().put("maxOutputTokens", maxOf(r.maxOutputTokens, 1024))
        // 2.5 Flash "thinks" by default and thinking tokens count against the output budget.
        if (r.model.contains("flash", ignoreCase = true)) {
            generationConfig.put("thinkingConfig", JSONObject().put("thinkingBudget", 0))
        }
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", r.system))))
            .put(
                "contents",
                JSONArray().put(
                    JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", r.user)))
                )
            )
            .put("generationConfig", generationConfig)
        val model = URLEncoder.encode(r.model, "UTF-8")
        val json = post("$base/v1beta/models/$model:generateContent", mapOf("x-goog-api-key" to r.apiKey), body)

        val blocked = json.optJSONObject("promptFeedback")?.optString("blockReason").orEmpty()
        if (blocked.isNotBlank()) throw LlmException(LlmException.Kind.BAD_RESPONSE, "Gemini blocked the prompt ($blocked)")
        val parts = json.optJSONArray("candidates")?.optJSONObject(0)
            ?.optJSONObject("content")?.optJSONArray("parts") ?: throw badResponse()
        return buildString {
            for (i in 0 until parts.length()) append(parts.optJSONObject(i)?.optString("text").orEmpty())
        }
    }

    // ---------------- OpenAI-compatible chat completions ----------------

    private fun callOpenAiCompatible(r: LlmRequest, base: String): String {
        val tokenField = if (r.provider == LlmProvider.OPENAI) "max_completion_tokens" else "max_tokens"
        val body = JSONObject()
            .put("model", r.model)
            .put(tokenField, r.maxOutputTokens)
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", r.system))
                    .put(JSONObject().put("role", "user").put("content", r.user))
            )
        val json = post("$base/chat/completions", mapOf("Authorization" to "Bearer ${r.apiKey}"), body)
        val message = json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message") ?: throw badResponse()
        return message.optString("content", "").let { if (it == "null") "" else it }
    }

    // ---------------- HTTP ----------------

    private fun post(url: String, headers: Map<String, String>, body: JSONObject): JSONObject {
        val conn = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (e: IOException) {
            throw LlmException(LlmException.Kind.CONFIG, "Invalid URL")
        }
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json")
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val raw = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw httpError(code, raw)
            return try {
                JSONObject(raw)
            } catch (e: JSONException) {
                throw badResponse()
            }
        } catch (e: LlmException) {
            throw e
        } catch (e: IOException) {
            throw LlmException(LlmException.Kind.NETWORK, "Network error: ${e.javaClass.simpleName}")
        } finally {
            conn.disconnect()
        }
    }

    private fun httpError(code: Int, raw: String): LlmException {
        val detail = runCatching {
            val obj = JSONObject(raw)
            // Anthropic/OpenAI: {"error":{"message":..}}  Gemini: {"error":{"message":..}}
            obj.optJSONObject("error")?.optString("message") ?: obj.optString("message")
        }.getOrNull().orEmpty().take(200)
        val suffix = if (detail.isNotBlank()) ": $detail" else ""
        return when (code) {
            400 -> LlmException(LlmException.Kind.CONFIG, "Request rejected (400)$suffix")
            401, 403 -> LlmException(LlmException.Kind.AUTH, "API key rejected ($code)$suffix")
            404 -> LlmException(LlmException.Kind.CONFIG, "Model or endpoint not found (404)$suffix")
            429 -> LlmException(LlmException.Kind.RATE_LIMIT, "Rate limited or out of credit (429)$suffix")
            in 500..599 -> LlmException(LlmException.Kind.SERVER, "Provider error ($code)$suffix")
            else -> LlmException(LlmException.Kind.BAD_RESPONSE, "HTTP $code$suffix")
        }
    }

    private fun badResponse() = LlmException(LlmException.Kind.BAD_RESPONSE, "Unexpected response from provider")
}
