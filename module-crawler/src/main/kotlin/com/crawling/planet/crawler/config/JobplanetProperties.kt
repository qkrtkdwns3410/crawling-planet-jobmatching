package com.crawling.planet.crawler.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 잡플래닛 크롤링 설정 프로퍼티
 *
 * VM 옵션으로 오버라이드 가능:
 * -Djobplanet.email=your-email
 * -Djobplanet.password=your-password
 */
@ConfigurationProperties(prefix = "jobplanet")
data class JobplanetProperties(
    val email: String,
    val password: String,
    val loginUrl: String = "https://www.jobplanet.co.kr/user-session/sign-in?_nav=gb",
    val baseUrl: String = "https://www.jobplanet.co.kr",
    val crawler: CrawlerProperties = CrawlerProperties()
) {
    data class CrawlerProperties(
        val headless: Boolean = false,
        val timeoutSeconds: Long = 30,
        val implicitWaitSeconds: Long = 10
    )
}

