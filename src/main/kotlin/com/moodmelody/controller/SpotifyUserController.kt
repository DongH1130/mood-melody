package com.moodmelody.controller

import com.moodmelody.service.SpotifyUserService
import org.springframework.http.ResponseEntity
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.PathVariable

// 로깅 및 OAuth2 인증 타입 체크를 위한 추가 import
import org.slf4j.LoggerFactory
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken

@RestController
@RequestMapping("/api/spotify")
class SpotifyUserController(
    private val spotifyUserService: SpotifyUserService
) {
    // 로거 추가
    private val log = LoggerFactory.getLogger(SpotifyUserController::class.java)

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
        val defaults = mapOf(
            "count" to 0,
            "avg_danceability" to 0.0,
            "avg_energy" to 0.0,
            "avg_valence" to 0.0,
            "avg_tempo" to 0.0
        )
        // 인증 객체가 없거나, OAuth2가 아니거나, Spotify 로그인 세션이 아니면 바로 기본값 응답
        if (authentication == null) return defaults
        if (authentication !is OAuth2AuthenticationToken || authentication.authorizedClientRegistrationId != "spotify") {
            return defaults
        }

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
                val dance = (f["danceability"] as? Number)?.toDouble()
                val energy = (f["energy"] as? Number)?.toDouble()
                val valence = (f["valence"] as? Number)?.toDouble()
                val tempo = (f["tempo"] as? Number)?.toDouble()
                if (dance != null && energy != null && valence != null && tempo != null) {
                    c++
                    sumDance += dance
                    sumEnergy += energy
                    sumValence += valence
                    sumTempo += tempo
                }
            }
            if (c == 0) return defaults
            mapOf(
                "count" to c,
                "avg_danceability" to sumDance / c,
                "avg_energy" to sumEnergy / c,
                "avg_valence" to sumValence / c,
                "avg_tempo" to sumTempo / c
            )
        } catch (e: Exception) {
            log.warn("[/api/spotify/stats] Fallback to defaults due to error: {}", e.message, e)
            defaults
        }
    }

    // 플레이리스트 저장 요청 바디
    data class SavePlaylistRequest(
        val name: String? = null,
        val description: String? = null,
        val public: Boolean? = null,
        val trackUris: List<String>? = null
    )

    @PostMapping("/playlist")
    fun savePlaylist(authentication: Authentication?, @RequestBody req: SavePlaylistRequest): ResponseEntity<Map<String, Any>> {
        // 인증 확인: Spotify OAuth2 세션 필요
        if (authentication == null || authentication !is OAuth2AuthenticationToken || authentication.authorizedClientRegistrationId != "spotify") {
            return ResponseEntity.status(401).body(mapOf("error" to "Spotify 로그인 필요"))
        }
        val uris = (req.trackUris ?: emptyList()).filter { it.isNotBlank() }
        if (uris.isEmpty()) {
            return ResponseEntity.badRequest().body(mapOf("error" to "trackUris 비어 있음"))
        }
        val name = (req.name ?: "Mood Melody 추천").ifBlank { "Mood Melody 추천" }
        val description = req.description ?: ""
        val isPublic = req.public ?: false
        return try {
            val playlistId = spotifyUserService.createPlaylist(authentication, name, description, isPublic)
            spotifyUserService.addTracksToPlaylist(authentication, playlistId, uris)
            ResponseEntity.ok(mapOf("id" to playlistId))
        } catch (e: Exception) {
            log.warn("플레이리스트 저장 실패: {}", e.message, e)
            ResponseEntity.status(500).body(mapOf("error" to (e.message ?: "플레이리스트 저장 실패")))
        }
    }

    @GetMapping("/playlists")
    fun getMyPlaylists(
        authentication: Authentication,
        @RequestParam(required = false, defaultValue = "20") limit: Int,
        @RequestParam(required = false, defaultValue = "0") offset: Int
    ): ResponseEntity<Any> {
        return try {
            val result = spotifyUserService.getUserPlaylists(authentication, limit, offset)
            ResponseEntity.ok(result)
        } catch (e: Exception) {
            log.error("Failed to get user playlists", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf("error" to (e.message ?: "unknown")))
        }
    }

    @GetMapping("/playlist/{playlistId}/tracks")
    fun getPlaylistTracks(
        authentication: Authentication,
        @PathVariable playlistId: String,
        @RequestParam(required = false, defaultValue = "100") limit: Int
    ): ResponseEntity<Any> {
        return try {
            val result = spotifyUserService.getPlaylistItems(authentication, playlistId, limit)
            ResponseEntity.ok(result)
        } catch (e: Exception) {
            log.error("Failed to get playlist items", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf("error" to (e.message ?: "unknown")))
        }
    }
}