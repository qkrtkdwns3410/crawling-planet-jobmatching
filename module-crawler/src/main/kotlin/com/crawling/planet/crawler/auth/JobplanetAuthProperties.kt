package com.crawling.planet.crawler.auth

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "jobplanet.auth")
data class JobplanetAuthProperties(
    val email: String = "",
    val password: String = "",
    val loginUrl: String = "https://www.jobplanet.co.kr/user-session/sign-in",
    val headless: Boolean = true,
    val loginTimeoutSeconds: Long = 30
)
