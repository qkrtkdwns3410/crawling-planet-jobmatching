package com.crawling.planet.api.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

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

        if (request.requestURI.startsWith("/api/ext/") && apiKey.isNotBlank()) {
            val requestKey = request.getHeader("X-API-Key")
            if (requestKey != apiKey) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid API Key")
                return
            }
        }

        filterChain.doFilter(request, response)
    }
}
