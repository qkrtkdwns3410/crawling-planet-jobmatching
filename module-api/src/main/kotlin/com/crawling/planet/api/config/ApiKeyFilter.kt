package com.crawling.planet.api.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.security.MessageDigest

@Component
class ApiKeyFilter(
    @Value("\${api.security.key:}") private val apiKey: String
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        if (request.method == "OPTIONS") {
            filterChain.doFilter(request, response)
            return
        }

        if (request.requestURI.startsWith("/api/ext/")) {
            if (apiKey.isBlank()) {
                response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Auth not configured")
                return
            }
            val requestKey = request.getHeader("X-API-Key") ?: ""
            if (!MessageDigest.isEqual(
                    requestKey.toByteArray(Charsets.UTF_8),
                    apiKey.toByteArray(Charsets.UTF_8)
                )) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid API Key")
                return
            }
        }

        filterChain.doFilter(request, response)
    }
}
