package com.moodmelody.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableWebSecurity
class SecurityConfig {
    
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .authorizeHttpRequests { authorize ->
                authorize
                    .requestMatchers(
                        "/", "/css/**", "/js/**", "/images/**", "/webjars/**",
                        "/oauth2/**", "/login/oauth2/**",
                        "/swagger-ui/**", "/v3/api-docs/**",
                        "/api/ai/status", "/api/analyze-mood-params"
                    ).permitAll()
                    .anyRequest().authenticated()
            }
            .oauth2Login { oauth2 ->
                oauth2
                    .loginPage("/")
                    .defaultSuccessUrl("/home", true)
            }
            .logout { logout ->
                logout
                    .logoutSuccessUrl("/")
                    .invalidateHttpSession(true)
                    .clearAuthentication(true)
                    .deleteCookies("JSESSIONID")
            }
            .csrf { csrf ->
                csrf.disable()
            }
        
        return http.build()
    }
}
