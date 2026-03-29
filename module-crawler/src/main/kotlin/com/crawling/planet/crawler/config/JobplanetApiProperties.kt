package com.crawling.planet.crawler.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 잡플래닛 API 호출 설정
 */
@ConfigurationProperties(prefix = "jobplanet.api")
data class JobplanetApiProperties(
    /**
     * Access Token (로그인 후 획득)
     */
    val accessToken: String = "",
    
    /**
     * Refresh Token
     */
    val refreshToken: String = "",
    
    /**
     * SSR Auth 토큰
     */
    val ssrAuth: String = "",
    
    /**
     * 시작 회사 ID
     */
    val startCompanyId: Long = 1,
    
    /**
     * 종료 회사 ID
     */
    val endCompanyId: Long = 500000,
    
    /**
     * 배치 크기 (한 번에 처리할 회사 수)
     */
    val batchSize: Int = 100,
    
    /**
     * API 호출 간 딜레이 (밀리초)
     */
    val delayMs: Long = 500,
    
    /**
     * 재시도 횟수
     */
    val maxRetries: Int = 3,
    
    /**
     * 재시도 간 딜레이 (밀리초)
     */
    val retryDelayMs: Long = 1000,
    
    /**
     * 동시 처리 수
     */
    val concurrency: Int = 5
)



