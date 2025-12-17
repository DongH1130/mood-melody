package com.moodmelody.controller

import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping

@Controller
class HomeController {
    
    @GetMapping("/")
    fun home(): String {
        return "index"
    }
    
    @GetMapping("/home")
    fun dashboard(@AuthenticationPrincipal user: OAuth2User, model: Model): String {
        model.addAttribute("name", user.attributes["name"] ?: "User")
        model.addAttribute("email", user.attributes["email"] ?: "")
        return "home"
    }
}
