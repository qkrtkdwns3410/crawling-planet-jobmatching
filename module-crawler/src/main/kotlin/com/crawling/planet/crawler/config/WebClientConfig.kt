package com.crawling.planet.crawler.config

import io.netty.channel.ChannelOption
import io.netty.handler.timeout.ReadTimeoutHandler
import io.netty.handler.timeout.WriteTimeoutHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.ExchangeStrategies
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * WebClient 설정
 */
@Configuration
class WebClientConfig {

    companion object {
        private const val BASE_URL = "https://www.jobplanet.co.kr"
        private const val CONNECT_TIMEOUT_MS = 10000
        private const val READ_TIMEOUT_SEC = 30L
        private const val WRITE_TIMEOUT_SEC = 30L
        private const val MAX_IN_MEMORY_SIZE = 16 * 1024 * 1024 // 16MB
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

        // 큰 응답을 처리하기 위한 메모리 설정
        val exchangeStrategies = ExchangeStrategies.builder()
            .codecs { configurer ->
                configurer.defaultCodecs().maxInMemorySize(MAX_IN_MEMORY_SIZE)
            }
            .build()

        return WebClient.builder()
            .baseUrl(BASE_URL)
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .exchangeStrategies(exchangeStrategies)
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader(HttpHeaders.ACCEPT_LANGUAGE, "ko,en;q=0.9,en-US;q=0.8")
            .defaultHeader("jp-os-type", "web")
            .defaultHeader(HttpHeaders.USER_AGENT, 
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36")
            .build()
    }
}


