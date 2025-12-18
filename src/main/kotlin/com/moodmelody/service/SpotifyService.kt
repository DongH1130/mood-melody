package com.moodmelody.service

import se.michaelthelin.spotify.SpotifyApi
import se.michaelthelin.spotify.model_objects.specification.Paging
import se.michaelthelin.spotify.model_objects.specification.PlaylistTrack
import se.michaelthelin.spotify.model_objects.specification.Track
import se.michaelthelin.spotify.requests.data.playlists.GetPlaylistsItemsRequest
import org.springframework.stereotype.Service
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory

@Service
class SpotifyService(
    private val spotifyApi: SpotifyApi
) {
    private val log = LoggerFactory.getLogger(SpotifyService::class.java)
    
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

    private fun ensureAccessToken() {
        try {
            val token = spotifyApi.accessToken
            if (token == null || token.isBlank()) {
                val creds = spotifyApi.clientCredentials().build().execute()
                spotifyApi.accessToken = creds.accessToken
            }
        } catch (e: Exception) {
            log.warn("Spotify 토큰 확보 실패: {}", e.message)
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
            any("우울", "우울해", "슬프", "슬픔", "침울", "꿀꿀", "blue", "sad", "down", "depress") -> {
                genres = listOf("acoustic", "ambient", "classical")
                valence = 0.2f; energy = 0.3f; danceability = 0.2f
            }
            any("차분", "평온", "잔잔", "조용", "calm", "relax", "편안") -> {
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

    // 공개 메서드: 텍스트를 입력받아 룰 기반 파라미터를 계산
    fun deriveParamsFromText(text: String): RecommendationParams {
        return deriveParams(text)
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

    // Cache allowed genre seeds to avoid repeated calls
    private var allowedGenreSeedsCache: Set<String>? = null

    private fun getAllowedGenreSeeds(): Set<String> {
        allowedGenreSeedsCache?.let { return it }
        return try {
            val seedsArr: Array<String> = spotifyApi.getAvailableGenreSeeds().build().execute()
            val seeds = seedsArr.map { s -> s.lowercase() }.toSet()
            allowedGenreSeedsCache = seeds
            seeds
        } catch (_: Exception) {
            // Fallback to a safe subset that is typically available
            val seeds = setOf("pop", "rock", "indie", "classical", "jazz", "metal", "edm", "dance")
            allowedGenreSeedsCache = seeds
            seeds
        }
    }

    private fun sanitizeGenres(genres: List<String>): List<String> {
        if (genres.isEmpty()) return emptyList()
        val allowed = getAllowedGenreSeeds()
        return genres.map { it.lowercase() }.filter { allowed.contains(it) }
    }

    private fun clamp01(value: Float?): Float? {
        if (value == null) return null
        return kotlin.math.max(0f, kotlin.math.min(1f, value))
    }

    fun getRecommendationsAdvanced(
        seedTracks: List<String> = emptyList(),
        genres: List<String> = emptyList(),
        targetValence: Float? = null,
        targetEnergy: Float? = null,
        targetDanceability: Float? = null,
        strict: Boolean = false
    ): List<Map<String, Any>> {
        try {
            ensureAccessToken()
            val builder = spotifyApi.getRecommendations()
            // Sanitize genres to Spotify's allowed seed list
            var safeGenres = sanitizeGenres(genres)
            if (safeGenres.isEmpty() && seedTracks.isEmpty()) {
                if (strict) {
                    throw IllegalArgumentException("AI 파라미터 없음: seedGenres/seedTracks 미지정")
                }
                // Ensure at least one valid seed genre exists to avoid Spotify 400
                val allowed = getAllowedGenreSeeds()
                val defaults = listOf("pop", "rock", "indie").filter { allowed.contains(it) }
                safeGenres = defaults
            }

            // Enforce combined seed cap: total of tracks+genres must be <= 5
            val trackSeeds = seedTracks.take(5)
            val genreCapacity = kotlin.math.max(0, 5 - trackSeeds.size)
            val genreSeeds = safeGenres.take(genreCapacity)

            if (trackSeeds.isNotEmpty()) builder.seed_tracks(trackSeeds.joinToString(","))
            if (genreSeeds.isNotEmpty()) builder.seed_genres(genreSeeds.joinToString(","))

            // Clamp target values to [0,1] to satisfy Spotify API constraints
            clamp01(targetValence)?.let { builder.target_valence(it) }
            clamp01(targetEnergy)?.let { builder.target_energy(it) }
            clamp01(targetDanceability)?.let { builder.target_danceability(it) }

            val rec = try {
                builder.limit(12).build().execute()
            } catch (ex: Exception) {
                log.warn("Spotify 추천 호출 실패: {}", ex.message)
                ensureAccessToken()
                builder.limit(12).build().execute()
            }
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
            log.warn("고급 추천 실패, 검색 기반 폴백 적용: {}", e.message)
            if (strict) {
                throw e
            }
            val allowed = getAllowedGenreSeeds()
            val baseGenres = sanitizeGenres(genres).ifEmpty { listOf("pop", "rock", "indie").filter { allowed.contains(it) } }
            return fallbackRecommendationsBySearch(baseGenres, limit = 12)
        }
    }

    private fun fallbackRecommendationsBySearch(genres: List<String>, limit: Int = 12): List<Map<String, Any>> {
        return try {
            ensureAccessToken()
            val query = if (genres.isNotEmpty()) genres.joinToString(" ") else "pop indie rock"
            val res = spotifyApi.searchTracks(query).limit(limit).build().execute()
            res.items.map { t ->
                mapOf(
                    "id" to t.id,
                    "uri" to t.uri,
                    "name" to t.name,
                    "artists" to t.artists.joinToString(", ") { it.name },
                    "albumImage" to (t.album?.images?.firstOrNull()?.url ?: ""),
                    "spotifyUrl" to (t.externalUrls?.externalUrls?.get("spotify") ?: "")
                )
            }.filter { (it["id"] as? String).orEmpty().isNotBlank() }
        } catch (e: Exception) {
            log.warn("검색 폴백 실패: {}", e.message)
            emptyList()
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
