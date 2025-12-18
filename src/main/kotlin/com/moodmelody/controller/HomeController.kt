package com.moodmelody.controller

import com.moodmelody.service.SpotifyUserService
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping

@Controller
class HomeController(
    private val spotifyUserService: SpotifyUserService
) {
    @GetMapping("/")
    fun home(): String {
        return "index"
    }
    
    @GetMapping("/home")
    fun dashboard(authentication: Authentication, user: OAuth2User, model: Model): String {
        // 이름은 Google/Spotify 호환
        val nameAttr = (user.attributes["name"] ?: user.attributes["display_name"]) as? String ?: "User"
        model.addAttribute("name", nameAttr)
        model.addAttribute("email", user.attributes["email"] ?: "")

        // Spotify 프로필 불러오기 (로그인 제공자가 Spotify인 경우 유효 토큰 존재)
        try {
            val profile = spotifyUserService.getCurrentUserProfile(authentication)
            val images = (profile["images"] as? List<Map<String, Any>>)
            val avatarUrl = images?.firstOrNull()?.get("url") as? String ?: "https://ui-avatars.com/api/?name=${nameAttr}&background=6c63ff&color=fff"
            val followers = (profile["followers"] as? Map<*, *>)?.get("total") as? Int ?: 0
            model.addAttribute("avatarUrl", avatarUrl)
            model.addAttribute("followers", followers)
        } catch (_: Exception) {
            // Spotify 토큰 없거나 오류 시 기본값 유지
        }
        
        return "home"
    }
}
