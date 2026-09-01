package io.legado.app.help.edgeTts

import io.legado.app.help.http.okHttpClient
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Edge TTS 在线朗读客户端
 * 协议: 微软 Edge 浏览器"大声朗读"所用的 WebSocket 接口, 免费无需账号
 */
object EdgeTts {

    /** 引擎标识, 存于 ttsEngine 配置 */
    const val ENGINE_ID = "edgeTTS"

    /** 默认声音 */
    const val DEFAULT_VOICE = "zh-CN-XiaoxiaoNeural"

    private const val WSS_URL =
        "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1"
    private const val TRUSTED_CLIENT_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4"
    private const val CHROMIUM_VERSION = "143.0.3650.75"
    private const val SYNTHESIS_TIMEOUT = 30_000L

    /** 双语混读时片段并行合成并发数 */
    private const val PARALLEL_SYNTHESIS = 4

    /**
     * 合成语音
     * @param text 待合成文本
     * @param voice 声音名称, 见 [VOICES]
     * @param ratePct 语速百分比变化, 0为正常, 正值加快, 负值减慢
     * @return mp3 音频字节, 失败返回 null
     */
    suspend fun synthesize(
        text: String,
        voice: String,
        ratePct: Int
    ): ByteArray? = withContext(IO) {
        if (text.isBlank()) {
            return@withContext null
        }
        withTimeout(SYNTHESIS_TIMEOUT) {
            suspendCancellableCoroutine { cont ->
                val audio = ByteArrayOutputStream()
                var finished = false
                val connectionId = UUID.randomUUID().toString().replace("-", "")
                val wsUrl = buildString {
                    append(WSS_URL)
                    append("?TrustedClientToken=").append(TRUSTED_CLIENT_TOKEN)
                    append("&Sec-MS-GEC=").append(secMsGec())
                    append("&Sec-MS-GEC-Version=1-").append(CHROMIUM_VERSION)
                    append("&ConnectionId=").append(connectionId)
                }
                val request = Request.Builder()
                    .url(wsUrl)
                    .header("Origin", "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold")
                    .header("Cookie", "muid=${UUID.randomUUID().toString().replace("-", "").uppercase()};")
                    .header("Pragma", "no-cache")
                    .header("Cache-Control", "no-cache")
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36" +
                                " (KHTML, like Gecko) Chrome/$CHROMIUM_VERSION Safari/537.36 Edg/$CHROMIUM_VERSION"
                    )
                    .build()
                val webSocket = okHttpClient.newBuilder()
                    .readTimeout(SYNTHESIS_TIMEOUT, TimeUnit.MILLISECONDS)
                    .pingInterval(15, TimeUnit.SECONDS)
                    .build()
                    .newWebSocket(request, object : WebSocketListener() {

                        override fun onOpen(webSocket: WebSocket, response: Response) {
                            val timestamp = wsTimestamp()
                            // 语音合成配置, 指定输出 mp3 格式
                            webSocket.send(
                                "X-Timestamp:$timestamp\r\n" +
                                        "Content-Type:application/json; charset=utf-8\r\n" +
                                        "Path:speech.config\r\n\r\n" +
                                        """{"context":{"synthesis":{"audio":""" +
                                        """{"metadataoptions":{"sentenceBoundaryEnabled":"false",""" +
                                        """"wordBoundaryEnabled":"false"},""" +
                                        """"outputFormat":"audio-24khz-48kbitrate-mono-mp3"}}}}"""
                            )
                            webSocket.send(
                                "X-RequestId:$connectionId\r\n" +
                                        "Content-Type:application/ssml+xml\r\n" +
                                        "X-Timestamp:$timestamp Z\r\n" +
                                        "Path:ssml\r\n\r\n" +
                                        buildSsml(text, voice, ratePct)
                            )
                        }

                        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                            // 二进制帧: 前2字节为header长度(大端), 之后为header文本, 再后为音频数据
                            if (bytes.size < 2) return
                            val headerLength =
                                ((bytes[0].toInt() and 0xFF) shl 8) or (bytes[1].toInt() and 0xFF)
                            val audioStart = headerLength + 2
                            if (bytes.size > audioStart) {
                                audio.write(bytes.toByteArray(), audioStart, bytes.size - audioStart)
                            }
                        }

                        override fun onMessage(webSocket: WebSocket, text: String) {
                            if (text.contains("Path:turn.end")) {
                                finished = true
                                webSocket.close(1000, null)
                                if (cont.isActive) {
                                    cont.resume(audio.toByteArray())
                                }
                            }
                        }

                        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                            if (!finished && cont.isActive) {
                                cont.resume(null)
                            }
                        }

                        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                            // 正常关闭但未收到 turn.end, 返回已收到的部分
                            if (!finished && cont.isActive) {
                                cont.resume(
                                    if (audio.size() > 0) audio.toByteArray() else null
                                )
                            }
                        }
                    })
                cont.invokeOnCancellation {
                    webSocket.cancel()
                }
            }
        }
    }

    /**
     * 计算 DRM 令牌 Sec-MS-GEC
     * 算法: SHA256(时间戳刻度 + TrustedClientToken) 的十六进制大写
     * 注意拼接顺序: 刻度在前, token在后(与官方edge-tts实现一致)
     */
    private fun secMsGec(): String {
        // Unix秒 -> Windows纪元(1601年)偏移
        var ticks = System.currentTimeMillis() / 1000 + 11644473600L
        // 按5分钟取整
        ticks -= ticks % 300
        // 秒转换为100纳秒刻度
        ticks *= 10_000_000L
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$ticks$TRUSTED_CLIENT_TOKEN".toByteArray(Charsets.US_ASCII))
        return digest.joinToString("") { String.format(Locale.US, "%02X", it) }
    }

    private fun wsTimestamp(): String {
        return SimpleDateFormat("EEE MMM dd yyyy HH:mm:ss 'GMT+0000 (Coordinated Universal Time)'", Locale.US)
            .format(Date())
    }

    private fun buildSsml(text: String, voice: String, ratePct: Int): String {
        val lang = voice.substringBeforeLast("-", "zh-CN")
        val escaped = text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
        val rate = if (ratePct >= 0) "+$ratePct%" else "$ratePct%"
        return "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='$lang'>" +
                "<voice name='$voice'>" +
                "<prosody pitch='+0Hz' rate='$rate' volume='+0%'>$escaped</prosody>" +
                "</voice></speak>"
    }

    /**
     * 中英文混读: 根据中文声音性别自动匹配英文声音
     */
    fun defaultEnVoice(zhVoice: String): String {
        val male = zhVoice.contains("Yun") || zhVoice.contains("WanLung")
        return if (male) "en-US-GuyNeural" else "en-US-JennyNeural"
    }

    /** 英文可选声音列表 */
    val EN_VOICES: List<Voice> = listOf(
        Voice("en-US-JennyNeural", "Jenny · 珍妮 · 女声 · 自然(推荐)"),
        Voice("en-US-GuyNeural", "Guy · 盖伊 · 男声 · 自然(推荐)"),
        Voice("en-US-AriaNeural", "Aria · 阿丽亚 · 女声 · 沉稳"),
        Voice("en-US-DavisNeural", "Davis · 戴维斯 · 男声 · 沉稳"),
        Voice("en-US-AnaNeural", "Ana · 安娜 · 女声 · 童声"),
        Voice("en-GB-SoniaNeural", "Sonia · 索尼娅 · 女声 · 英式"),
        Voice("en-GB-RyanNeural", "Ryan · 瑞安 · 男声 · 英式"),
        Voice("en-AU-NatashaNeural", "Natasha · 娜塔莎 · 女声 · 澳式")
    )

    fun enVoiceDisplayName(voice: String): String {
        return EN_VOICES.firstOrNull { it.name == voice }?.display ?: voice
    }

    /** 双语文本片段 */
    data class BilingualSegment(val text: String, val isEnglish: Boolean)

    /**
     * 将文本拆分为中文/英文片段
     * 英文片段: 以字母开头, 可含字母/数字/撇号/连字符, 词间以空白连接(如 "Hello World")
     * 其余(含中文/标点/数字)归为中文片段
     * 优化: 字母数不超过2的英文片段(如 AI/IT/OK)并入中文片段, 相邻同语言片段合并, 减少合成请求次数
     */
    fun splitBilingual(text: String): List<BilingualSegment> {
        val raw = mutableListOf<BilingualSegment>()
        val enRegex = Regex("[A-Za-z][A-Za-z0-9'’\\-]*(?:\\s+[A-Za-z0-9'’\\-]+)*")
        var lastEnd = 0
        for (match in enRegex.findAll(text)) {
            if (match.range.first > lastEnd) {
                raw.add(BilingualSegment(text.substring(lastEnd, match.range.first), false))
            }
            raw.add(BilingualSegment(match.value, true))
            lastEnd = match.range.last + 1
        }
        if (lastEnd < text.length) {
            raw.add(BilingualSegment(text.substring(lastEnd), false))
        }
        val merged = mutableListOf<BilingualSegment>()
        for (segment in raw) {
            val isEn = segment.isEnglish && segment.text.count { it.isLetter() } > 2
            val last = merged.lastOrNull()
            if (last != null && last.isEnglish == isEn) {
                merged[merged.lastIndex] = BilingualSegment(last.text + segment.text, isEn)
            } else {
                merged.add(BilingualSegment(segment.text, isEn))
            }
        }
        return merged
    }

    /**
     * 中英文混读合成
     * 中文片段用中文声音, 英文片段用英文声音, 片段并行合成后按原顺序拼接音频
     * @return mp3 音频字节, 失败返回 null
     */
    suspend fun synthesizeBilingual(
        text: String,
        zhVoice: String,
        enVoice: String,
        ratePct: Int
    ): ByteArray? = withContext(IO) {
        val segments = splitBilingual(text).filter { it.text.isNotBlank() }
        if (segments.isEmpty()) {
            return@withContext null
        }
        //片段并行合成, 限制并发避免触发服务端限流
        val semaphore = Semaphore(PARALLEL_SYNTHESIS)
        val audios = coroutineScope {
            segments.map { segment ->
                async {
                    semaphore.withPermit {
                        val voice = if (segment.isEnglish) enVoice else zhVoice
                        synthesize(segment.text.trim(), voice, ratePct)
                    }
                }
            }.awaitAll()
        }
        val output = ByteArrayOutputStream()
        for (audio in audios) {
            if (audio == null) {
                return@withContext null
            }
            output.write(audio)
        }
        if (output.size() > 0) output.toByteArray() else null
    }

    /** 可选声音列表 */
    data class Voice(val name: String, val display: String)

    val VOICES: List<Voice> = listOf(
        Voice("zh-CN-XiaoxiaoNeural", "晓晓 · 女声 · 温柔(推荐)"),
        Voice("zh-CN-XiaoyiNeural", "晓伊 · 女声 · 甜美"),
        Voice("zh-CN-YunxiNeural", "云希 · 男声 · 阳光"),
        Voice("zh-CN-YunyangNeural", "云扬 · 男声 · 播音"),
        Voice("zh-CN-YunjianNeural", "云健 · 男声 · 浑厚"),
        Voice("zh-CN-YunxiaNeural", "云夏 · 男声 · 少年"),
        Voice("zh-CN-liaoning-XiaobeiNeural", "晓北 · 女声 · 东北"),
        Voice("zh-CN-shaanxi-XiaoniNeural", "晓妮 · 女声 · 陕西"),
        Voice("zh-HK-HiuMaanNeural", "曉曼 · 女声 · 粤语"),
        Voice("zh-HK-WanLungNeural", "雲龍 · 男声 · 粤语"),
        Voice("zh-TW-HsiaoChenNeural", "曉臻 · 女声 · 台湾"),
        Voice("zh-TW-YunJheNeural", "雲哲 · 男声 · 台湾")
    )

    fun voiceDisplayName(voice: String): String {
        return VOICES.firstOrNull { it.name == voice }?.display ?: voice
    }

}
