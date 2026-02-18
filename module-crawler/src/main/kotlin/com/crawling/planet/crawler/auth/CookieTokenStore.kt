package com.crawling.planet.crawler.auth

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

private val logger = KotlinLogging.logger {}

data class TokenPair(
    val accessToken: String,
    val refreshToken: String,
    val obtainedAt: Instant = Instant.now()
)

@Component
class CookieTokenStore {
    private val tokenRef = AtomicReference<TokenPair?>(null)

    companion object {
        private const val TOKEN_TTL_HOURS = 12L
    }

    fun store(tokenPair: TokenPair) {
        tokenRef.set(tokenPair)
        logger.info { "토큰 저장 완료 (accessToken 길이: ${tokenPair.accessToken.length})" }
    }

    fun get(): TokenPair? = tokenRef.get()

    fun getAccessToken(): String? = tokenRef.get()?.accessToken

    fun getRefreshToken(): String? = tokenRef.get()?.refreshToken

    fun isExpired(): Boolean {
        val token = tokenRef.get() ?: return true
        val elapsed = Instant.now().epochSecond - token.obtainedAt.epochSecond
        return elapsed > TOKEN_TTL_HOURS * 3600
    }

    fun hasToken(): Boolean = tokenRef.get() != null

    fun clear() {
        tokenRef.set(null)
        logger.info { "토큰 초기화 완료" }
    }
}
