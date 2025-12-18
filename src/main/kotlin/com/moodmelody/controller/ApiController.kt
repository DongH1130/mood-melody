package com.moodmelody.controller

import com.moodmelody.service.GeminiService
import com.moodmelody.service.SpotifyService
import com.moodmelody.service.SpotifyUserService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.security.core.Authentication
import org.slf4j.LoggerFactory

@RestController
@RequestMapping("/api")
class ApiController(
    private val spotifyService: SpotifyService,
    private val geminiService: GeminiService,
    private val spotifyUserService: SpotifyUserService
) {
    private val log = LoggerFactory.getLogger(ApiController::class.java)
    
    @GetMapping("/search")
    fun searchTracks(@RequestParam query: String): ResponseEntity<Map<String, Any>> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            return ResponseEntity.ok(mapOf(
                "tracks" to emptyList<String>(),
                "error" to "검색어가 비어 있습니다."
            ))
        }
        return try {
            val tracks = spotifyService.searchTracks(trimmed)
            ResponseEntity.ok(mapOf("tracks" to tracks))
        } catch (e: Exception) {
            log.warn("/search 처리 실패: {}", e.message)
            ResponseEntity.ok(mapOf(
                "tracks" to emptyList<String>(),
                "error" to ("검색 실패: " + (e.message ?: "알 수 없는 오류"))
            ))
        }
    }

    @PostMapping("/analyze-mood")
    fun analyzeMood(@RequestBody request: Map<String, String>): ResponseEntity<Map<String, Any?>> {
        val text = request["text"]?.trim().orEmpty()
        if (text.isBlank()) return ResponseEntity.ok(mapOf("error" to "텍스트가 비어 있습니다."))
        return try {
            val moodAnalysis = geminiService.analyzeMood(text)
            ResponseEntity.ok(mapOf("mood" to moodAnalysis))
        } catch (e: Exception) {
            log.warn("/analyze-mood 처리 실패: {}", e.message)
            // 휴리스틱 문장형 대체: 결제 없이도 간단한 설명 제공
            val params = spotifyService.deriveParamsFromText(text)
            val genres = if (params.genres.isNotEmpty()) params.genres.joinToString(", ") else "pop, indie, rock"
            val fallback = "입력한 기분에 맞춰 ${genres} 계열의 곡을 추천할게요."
            ResponseEntity.ok(mapOf("mood" to fallback))
        }
    }

    @PostMapping("/analyze-mood-params")
    fun analyzeMoodParams(@RequestBody request: Map<String, Any>): ResponseEntity<Map<String, Any?>> {
        val text = (request["text"] as? String)?.trim().orEmpty()
        val aiOnly = (request["aiOnly"] as? Boolean)
            ?: ((request["aiOnly"] as? String)?.toBoolean() ?: false)
        if (text.isBlank()) return ResponseEntity.ok(mapOf("error" to "텍스트가 비어 있습니다."))
        return try {
            val params = geminiService.analyzeMoodParams(text)
            if (params == null) {
                if (aiOnly) {
                    // AI만 사용하는 모드에서는 폴백 없이 오류 반환
                    return ResponseEntity.ok(mapOf("error" to "AI 분석 실패"))
                }
                // 하이브리드 모드: 룰 기반 파라미터로 폴백
                val rb = spotifyService.deriveParamsFromText(text)
                ResponseEntity.ok(
                    mapOf(
                        "seed_genres" to rb.genres,
                        "target_valence" to rb.valence,
                        "target_energy" to rb.energy,
                        "target_danceability" to rb.danceability
                    )
                )
            } else {
                if (aiOnly) {
                    // AI 전용 모드: 가드레일/보정 없이 AI 결과 그대로 반환
                    ResponseEntity.ok(
                        mapOf(
                            "seed_genres" to params.seed_genres,
                            "target_valence" to params.target_valence,
                            "target_energy" to params.target_energy,
                            "target_danceability" to params.target_danceability
                        )
                    )
                } else {
                    // 하이브리드 모드: generic일 때 룰 기반으로 보정
                    val rb = spotifyService.deriveParamsFromText(text)
                    val generic = setOf("pop", "indie", "rock")
                    val seedGenres = if (params.seed_genres.isNullOrEmpty()) {
                        rb.genres
                    } else {
                        val normalized = params.seed_genres.map { it.lowercase() }
                        if (normalized.all { generic.contains(it) } && rb.genres.isNotEmpty()) rb.genres else params.seed_genres
                    }
                    val targetValence = params.target_valence ?: rb.valence
                    val targetEnergy = params.target_energy ?: rb.energy
                    val targetDanceability = params.target_danceability ?: rb.danceability
                    ResponseEntity.ok(
                        mapOf(
                            "seed_genres" to seedGenres,
                            "target_valence" to targetValence,
                            "target_energy" to targetEnergy,
                            "target_danceability" to targetDanceability
                        )
                    )
                }
            }
        } catch (e: Exception) {
            log.warn("/analyze-mood-params 처리 실패: {}", e.message)
            if (aiOnly) {
                // AI 전용 모드에서는 예외 시에도 폴백하지 않고 오류 반환
                return ResponseEntity.ok(mapOf("error" to (e.message ?: "AI 분석 실패")))
            }
            // 하이브리드 모드: 예외 시에도 룰 기반으로 안전한 응답 반환
            val rb = spotifyService.deriveParamsFromText(text)
            ResponseEntity.ok(
                mapOf(
                    "seed_genres" to rb.genres,
                    "target_valence" to rb.valence,
                    "target_energy" to rb.energy,
                    "target_danceability" to rb.danceability
                )
            )
        }
    }

    @PostMapping("/recommendations")
    fun getRecommendations(@RequestBody request: Map<String, Any>): ResponseEntity<Map<String, Any>> {
        val seedTracks = request["seedTracks"] as? List<String> ?: emptyList()
        val mood = (request["mood"] as? String).orEmpty()
        
        return try {
            val recommendedTracks = spotifyService.getRecommendations(seedTracks, mood)
            val playlistDescription = try {
                geminiService.generatePlaylistDescription(mood, recommendedTracks)
            } catch (_: Exception) {
                // 간단한 폴백 설명 제공
                val params = spotifyService.deriveParamsFromText(mood)
                val genres = if (params.genres.isNotEmpty()) params.genres.joinToString(", ") else "pop, indie, rock"
                "${genres} 분위기의 선곡으로 구성한 플레이리스트입니다."
            }
            ResponseEntity.ok(
                mapOf(
                    "tracks" to recommendedTracks,
                    "playlistDescription" to playlistDescription
                )
            )
        } catch (e: Exception) {
            log.warn("/recommendations 처리 실패: {}", e.message)
            ResponseEntity.ok(mapOf("error" to (e.message ?: "추천 생성 실패")))
        }
    }

    @PostMapping("/recommendations/advanced")
    fun getRecommendationsAdvanced(
        authentication: Authentication?,
        @RequestBody request: Map<String, Any>
    ): ResponseEntity<Map<String, Any>> {
        val mood = (request["mood"] as? String).orEmpty()
        val usePersonalized = request["usePersonalized"] as? Boolean ?: true
        val params = request["params"] as? Map<String, Any>

        val seedGenres = (params?.get("seed_genres") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        val targetValence = (params?.get("target_valence") as? Number)?.toFloat()
        val targetEnergy = (params?.get("target_energy") as? Number)?.toFloat()
        val targetDanceability = (params?.get("target_danceability") as? Number)?.toFloat()

        return try {
            // seedTracks from user recent/top if available
            val seedTracks: List<String> = if (usePersonalized && authentication != null) {
                val ids = mutableSetOf<String>()
                try {
                    val recent = spotifyUserService.getRecentlyPlayed(authentication, 50)
                    val items = (recent["items"] as? List<Map<String, Any>>) ?: emptyList()
                    items.forEach { item ->
                        val track = item["track"] as? Map<*, *>
                        val id = track?.get("id") as? String
                        if (!id.isNullOrBlank()) ids.add(id)
                    }
                } catch (_: Exception) { /* ignore */ }
                if (ids.size < 3) {
                    try {
                        val top = spotifyUserService.getTop(authentication, type = "tracks", timeRange = "medium_term", limit = 50)
                        val items = (top["items"] as? List<Map<String, Any>>) ?: emptyList()
                        items.forEach { item ->
                            val id = item["id"] as? String
                            if (!id.isNullOrBlank()) ids.add(id)
                        }
                    } catch (_: Exception) { /* ignore */ }
                }
                ids.take(5).toList()
            } else emptyList()

            val strictParams = (request["strictParams"] as? Boolean) ?: false
            var effectiveGenres = if (seedGenres.isNotEmpty()) seedGenres else emptyList()
            if (strictParams && seedTracks.isEmpty() && effectiveGenres.isEmpty()) {
                throw IllegalArgumentException("AI 파라미터 없음: 장르/시드 트랙이 비어있음")
            }

            val tracks = spotifyService.getRecommendationsAdvanced(
                seedTracks = seedTracks,
                genres = effectiveGenres,
                targetValence = targetValence,
                targetEnergy = targetEnergy,
                targetDanceability = targetDanceability,
                strict = strictParams
            )

            val desc = try {
                geminiService.generatePlaylistDescription(
                    mood,
                    tracks.take(5).map { "${it["name"]} - ${it["artists"]}" }
                )
            } catch (_: Exception) {
                val rb = spotifyService.deriveParamsFromText(mood)
                val genres = if (rb.genres.isNotEmpty()) rb.genres.joinToString(", ") else "pop, indie, rock"
                "${genres} 분위기의 선곡으로 구성한 플레이리스트입니다."
            }

            ResponseEntity.ok(
                mapOf(
                    "tracks" to tracks,
                    "playlistDescription" to desc,
                    "seedTracksUsed" to seedTracks
                )
            )
        } catch (e: Exception) {
            log.warn("/recommendations/advanced 처리 실패: {}", e.message)
            ResponseEntity.ok(mapOf("error" to (e.message ?: "추천 생성 실패")))
        }
    }
    
    @GetMapping("/playlist/{playlistId}")
    fun getPlaylistTracks(@PathVariable playlistId: String): ResponseEntity<Map<String, Any>> {
        val tracks = spotifyService.getPlaylistTracks(playlistId)
        return ResponseEntity.ok(mapOf("tracks" to tracks))
    }
}
