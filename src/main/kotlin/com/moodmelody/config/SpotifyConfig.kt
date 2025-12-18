package com.moodmelody.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import se.michaelthelin.spotify.SpotifyApi
import se.michaelthelin.spotify.SpotifyHttpManager

@Configuration
class SpotifyConfig(
    @Value("\${spotify.client.id}") private val clientId: String,
    @Value("\${spotify.client.secret}") private val clientSecret: String,
    @Value("\${spotify.client.redirect-uri}") private val redirectUri: String
) {
    
    @Bean
    fun spotifyApi(): SpotifyApi {
        return SpotifyApi.Builder()
            .setClientId(clientId)
            .setClientSecret(clientSecret)
            .setRedirectUri(SpotifyHttpManager.makeUri(redirectUri))
            .build()
    }
}
