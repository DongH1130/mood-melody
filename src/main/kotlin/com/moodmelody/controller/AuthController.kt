package com.moodmelody.controller

import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ResponseBody

@Controller
class AuthController {

    @GetMapping("/user")
    @ResponseBody
    fun user(@AuthenticationPrincipal user: OAuth2User): Map<String, Any> {
        return user.attributes
    }

    @GetMapping("/login-success")
    fun loginSuccess(@AuthenticationPrincipal user: OAuth2User, model: Model): String {
        model.addAttribute("name", user.attributes["name"] ?: "User")
        return "redirect:/home"
    }

    @GetMapping("/login-failure")
    fun loginFailure(): String {
        return "redirect:/?error"
    }
}
