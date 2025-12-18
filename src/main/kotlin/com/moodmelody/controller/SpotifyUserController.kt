package com.moodmelody.controller

import com.moodmelody.service.SpotifyUserService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RequestParam

@RestController
@RequestMapping("/api/spotify")
class SpotifyUserController(
    private val spotifyUserService: SpotifyUserService
) {
    @GetMapping("/profile")
    fun profile(authentication: Authentication): ResponseEntity<Map<String, Any>> {
        val profile = spotifyUserService.getCurrentUserProfile(authentication)
        return ResponseEntity.ok(profile)
    }

    @GetMapping("/recent")
    fun recent(authentication: Authentication, @RequestParam(required = false, defaultValue = "20") limit: Int): ResponseEntity<Map<String, Any>> {
        val result = spotifyUserService.getRecentlyPlayed(authentication, limit)
        return ResponseEntity.ok(result)
    }

    @GetMapping("/top/tracks")
    fun topTracks(authentication: Authentication, @RequestParam(required = false, defaultValue = "medium_term") timeRange: String, @RequestParam(required = false, defaultValue = "20") limit: Int): ResponseEntity<Map<String, Any>> {
        val result = spotifyUserService.getTop(authentication, type = "tracks", timeRange = timeRange, limit = limit)
        return ResponseEntity.ok(result)
    }

    @GetMapping("/top/artists")
    fun topArtists(authentication: Authentication, @RequestParam(required = false, defaultValue = "medium_term") timeRange: String, @RequestParam(required = false, defaultValue = "20") limit: Int): ResponseEntity<Map<String, Any>> {
        val result = spotifyUserService.getTop(authentication, type = "artists", timeRange = timeRange, limit = limit)
        return ResponseEntity.ok(result)
    }

    @GetMapping("/stats")
    fun stats(authentication: Authentication?, @RequestParam(defaultValue = "50") limit: Int): Map<String, Any> {
        // 기본값(토큰 없음/데이터 없음 대비)
        val defaults = mapOf(
            "count" to 0,
            "avg_danceability" to 0.0,
            "avg_energy" to 0.0,
            "avg_valence" to 0.0,
            "avg_tempo" to 0.0
        )
        // 인증 세션이 있어도 Spotify 토큰이 없을 수 있음(다른 OAuth 제공자 로그인)
        if (authentication == null) return defaults
        return try {
            val recent = spotifyUserService.getRecentlyPlayed(authentication, limit)
            val items = (recent["items"] as? List<*>) ?: emptyList<Any>()
            val ids = items.mapNotNull {
                val track = (it as? Map<*, *>)?.get("track") as? Map<*, *>
                track?.get("id") as? String
            }.filterNotNull()
            if (ids.isEmpty()) return defaults

            val featuresResp = spotifyUserService.getAudioFeatures(authentication, ids.take(100))
            val features = (featuresResp["audio_features"] as? List<*>) ?: emptyList<Any>()
            var c = 0
            var sumDance = 0.0
            var sumEnergy = 0.0
            var sumValence = 0.0
            var sumTempo = 0.0
            features.forEach { fAny ->
                val f = fAny as? Map<*, *> ?: return@forEach
                (f["danceability"] as? Number)?.toDouble()?.let { sumDance += it }
                (f["energy"] as? Number)?.toDouble()?.let { sumEnergy += it }
                (f["valence"] as? Number)?.toDouble()?.let { sumValence += it }
                (f["tempo"] as? Number)?.toDouble()?.let { sumTempo += it }
                c++
            }
            if (c == 0) return defaults
            mapOf(
                "count" to c,
                "avg_danceability" to sumDance / c,
                "avg_energy" to sumEnergy / c,
                "avg_valence" to sumValence / c,
                "avg_tempo" to sumTempo / c
            )
        } catch (_: Exception) {
            // 토큰 없음/Spotify API 오류 등 모든 예외는 기본값으로 응답
            defaults
        }
    }
}