package com.crawling.planet.app.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class CrawlingAuthFilter(
    @Value("\${crawling.admin.token:}") private val adminToken: String
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        if (request.requestURI.startsWith("/api/crawling/") && adminToken.isNotBlank()) {
            // GET /status는 인증 없이 허용
            if (request.method == "GET") {
                filterChain.doFilter(request, response)
                return
            }

            val token = request.getHeader("Authorization")?.removePrefix("Bearer ")
            if (token != adminToken) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid admin token")
                return
            }
        }

        filterChain.doFilter(request, response)
    }
}
