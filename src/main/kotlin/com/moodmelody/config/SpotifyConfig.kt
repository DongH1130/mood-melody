package com.moodmelody.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import se.michaelthelin.spotify.SpotifyApi
import se.michaelthelin.spotify.SpotifyHttpManager
import se.michaelthelin.spotify.model.credentials.ClientCredentials

@Configuration
class SpotifyConfig(
    @Value("\${spotify.clientId}") private val clientId: String,
    @Value("\${spotify.clientSecret}") private val clientSecret: String,
    @Value("\${spotify.redirectUri}") private val redirectUri: String
) {
    
    @Bean
    fun spotifyApi(): SpotifyApi {
        return SpotifyApi.Builder()
            .setClientId(clientId)
            .setClientSecret(clientSecret)
            .setRedirectUri(SpotifyHttpManager.makeUri(redirectUri))
            .build()
    }
    
    @Bean
    fun spotifyClientCredentials(): ClientCredentials {
        return ClientCredentials.Builder()
            .setClientId(clientId)
            .setClientSecret(clientSecret)
            .build()
    }
}
