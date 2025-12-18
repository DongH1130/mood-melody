package com.moodmelody.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.apache.hc.client5.http.classic.methods.HttpGet
import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.core5.http.ClassicHttpResponse
import org.apache.hc.core5.http.io.entity.StringEntity
import org.apache.hc.core5.http.ContentType
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService
import org.springframework.stereotype.Service

@Service
class SpotifyUserService(
    private val authorizedClientService: OAuth2AuthorizedClientService
) {
    private fun getAccessToken(authentication: Authentication): String? {
        val client: OAuth2AuthorizedClient? = authorizedClientService.loadAuthorizedClient(
            "spotify",
            authentication.name
        )
        return client?.accessToken?.tokenValue
    }

    fun getCurrentUserProfile(authentication: Authentication): Map<String, Any> {
        val token = getAccessToken(authentication) ?: throw IllegalStateException("Spotify access token not found")
        val httpClient = HttpClients.createDefault()
        val request = HttpGet("https://api.spotify.com/v1/me")
        request.addHeader("Authorization", "Bearer $token")

        httpClient.execute(request).use { response ->
            val status = response.code
            if (status in 200..299) {
                val body = (response as ClassicHttpResponse).entity.content.bufferedReader().readText()
                return jacksonObjectMapper().readValue<Map<String, Any>>(body)
            } else {
                throw RuntimeException("Spotify /me failed: HTTP $status")
            }
        }
    }

    fun getRecentlyPlayed(authentication: Authentication, limit: Int = 20): Map<String, Any> {
        val token = getAccessToken(authentication) ?: throw IllegalStateException("Spotify access token not found")
        val httpClient = HttpClients.createDefault()
        val request = HttpGet("https://api.spotify.com/v1/me/player/recently-played?limit=$limit")
        request.addHeader("Authorization", "Bearer $token")

        httpClient.execute(request).use { response ->
            val status = response.code
            if (status in 200..299) {
                val body = (response as ClassicHttpResponse).entity.content.bufferedReader().readText()
                return jacksonObjectMapper().readValue<Map<String, Any>>(body)
            } else {
                throw RuntimeException("Spotify recently played failed: HTTP $status")
            }
        }
    }

    fun getTop(authentication: Authentication, type: String = "tracks", timeRange: String = "medium_term", limit: Int = 20): Map<String, Any> {
        val token = getAccessToken(authentication) ?: throw IllegalStateException("Spotify access token not found")
        val httpClient = HttpClients.createDefault()
        val request = HttpGet("https://api.spotify.com/v1/me/top/$type?time_range=$timeRange&limit=$limit")
        request.addHeader("Authorization", "Bearer $token")

        httpClient.execute(request).use { response ->
            val status = response.code
            if (status in 200..299) {
                val body = (response as ClassicHttpResponse).entity.content.bufferedReader().readText()
                return jacksonObjectMapper().readValue<Map<String, Any>>(body)
            } else {
                throw RuntimeException("Spotify top $type failed: HTTP $status")
            }
        }
    }

    fun createPlaylist(authentication: Authentication, name: String, description: String = "", public: Boolean = false): String {
        val token = getAccessToken(authentication) ?: throw IllegalStateException("Spotify access token not found")
        val httpClient = HttpClients.createDefault()
        val url = "https://api.spotify.com/v1/me/playlists"
        val request = HttpPost(url)
        request.addHeader("Authorization", "Bearer $token")
        request.addHeader("Content-Type", ContentType.APPLICATION_JSON.mimeType)
        val body = jacksonObjectMapper().writeValueAsString(mapOf(
            "name" to name,
            "description" to description,
            "public" to public
        ))
        request.entity = StringEntity(body, ContentType.APPLICATION_JSON)

        httpClient.execute(request).use { response ->
            val status = response.code
            val respBody = (response as ClassicHttpResponse).entity.content.bufferedReader().readText()
            if (status in 200..299) {
                val json = jacksonObjectMapper().readValue<Map<String, Any>>(respBody)
                return (json["id"] as? String) ?: throw RuntimeException("Playlist ID missing in response")
            } else {
                throw RuntimeException("Create playlist failed: HTTP $status - $respBody")
            }
        }
    }

    fun addTracksToPlaylist(authentication: Authentication, playlistId: String, uris: List<String>) {
        val token = getAccessToken(authentication) ?: throw IllegalStateException("Spotify access token not found")
        val httpClient = HttpClients.createDefault()
        val url = "https://api.spotify.com/v1/playlists/$playlistId/tracks"
        val request = HttpPost(url)
        request.addHeader("Authorization", "Bearer $token")
        request.addHeader("Content-Type", ContentType.APPLICATION_JSON.mimeType)
        val body = jacksonObjectMapper().writeValueAsString(mapOf(
            "uris" to uris
        ))
        request.entity = StringEntity(body, ContentType.APPLICATION_JSON)

        httpClient.execute(request).use { response ->
            val status = response.code
            val respBody = (response as ClassicHttpResponse).entity.content.bufferedReader().readText()
            if (status !in 200..299) {
                throw RuntimeException("Add tracks failed: HTTP $status - $respBody")
            }
        }
    }

    fun getAudioFeatures(authentication: Authentication, ids: List<String>): Map<String, Any> {
        val token = getAccessToken(authentication) ?: throw IllegalStateException("Spotify access token not found")
        val httpClient = HttpClients.createDefault()
        val joined = ids.joinToString(",")
        val request = HttpGet("https://api.spotify.com/v1/audio-features?ids=$joined")
        request.addHeader("Authorization", "Bearer $token")

        httpClient.execute(request).use { response ->
            val status = response.code
            val body = (response as ClassicHttpResponse).entity.content.bufferedReader().readText()
            if (status in 200..299) {
                return jacksonObjectMapper().readValue<Map<String, Any>>(body)
            } else {
                throw RuntimeException("Spotify audio-features failed: HTTP $status - $body")
            }
        }
    }
}