package com.moodmelody.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.core5.http.io.entity.StringEntity
import org.apache.hc.core5.http.ContentType
import org.slf4j.LoggerFactory

@Service
class GeminiService(
    @Value("\${gemini.api.key:\${gemini.key:}}") private val apiKey: String,
    @Value("\${gemini.api.model:\${gemini.model:gemini-2.0-flash}}") private val model: String,
    @Value("\${ai.provider:gemini}") private val provider: String,
    @Value("\${cloudflare.api.token:}") private val cfApiToken: String,
    @Value("\${cloudflare.account.id:}") private val cfAccountId: String,
    @Value("\${cloudflare.model:@cf/meta/llama-3.1-8b-instruct}") private val cfModel: String,
    @Value("\${openrouter.api.key:\${openrouter.key:}}") private val orApiKey: String,
    @Value("\${openrouter.model:openai/gpt-4o-mini}") private val orModel: String,
    @Value("\${openrouter.base:https://openrouter.ai/api}") private val orBase: String
) {
    private val mapper = jacksonObjectMapper()
    private val log = LoggerFactory.getLogger(GeminiService::class.java)

    fun analyzeMood(text: String): String {
        val prompt = """
            너는 기분을 분석하고 그에 맞는 음악을 추천해주는 AI야.
            사용자의 기분을 분석하고, 그에 어울리는 음악 장르나 분위기를 한 문장으로 알려줘.
            사용자 입력: $text
        """.trimIndent()
        val responseText = generateContent(prompt)
        return responseText ?: "분석 결과를 생성하지 못했습니다."
    }

    data class MoodParams(
        val seed_genres: List<String> = emptyList(),
        val target_valence: Float? = null,
        val target_energy: Float? = null,
        val target_danceability: Float? = null
    )

    fun analyzeMoodParams(text: String): MoodParams? {
        val prompt = """
            너는 음악 추천용 파라미터만 JSON으로 반환하는 엔진이다.
            아래 규칙을 반드시 지켜라:
            - 출력은 오직 JSON 한 덩어리만, 설명/코드블록 금지.
            - 키 이름은 seed_genres, target_valence, target_energy, target_danceability.
            - seed_genres는 Spotify 장르 슬러그 배열(pop, rock, edm, ambient 등).
            - 각 target_* 값은 0.0~1.0 사이의 숫자 또는 null.

            사용자 기분 텍스트: "$text"
            JSON만 출력해.
        """.trimIndent()
        val response = generateContent(prompt) ?: return null
        return try {
            mapper.readValue<MoodParams>(response)
        } catch (e: Exception) {
            log.warn("Gemini JSON 파싱 실패: {}", e.message)
            null
        }
    }

    fun generatePlaylistDescription(mood: String, tracks: List<String>): String {
        val prompt = """
            너는 플레이리스트 설명을 생성하는 AI야.
            주어진 기분과 트랙 목록을 바탕으로 매력적인 플레이리스트 설명을 2-3문장으로 생성해줘.
            기분: $mood
            트랙 목록(일부): ${tracks.take(5).joinToString(", ")}
        """.trimIndent()
        val responseText = generateContent(prompt)
        return responseText ?: "플레이리스트 설명을 생성하지 못했습니다."
    }

    private fun generateContent(userText: String): String? {
        // 공급자 스위치: OpenRouter 우선
        if (provider.equals("openrouter", ignoreCase = true)) {
            return generateContentViaOpenRouter(userText)
        }
        try {
            if (apiKey.isBlank()) {
                log.warn("Gemini API 키가 설정되지 않았습니다. 응답을 건너뜁니다.")
                return null
            }
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
            val httpClient = HttpClients.createDefault()
            val httpPost = HttpPost(url)
            httpPost.addHeader("Content-Type", "application/json")
            httpPost.addHeader("Accept", "application/json")

            val body = mapOf(
                "contents" to listOf(
                    mapOf(
                        "role" to "user",
                        "parts" to listOf(mapOf("text" to userText))
                    )
                )
            )

            val json = mapper.writeValueAsString(body)
            httpPost.entity = StringEntity(json, ContentType.APPLICATION_JSON)

            httpClient.execute(httpPost).use { response ->
                val status = response.code
                val responseJson = response.entity?.content?.bufferedReader()?.use { it.readText() } ?: run {
                    log.warn("Gemini 응답 본문이 비어 있습니다. status={}", status)
                    return null
                }
                if (status !in 200..299) {
                    log.warn("Gemini HTTP 오류 status={} body={}", status, responseJson.take(300))
                    return null
                }
                return try {
                    val parsed: GenerateContentResponse = mapper.readValue(responseJson)
                    parsed.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
                } catch (e: Exception) {
                    log.warn("Gemini 응답 파싱 오류: {}", e.message)
                    null
                }
            }
        } catch (e: Exception) {
            log.warn("Gemini 호출 실패: {}", e.message)
            return null
        }
    }

    // OpenRouter Chat Completions 호출
    private fun generateContentViaOpenRouter(userText: String): String? {
        try {
            if (orApiKey.isBlank()) {
                log.warn("OpenRouter API 키가 설정되지 않았습니다. 응답을 건너뜁니다.")
                return null
            }
            val url = "$orBase/v1/chat/completions"
            val httpClient = HttpClients.createDefault()
            val httpPost = HttpPost(url)
            httpPost.addHeader("Content-Type", "application/json")
            httpPost.addHeader("Accept", "application/json")
            httpPost.addHeader("Authorization", "Bearer $orApiKey")
            // 선택 헤더: 서비스 소개용 (없어도 동작)
            httpPost.addHeader("X-Title", "Mood Melody")

            val body = mapOf(
                "model" to orModel,
                "messages" to listOf(
                    mapOf("role" to "system", "content" to "너는 음악 추천과 감정 분석을 돕는 어시스턴트야."),
                    mapOf("role" to "user", "content" to userText)
                )
            )
            val json = mapper.writeValueAsString(body)
            httpPost.entity = StringEntity(json, ContentType.APPLICATION_JSON)

            httpClient.execute(httpPost).use { response ->
                val status = response.code
                val responseJson = response.entity?.content?.bufferedReader()?.use { it.readText() } ?: run {
                    log.warn("OpenRouter 응답 본문이 비어 있습니다. status={}", status)
                    return null
                }
                if (status !in 200..299) {
                    log.warn("OpenRouter HTTP 오류 status={} body={}", status, responseJson.take(300))
                    return null
                }
                return try {
                    val parsed: OpenRouterChatCompletionResponse = mapper.readValue(responseJson)
                    parsed.choices.firstOrNull()?.message?.content
                } catch (e: Exception) {
                    log.warn("OpenRouter 응답 파싱 오류: {}", e.message)
                    null
                }
            }
        } catch (e: Exception) {
            log.warn("OpenRouter 호출 실패: {}", e.message)
            return null
        }
    }
}

// --- JSON 파싱용 데이터 클래스 ---
data class Part(val text: String?)
data class Content(val role: String?, val parts: List<Part> = emptyList())
data class Candidate(val content: Content)
data class GenerateContentResponse(val candidates: List<Candidate> = emptyList())
// --- OpenRouter 응답 파싱용 데이터 클래스 ---
data class OrMessage(val role: String?, val content: String?)
data class OrChoice(val index: Int? = null, val message: OrMessage? = null)
data class OpenRouterChatCompletionResponse(val choices: List<OrChoice> = emptyList())