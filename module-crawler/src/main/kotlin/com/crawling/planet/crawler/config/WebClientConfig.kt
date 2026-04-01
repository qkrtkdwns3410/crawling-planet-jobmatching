package com.crawling.planet.crawler.config

import com.crawling.planet.crawler.auth.CookieTokenStore
import com.crawling.planet.crawler.diagnostics.CrawlerDiagnosticsService
import io.netty.channel.ChannelOption
import io.netty.handler.timeout.ReadTimeoutHandler
import io.netty.handler.timeout.WriteTimeoutHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ExchangeFilterFunction
import org.springframework.web.reactive.function.client.ExchangeStrategies
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import reactor.netty.http.client.HttpClient
import java.net.URI
import java.time.Duration
import java.util.concurrent.TimeUnit

@Configuration
class WebClientConfig(
    private val cookieTokenStore: CookieTokenStore,
    private val apiProperties: JobplanetApiProperties,
    private val diagnosticsService: CrawlerDiagnosticsService
) {
    companion object {
        private const val BASE_URL = "https://www.jobplanet.co.kr"
        private const val CONNECT_TIMEOUT_MS = 10000
        private const val READ_TIMEOUT_SEC = 30L
        private const val WRITE_TIMEOUT_SEC = 30L
        private const val MAX_IN_MEMORY_SIZE = 16 * 1024 * 1024
    }

    @Bean
    fun jobplanetWebClient(): WebClient {
        val httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MS)
            .responseTimeout(Duration.ofSeconds(READ_TIMEOUT_SEC))
            .doOnConnected { conn ->
                conn.addHandlerLast(ReadTimeoutHandler(READ_TIMEOUT_SEC, TimeUnit.SECONDS))
                conn.addHandlerLast(WriteTimeoutHandler(WRITE_TIMEOUT_SEC, TimeUnit.SECONDS))
            }

        val exchangeStrategies = ExchangeStrategies.builder()
            .codecs { configurer ->
                configurer.defaultCodecs().maxInMemorySize(MAX_IN_MEMORY_SIZE)
            }
            .build()

        return WebClient.builder()
            .baseUrl(BASE_URL)
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .exchangeStrategies(exchangeStrategies)
            .filter(dynamicCookieFilter())
            .filter(diagnosticsFilter())
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader(HttpHeaders.ACCEPT_LANGUAGE, "ko,en;q=0.9,en-US;q=0.8")
            .defaultHeader("jp-os-type", "web")
            .defaultHeader(
                HttpHeaders.USER_AGENT,
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36"
            )
            .build()
    }

    private fun dynamicCookieFilter(): ExchangeFilterFunction {
        return ExchangeFilterFunction.ofRequestProcessor { request ->
            val cookies = buildCookieString()
            if (cookies.isNotBlank()) {
                val modified = ClientRequest.from(request)
                    .header(HttpHeaders.COOKIE, cookies)
                    .header("jp-ssr-auth", apiProperties.ssrAuth)
                    .header("sec-ch-ua", "\"Microsoft Edge\";v=\"143\", \"Chromium\";v=\"143\", \"Not A(Brand\";v=\"24\"")
                    .header("sec-ch-ua-mobile", "?0")
                    .header("sec-ch-ua-platform", "\"Windows\"")
                    .header("sec-fetch-dest", "empty")
                    .header("sec-fetch-mode", "cors")
                    .header("sec-fetch-site", "same-origin")
                    .build()
                Mono.just(modified)
            } else {
                Mono.just(request)
            }
        }
    }

    private fun diagnosticsFilter(): ExchangeFilterFunction {
        return ExchangeFilterFunction { request, next ->
            val path = request.url().path
            val companyId = extractCompanyId(request.url())
            diagnosticsService.recordApiRequest(path, companyId)

            next.exchange(request)
                .doOnNext { response ->
                    diagnosticsService.recordApiResponse(path, companyId, response.statusCode().value())
                }
                .doOnError { error ->
                    diagnosticsService.recordTransportError(path, companyId, error.message)
                }
        }
    }

    private fun buildCookieString(): String {
        val allCookies = cookieTokenStore.getAllCookies()
        if (allCookies.isNotEmpty()) {
            return allCookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
        }

        // fallback: 개별 토큰만 있는 경우
        val cookies = mutableListOf<String>()

        val accessToken = cookieTokenStore.getAccessToken()
        if (!accessToken.isNullOrBlank()) {
            cookies.add("access_token=$accessToken")
        }

        val refreshToken = cookieTokenStore.getRefreshToken()
        if (!refreshToken.isNullOrBlank()) {
            cookies.add("refresh_token=$refreshToken")
        }

        return cookies.joinToString("; ")
    }

    private fun extractCompanyId(uri: URI): Long? {
        val query = uri.query.orEmpty()
        val queryMatch = Regex("""(?:^|&)company_id=(\d+)""").find(query)
        if (queryMatch != null) {
            return queryMatch.groupValues[1].toLongOrNull()
        }

        val pathMatch = Regex("""/companies/(\d+)/""").find(uri.path)
        return pathMatch?.groupValues?.get(1)?.toLongOrNull()
    }
}
