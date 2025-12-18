package com.moodmelody.controller

import com.moodmelody.service.SpotifyUserService
import org.springframework.security.core.Authentication
import org.springframework.security.core.annotation.AuthenticationPrincipal
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
    fun dashboard(authentication: Authentication?, @AuthenticationPrincipal user: OAuth2User?, model: Model): String {
        // 이름은 Google/Spotify 호환
        val nameAttr = (user?.attributes?.get("name") ?: user?.attributes?.get("display_name")) as? String ?: "User"
        model.addAttribute("name", nameAttr)
        model.addAttribute("email", user?.attributes?.get("email") ?: "")

        // 기본값 설정
        var avatarUrl = "https://ui-avatars.com/api/?name=${nameAttr}&background=6c63ff&color=fff"
        var followers = 0

        // Spotify 프로필 불러오기 (로그인 제공자가 Spotify인 경우 유효 토큰 존재)
        if (authentication != null) {
            try {
                val profile = spotifyUserService.getCurrentUserProfile(authentication)
                val images = (profile["images"] as? List<Map<String, Any>>)
                avatarUrl = images?.firstOrNull()?.get("url") as? String ?: avatarUrl
                followers = (profile["followers"] as? Map<*, *>)?.get("total") as? Int ?: 0
            } catch (_: Exception) {
                // Spotify 토큰 없거나 오류 시 기본값 유지
            }
        }

        model.addAttribute("avatarUrl", avatarUrl)
        model.addAttribute("followers", followers)
        
        return "home"
    }
}
