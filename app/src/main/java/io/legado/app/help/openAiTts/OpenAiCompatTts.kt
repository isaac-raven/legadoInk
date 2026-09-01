package io.legado.app.help.openAiTts

import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.http.await
import io.legado.app.help.http.okHttpClient
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * OpenAI 兼容 TTS 客户端
 * 请求: POST {baseUrl}/audio/speech (Authorization: Bearer {apiKey})
 * 支持阿里 DashScope(qwen3-tts系列) 与自建 OpenAI 兼容服务(微软 VibeVoice、网易有道 Confucius-TTS 等)
 */
object OpenAiCompatTts {

    /** 引擎标识, 存于 ttsEngine 配置 */
    const val DASHSCOPE_ENGINE_ID = "dashscopeTTS"
    const val OPENAI_ENGINE_ID = "openaiTTS"

    /** 阿里 DashScope OpenAI 兼容入口 */
    const val DASHSCOPE_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1"

    /** DashScope 默认模型 */
    const val DASHSCOPE_DEFAULT_MODEL = "qwen3-tts-flash"

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    /**
     * 合成语音
     * @param baseUrl 服务地址, 如 https://dashscope.aliyuncs.com/compatible-mode/v1
     * @param apiKey 密钥(自建服务若无鉴权可留空)
     * @param model 模型名, 如 qwen3-tts-flash / VibeVoice-7B
     * @param voice 音色名
     * @param text 待合成文本
     * @param speed 语速(1.0为正常), 为1.0时不传, 避免部分服务不兼容
     * @return mp3 音频字节, 文本为空返回 null
     */
    suspend fun synthesize(
        baseUrl: String,
        apiKey: String,
        model: String,
        voice: String,
        text: String,
        speed: Float = 1.0f
    ): ByteArray? = withContext(IO) {
        if (text.isBlank()) {
            return@withContext null
        }
        val body = JSONObject().apply {
            put("model", model)
            put("input", text)
            put("voice", voice)
            put("response_format", "mp3")
            if (speed != 1.0f) {
                put("speed", speed.toDouble())
            }
        }
        val requestBuilder = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/audio/speech")
        if (apiKey.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer $apiKey")
        }
        val request = requestBuilder
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        val response = okHttpClient.newBuilder()
            .readTimeout(120, TimeUnit.SECONDS)
            .callTimeout(180, TimeUnit.SECONDS)
            .build()
            .newCall(request).await()
        response.use {
            if (!it.isSuccessful) {
                val err = it.body?.string()?.take(500) ?: ""
                throw NoStackTraceException("TTS服务返回错误(${it.code}): $err")
            }
            val contentType = it.header("Content-Type") ?: ""
            if (contentType.contains("json") || contentType.contains("text")) {
                throw NoStackTraceException("TTS服务返回错误: ${it.body?.string()?.take(500)}")
            }
            it.body?.bytes()
        }
    }

    /** DashScope qwen3-tts 可选音色 */
    val DASHSCOPE_VOICES = listOf(
        "Cherry", "Ethan", "Chelsie", "Jada",
        "Dylan", "Serena", "Sunny", "Peter"
    )

    /** 常见 OpenAI 兼容音色(自建服务可自行输入) */
    val OPENAI_VOICES = listOf(
        "alloy", "echo", "fable", "onyx", "nova", "shimmer"
    )

}
