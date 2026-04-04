package com.crawling.planet.app.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.security.MessageDigest

@Component
class CrawlingAuthFilter(
    @Value("\${crawling.admin.token:}") private val adminToken: String
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        if (request.requestURI.startsWith("/api/crawling/")) {
            if (adminToken.isBlank()) {
                response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Auth not configured")
                return
            }
            // GET /status만 인증 없이 허용
            if (request.method == "GET" && request.requestURI == "/api/crawling/status") {
                filterChain.doFilter(request, response)
                return
            }

            val token = request.getHeader("Authorization")?.removePrefix("Bearer ") ?: ""
            if (!MessageDigest.isEqual(
                    token.toByteArray(Charsets.UTF_8),
                    adminToken.toByteArray(Charsets.UTF_8)
                )) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid admin token")
                return
            }
        }

        filterChain.doFilter(request, response)
    }
}
