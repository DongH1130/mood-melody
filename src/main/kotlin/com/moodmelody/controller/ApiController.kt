package com.moodmelody.controller

import com.moodmelody.service.GeminiService
import com.moodmelody.service.SpotifyService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api")
class ApiController(
    private val spotifyService: SpotifyService,
    private val geminiService: GeminiService
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
    
    @GetMapping("/playlist/{playlistId}")
    fun getPlaylistTracks(@PathVariable playlistId: String): ResponseEntity<Map<String, Any>> {
        val tracks = spotifyService.getPlaylistTracks(playlistId)
        return ResponseEntity.ok(mapOf("tracks" to tracks))
    }
}
