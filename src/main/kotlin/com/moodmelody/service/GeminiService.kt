package com.moodmelody.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.core5.http.io.entity.StringEntity
import org.apache.hc.core5.http.ContentType

@Service
class GeminiService(
    @Value("\${gemini.api.key}") private val apiKey: String,
    @Value("\${gemini.api.model:gemini-1.5-flash}") private val model: String
) {
    private val mapper = jacksonObjectMapper()

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
        } catch (_: Exception) {
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
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
        val httpClient = HttpClients.createDefault()
        val httpPost = HttpPost(url)

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
            val entity = response.entity ?: return null
            val responseJson = entity.content.bufferedReader().use { it.readText() }
            val parsed: GenerateContentResponse = mapper.readValue(responseJson)
            return parsed.candidates.firstOrNull()
                ?.content?.parts?.firstOrNull()?.text
        }
    }
}

// --- JSON 파싱용 데이터 클래스 ---
data class Part(val text: String?)
data class Content(val role: String?, val parts: List<Part> = emptyList())
data class Candidate(val content: Content)
data class GenerateContentResponse(val candidates: List<Candidate> = emptyList())