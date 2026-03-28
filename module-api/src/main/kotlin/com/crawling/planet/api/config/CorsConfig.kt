package com.crawling.planet.api.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class CorsConfig {

    @Bean
    fun corsConfigurer(): WebMvcConfigurer {
        return object : WebMvcConfigurer {
            override fun addCorsMappings(registry: CorsRegistry) {
                registry.addMapping("/api/ext/**")
                    .allowedOriginPatterns(
                        "chrome-extension://*",
                        "http://localhost:*",
                        "https://www.jobkorea.co.kr"
                    )
                    .allowedMethods("GET", "OPTIONS")
                    .allowedHeaders("*")
                    .allowCredentials(false)
                    .maxAge(3600)
            }
        }
    }
}
