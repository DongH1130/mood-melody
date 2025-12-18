package com.moodmelody.controller

import com.moodmelody.service.GeminiService
import com.moodmelody.service.SpotifyService
import com.moodmelody.service.SpotifyUserService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.security.core.Authentication

@RestController
@RequestMapping("/api")
class ApiController(
    private val spotifyService: SpotifyService,
    private val geminiService: GeminiService,
    private val spotifyUserService: SpotifyUserService
) {
    
    @GetMapping("/search")
    fun searchTracks(@RequestParam query: String): ResponseEntity<Map<String, Any>> {
        val tracks = spotifyService.searchTracks(query)
        return ResponseEntity.ok(mapOf("tracks" to tracks))
    }

    @PostMapping("/analyze-mood")
    fun analyzeMood(@RequestBody request: Map<String, String>): ResponseEntity<Map<String, String>> {
        val text = request["text"] ?: throw IllegalArgumentException("Text is required")
        val moodAnalysis = geminiService.analyzeMood(text)
        return ResponseEntity.ok(mapOf("mood" to moodAnalysis))
    }

    @PostMapping("/analyze-mood-params")
    fun analyzeMoodParams(@RequestBody request: Map<String, String>): ResponseEntity<Map<String, Any?>> {
        val text = request["text"] ?: throw IllegalArgumentException("Text is required")
        val params = geminiService.analyzeMoodParams(text)
        return ResponseEntity.ok(
            mapOf(
                "seed_genres" to (params?.seed_genres ?: emptyList<String>()),
                "target_valence" to params?.target_valence,
                "target_energy" to params?.target_energy,
                "target_danceability" to params?.target_danceability
            )
        )
    }

    @PostMapping("/recommendations")
    fun getRecommendations(@RequestBody request: Map<String, Any>): ResponseEntity<Map<String, Any>> {
        val seedTracks = request["seedTracks"] as? List<String> ?: emptyList()
        val mood = request["mood"] as? String ?: ""
        
        val recommendedTracks = spotifyService.getRecommendations(seedTracks, mood)
        val playlistDescription = geminiService.generatePlaylistDescription(mood, recommendedTracks)
        
        return ResponseEntity.ok(mapOf(
            "tracks" to recommendedTracks,
            "playlistDescription" to playlistDescription
        ))
    }

    @PostMapping("/recommendations/advanced")
    fun getRecommendationsAdvanced(
        authentication: Authentication?,
        @RequestBody request: Map<String, Any>
    ): ResponseEntity<Map<String, Any>> {
        val mood = request["mood"] as? String ?: ""
        val usePersonalized = request["usePersonalized"] as? Boolean ?: true
        val params = request["params"] as? Map<String, Any>

        val seedGenres = (params?.get("seed_genres") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        val targetValence = (params?.get("target_valence") as? Number)?.toFloat()
        val targetEnergy = (params?.get("target_energy") as? Number)?.toFloat()
        val targetDanceability = (params?.get("target_danceability") as? Number)?.toFloat()

        // seedTracks from user recent/top if available
        val seedTracks: List<String> = if (usePersonalized && authentication != null) {
            val ids = mutableSetOf<String>()
            try {
                val recent = spotifyUserService.getRecentlyPlayed(authentication, 50)
                val items = (recent["items"] as? List<Map<String, Any>>) ?: emptyList()
                items.forEach { it["track"]?.let { t ->
                    val id = (t as Map<*, *>)["id"] as? String
                    if (!id.isNullOrBlank()) ids.add(id)
                } }
            } catch (_: Exception) { /* ignore */ }
            if (ids.size < 3) {
                try {
                    val top = spotifyUserService.getTop(authentication, type = "tracks", timeRange = "medium_term", limit = 50)
                    val items = (top["items"] as? List<Map<String, Any>>) ?: emptyList()
                    items.forEach { it["id"]?.let { id -> if (id is String && id.isNotBlank()) ids.add(id) } }
                } catch (_: Exception) { /* ignore */ }
            }
            ids.take(5).toList()
        } else emptyList()

        val effectiveGenres = if (seedGenres.isNotEmpty()) seedGenres else emptyList()
        val tracks = spotifyService.getRecommendationsAdvanced(
            seedTracks = seedTracks,
            genres = effectiveGenres,
            targetValence = targetValence,
            targetEnergy = targetEnergy,
            targetDanceability = targetDanceability
        )

        val desc = geminiService.generatePlaylistDescription(
            mood,
            tracks.take(5).map { "${it["name"]} - ${it["artists"]}" }
        )

        return ResponseEntity.ok(
            mapOf(
                "tracks" to tracks,
                "playlistDescription" to desc,
                "seedTracksUsed" to seedTracks
            )
        )
    }
    
    @GetMapping("/playlist/{playlistId}")
    fun getPlaylistTracks(@PathVariable playlistId: String): ResponseEntity<Map<String, Any>> {
        val tracks = spotifyService.getPlaylistTracks(playlistId)
        return ResponseEntity.ok(mapOf("tracks" to tracks))
    }
}
