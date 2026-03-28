package com.crawling.planet.httpclient.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * WebClient 설정 프로퍼티
 */
@ConfigurationProperties(prefix = "webclient")
data class WebClientProperties(
    /**
     * 연결 타임아웃
     */
    val connectTimeout: Duration = Duration.ofSeconds(10),
    
    /**
     * 읽기 타임아웃
     */
    val readTimeout: Duration = Duration.ofSeconds(30),
    
    /**
     * 쓰기 타임아웃
     */
    val writeTimeout: Duration = Duration.ofSeconds(30),
    
    /**
     * 최대 인메모리 버퍼 크기 (바이트)
     */
    val maxInMemorySize: Long = 16 * 1024 * 1024, // 16MB
    
    /**
     * User-Agent 헤더
     */
    val userAgent: String = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
)


