package com.crawling.planet.httpclient.core

import org.springframework.http.HttpMethod

/**
 * HTTP 메서드 정보
 */
data class HttpMethodInfo(
    val httpMethod: HttpMethod,
    val path: String,
    val headers: Map<String, String>
)


