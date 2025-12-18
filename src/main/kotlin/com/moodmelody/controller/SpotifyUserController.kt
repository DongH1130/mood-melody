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

    @PostMapping("/playlist")
    fun createPlaylist(
        authentication: Authentication,
        @RequestBody request: Map<String, Any>
    ): ResponseEntity<Map<String, Any>> {
        val name = request["name"] as? String ?: "Mood Melody 추천"
        val description = request["description"] as? String ?: "AI 감정 추천으로 생성된 플레이리스트"
        val public = request["public"] as? Boolean ?: false
        val uris = (request["trackUris"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()

        val playlistId = spotifyUserService.createPlaylist(authentication, name, description, public)
        if (uris.isNotEmpty()) {
            spotifyUserService.addTracksToPlaylist(authentication, playlistId, uris)
        }
        return ResponseEntity.ok(mapOf("playlistId" to playlistId))
    }

    @GetMapping("/top/artists")
    fun topArtists(authentication: Authentication, @RequestParam(required = false, defaultValue = "medium_term") timeRange: String, @RequestParam(required = false, defaultValue = "20") limit: Int): ResponseEntity<Map<String, Any>> {
        val result = spotifyUserService.getTop(authentication, type = "artists", timeRange = timeRange, limit = limit)
        return ResponseEntity.ok(result)
    }

    @GetMapping("/stats")
    fun stats(authentication: Authentication?, @RequestParam(defaultValue = "50") limit: Int): Map<String, Any> {
        if (authentication == null) throw IllegalStateException("Authentication required")
        val recent = spotifyUserService.getRecentlyPlayed(authentication, limit)
        val items = (recent["items"] as? List<*>) ?: emptyList<Any>()
        val ids = items.mapNotNull {
            val track = (it as? Map<*, *>)?.get("track") as? Map<*, *>
            track?.get("id") as? String
        }.filterNotNull()
        if (ids.isEmpty()) {
            return mapOf(
                "count" to 0,
                "avg_danceability" to 0.0,
                "avg_energy" to 0.0,
                "avg_valence" to 0.0,
                "avg_tempo" to 0.0
            )
        }
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
            c += 1
        }
        val denom = if (c == 0) 1 else c
        return mapOf(
            "count" to c,
            "avg_danceability" to sumDance / denom,
            "avg_energy" to sumEnergy / denom,
            "avg_valence" to sumValence / denom,
            "avg_tempo" to sumTempo / denom
        )
    }
}