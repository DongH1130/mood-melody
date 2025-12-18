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
            - 출력은 오직 JSON 한 덩어리만, 설명/코드블록/마크다운 금지.
            - 키 이름은 seed_genres, target_valence, target_energy, target_danceability.
            - seed_genres는 Spotify 장르 슬러그 배열(pop, rock, edm, ambient 등).
            - 각 target_* 값은 0.0~1.0 사이의 숫자 또는 null.

            사용자 기분 텍스트: "$text"
            JSON만 출력해.
        """.trimIndent()
        var response = generateContent(prompt)
        if (response == null) {
            // 1차: 기본 공급자 실패 시 OpenRouter로 폴백
            response = generateContentViaOpenRouter(prompt)
            if (response == null) return null
        }
        // 1차 파싱 시도
        try {
            return mapper.readValue<MoodParams>(response)
        } catch (e: Exception) {
            log.warn("Gemini JSON 파싱 실패(원문 유지): {}", e.message)
        }
        // 1.5차: 기본 공급자가 응답을 줬지만 JSON 파싱 실패 시 OpenRouter 재시도
        run {
            val orAttempt = generateContentViaOpenRouter(prompt)
            if (orAttempt != null) {
                val ostripped = orAttempt
                    .replace("```json", "")
                    .replace("```", "")
                    .trim()
                val ostart = ostripped.indexOf('{')
                val oend = ostripped.lastIndexOf('}')
                if (ostart >= 0 && oend > ostart) {
                    val oslice = ostripped.substring(ostart, oend + 1)
                    try {
                        return mapper.readValue<MoodParams>(oslice)
                    } catch (e: Exception) {
                        log.warn("OpenRouter JSON 파싱 실패(1차): {}", e.message)
                    }
                }
            }
        }
        // 2차: 코드 펜스/마크다운 제거 및 JSON 슬라이스 추출
        val stripped = response
            .replace("```json", "")
            .replace("```", "")
            .trim()
        val start = stripped.indexOf('{')
        val end = stripped.lastIndexOf('}')
        if (start >= 0 && end > start) {
            val slice = stripped.substring(start, end + 1)
            try {
                return mapper.readValue<MoodParams>(slice)
            } catch (e: Exception) {
                log.warn("Gemini JSON 파싱 실패(정제 후): {}", e.message)
            }
        }
        // 3차: 재프롬프트 (스트릭트 JSON 및 예시 포함)
        val retryPrompt = """
            너는 음악 추천용 파라미터만 JSON으로 반환하는 엔진이다.
            오직 한 개의 JSON 객체만 출력해. 설명/마크다운/코드펜스 모두 금지.
            키: seed_genres (배열), target_valence (0.0~1.0), target_energy (0.0~1.0), target_danceability (0.0~1.0)
            예시: {"seed_genres":["pop","dance"],"target_valence":0.9,"target_energy":0.8,"target_danceability":0.8}
            사용자 기분 텍스트: "$text"
        """.trimIndent()
        var retry = generateContent(retryPrompt)
        if (retry == null) {
            retry = generateContentViaOpenRouter(retryPrompt)
        }
        if (retry != null) {
            val rstripped = retry
                .replace("```json", "")
                .replace("```", "")
                .trim()
            val rstart = rstripped.indexOf('{')
            val rend = rstripped.lastIndexOf('}')
            if (rstart >= 0 && rend > rstart) {
                val rslice = rstripped.substring(rstart, rend + 1)
                try {
                    return mapper.readValue<MoodParams>(rslice)
                } catch (e: Exception) {
                    log.warn("Gemini JSON 파싱 실패(재프롬프트 후): {}", e.message)
                }
            }
            // 3.5차: 재프롬프트 결과도 파싱 실패 시 OpenRouter 재시도
            val orRetry = generateContentViaOpenRouter(retryPrompt)
            if (orRetry != null) {
                val orStripped = orRetry
                    .replace("```json", "")
                    .replace("```", "")
                    .trim()
                val ors = orStripped.indexOf('{')
                val ore = orStripped.lastIndexOf('}')
                if (ors >= 0 && ore > ors) {
                    val orSlice = orStripped.substring(ors, ore + 1)
                    try {
                        return mapper.readValue<MoodParams>(orSlice)
                    } catch (e: Exception) {
                        log.warn("OpenRouter JSON 파싱 실패(재프롬프트): {}", e.message)
                    }
                }
            }
        }
        return null
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
                    mapOf("role" to "system", "content" to "너는 음악 추천과 감정 분석을 돕는 어시스턴트야. 반드시 순수 JSON으로만 응답해."),
                    mapOf("role" to "user", "content" to userText)
                ),
                "temperature" to 0.2,
                "response_format" to mapOf("type" to "json_object")
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
    // --- AI provider 상태 확인용 ---
    fun aiStatus(): Map<String, Any> {
        return mapOf(
            "provider" to provider,
            "hasGeminiKey" to (apiKey.isNotBlank()),
            "hasOpenRouterKey" to (orApiKey.isNotBlank()),
            "geminiModel" to model,
            "openrouterModel" to orModel
        )
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