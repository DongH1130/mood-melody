package com.moodmelody.service

import se.michaelthelin.spotify.SpotifyApi
import se.michaelthelin.spotify.model_objects.specification.Paging
import se.michaelthelin.spotify.model_objects.specification.PlaylistTrack
import se.michaelthelin.spotify.model_objects.specification.Track
import se.michaelthelin.spotify.requests.data.playlists.GetPlaylistsItemsRequest
import org.springframework.stereotype.Service
import jakarta.annotation.PostConstruct

@Service
class SpotifyService(
    private val spotifyApi: SpotifyApi
) {
    
    @PostConstruct
    fun init() {
        try {
            val clientCredentialsRequest = spotifyApi.clientCredentials().build()
            val creds = clientCredentialsRequest.execute()
            spotifyApi.accessToken = creds.accessToken
        } catch (e: Exception) {
            // 초기화 실패 시 애플리케이션 기동을 막지 않음
            println("[Spotify] 초기화 실패: ${e.message}. 유효한 클라이언트 자격 증명을 설정하세요.")
        }
    }

    fun searchTracks(query: String): List<String> {
        try {
            val searchTracksRequest = spotifyApi.searchTracks(query).build()
            val trackPaging = searchTracksRequest.execute()
            return trackPaging.items.map { "${it.name} - ${it.artists[0].name}" }
        } catch (e: Exception) {
            throw RuntimeException("Error searching tracks", e)
        }
    }

    data class RecommendationParams(
        val genres: List<String> = emptyList(),
        val valence: Float? = null,
        val energy: Float? = null,
        val danceability: Float? = null
    )

    private fun deriveParams(mood: String): RecommendationParams {
        val m = mood.lowercase()
        fun any(vararg keys: String) = keys.any { m.contains(it) }
        var genres = listOf<String>()
        var valence: Float? = null
        var energy: Float? = null
        var danceability: Float? = null

        when {
            any("행복", "기쁨", "즐거", "happy", "joy") -> {
                genres = listOf("pop", "dance", "edm")
                valence = 0.9f; energy = 0.7f; danceability = 0.7f
            }
            any("신나", "에너지", "활기", "energetic", "excited") -> {
                genres = listOf("rock", "edm", "dance")
                valence = 0.8f; energy = 0.9f; danceability = 0.6f
            }
            any("우울", "슬프", "sad", "down") -> {
                genres = listOf("acoustic", "ambient", "classical")
                valence = 0.2f; energy = 0.3f; danceability = 0.2f
            }
            any("차분", "평온", "calm", "relax", "편안") -> {
                genres = listOf("chill", "ambient", "acoustic")
                valence = 0.6f; energy = 0.3f; danceability = 0.3f
            }
            any("집중", "공부", "focus", "work") -> {
                genres = listOf("classical", "ambient", "chill")
                valence = 0.5f; energy = 0.2f; danceability = 0.2f
            }
            any("사랑", "로맨틱", "romantic", "love") -> {
                genres = listOf("soul", "jazz", "pop")
                valence = 0.8f; energy = 0.4f; danceability = 0.5f
            }
            any("화나", "분노", "angry", "rage") -> {
                genres = listOf("metal", "rock")
                valence = 0.3f; energy = 0.95f; danceability = 0.4f
            }
            else -> {
                genres = listOf("pop", "indie", "rock")
                valence = 0.6f; energy = 0.6f; danceability = 0.6f
            }
        }
        return RecommendationParams(genres, valence, energy, danceability)
    }

    fun getRecommendations(seedTracks: List<String>, mood: String): List<String> {
        try {
            val builder = spotifyApi.getRecommendations()
            if (seedTracks.isNotEmpty()) {
                builder.seed_tracks(seedTracks.take(5).joinToString(","))
            }
            val params = deriveParams(mood)
            if (seedTracks.isEmpty() && params.genres.isNotEmpty()) {
                builder.seed_genres(params.genres.take(5).joinToString(","))
            }
            params.valence?.let { builder.target_valence(it) }
            params.energy?.let { builder.target_energy(it) }
            params.danceability?.let { builder.target_danceability(it) }

            val recommendations = builder.limit(10).build().execute()
            return recommendations.tracks.map { "${it.name} - ${it.artists[0].name}" }
        } catch (e: Exception) {
            throw RuntimeException("Error getting recommendations", e)
        }
    }

    fun getRecommendationsAdvanced(
        seedTracks: List<String> = emptyList(),
        genres: List<String> = emptyList(),
        targetValence: Float? = null,
        targetEnergy: Float? = null,
        targetDanceability: Float? = null
    ): List<Map<String, Any>> {
        try {
            val builder = spotifyApi.getRecommendations()
            if (seedTracks.isNotEmpty()) builder.seed_tracks(seedTracks.take(5).joinToString(","))
            if (genres.isNotEmpty()) builder.seed_genres(genres.take(5).joinToString(","))
            targetValence?.let { builder.target_valence(it) }
            targetEnergy?.let { builder.target_energy(it) }
            targetDanceability?.let { builder.target_danceability(it) }
            val rec = builder.limit(12).build().execute()
            val ids = rec.tracks.mapNotNull { it.id }
            val imageMap = getAlbumImagesForTrackIds(ids)
            return rec.tracks.map { t ->
                mapOf(
                    "id" to t.id,
                    "uri" to t.uri,
                    "name" to t.name,
                    "artists" to t.artists.joinToString(", ") { it.name },
                    "albumImage" to (t.id?.let { imageMap[it] } ?: ""),
                    "spotifyUrl" to (t.externalUrls?.externalUrls?.get("spotify") ?: "")
                )
            }
        } catch (e: Exception) {
            throw RuntimeException("Error getting recommendations advanced", e)
        }
    }

    fun getAlbumImagesForTrackIds(ids: List<String>): Map<String, String> {
        return try {
            if (ids.isEmpty()) emptyMap() else {
                val res = spotifyApi.getSeveralTracks(*ids.toTypedArray()).build().execute()
                res.filterNotNull().associate { tr ->
                    val url = tr.album?.images?.firstOrNull()?.url ?: ""
                    val id = tr.id ?: ""
                    id to url
                }.filterKeys { it.isNotBlank() }
            }
        } catch (e: Exception) {
            // 이미지 조회 실패는 추천 자체를 막지 않음
            emptyMap()
        }
    }

    fun getPlaylistTracks(playlistId: String): List<String> {
        try {
            val request = spotifyApi
                .getPlaylistsItems(playlistId)
                .build()
            val result = request.execute()
            return result.items.map {
                val track = it.track as Track
                "${track.name} - ${track.artists[0].name}"
            }
        } catch (e: Exception) {
            throw RuntimeException("Error getting playlist tracks", e)
        }
    }
}
