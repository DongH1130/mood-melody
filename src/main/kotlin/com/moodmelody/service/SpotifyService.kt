package com.moodmelody.service

import com.wrapper.spotify.SpotifyApi
import com.wrapper.spotify.exceptions.SpotifyWebApiException
import com.wrapper.spotify.model_objects.credentials.ClientCredentials
import com.wrapper.spotify.model_objects.specification.Paging
import com.wrapper.spotify.model_objects.specification.Playlist
import com.wrapper.spotify.model_objects.specification.PlaylistTrack
import com.wrapper.spotify.requests.data.playlists.GetPlaylistsTracksRequest
import org.springframework.stereotype.Service
import java.io.IOException
import javax.annotation.PostConstruct

@Service
class SpotifyService(
    private val spotifyApi: SpotifyApi,
    private val clientCredentials: ClientCredentials
) {
    
    @PostConstruct
    fun init() {
        try {
            val clientCredentialsRequest = spotifyApi.clientCredentials().build()
            val clientCredentials = clientCredentialsRequest.execute()
            spotifyApi.accessToken = clientCredentials.accessToken
        } catch (e: Exception) {
            throw RuntimeException("Error initializing Spotify API", e)
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
    
    fun getRecommendations(seedTracks: List<String>, mood: String): List<String> {
        try {
            val recommendationsRequest = spotifyApi.getRecommendations()
                .seed_tracks(seedTracks.take(5).toTypedArray())
                .limit(10)
                .build()
                
            val recommendations = recommendationsRequest.execute()
            return recommendations.tracks.map { "${it.name} - ${it.artists[0].name}" }
        } catch (e: Exception) {
            throw RuntimeException("Error getting recommendations", e)
        }
    }
    
    fun getPlaylistTracks(playlistId: String): List<String> {
        try {
            val request: GetPlaylistsTracksRequest = spotifyApi
                .getPlaylistsTracks(playlistId)
                .build()
                
            val result: Paging<PlaylistTrack> = request.execute()
            return result.items.map { "${it.track.name} - ${it.track.artists[0].name}" }
        } catch (e: Exception) {
            throw RuntimeException("Error getting playlist tracks", e)
        }
    }
}
