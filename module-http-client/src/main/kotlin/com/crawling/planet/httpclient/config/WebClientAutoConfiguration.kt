package com.crawling.planet.httpclient.config

import io.github.oshai.kotlinlogging.KotlinLogging
import io.netty.channel.ChannelOption
import io.netty.handler.timeout.ReadTimeoutHandler
import io.netty.handler.timeout.WriteTimeoutHandler
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.ExchangeStrategies
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.time.Duration
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

/**
 * WebClient 자동 설정
 */
@AutoConfiguration
@ConditionalOnClass(WebClient::class)
@EnableConfigurationProperties(WebClientProperties::class)
class WebClientAutoConfiguration {
    
    @Bean
    @ConditionalOnMissingBean
    fun webClientBuilder(properties: WebClientProperties): WebClient.Builder {
        logger.info { "Configuring WebClient with properties: $properties" }
        
        val httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, properties.connectTimeout.toMillis().toInt())
            .responseTimeout(Duration.ofMillis(properties.readTimeout.toMillis()))
            .doOnConnected { conn ->
                conn.addHandlerLast(ReadTimeoutHandler(properties.readTimeout.toMillis(), TimeUnit.MILLISECONDS))
                conn.addHandlerLast(WriteTimeoutHandler(properties.writeTimeout.toMillis(), TimeUnit.MILLISECONDS))
            }
        
        // 큰 응답 처리를 위한 버퍼 크기 설정
        val exchangeStrategies = ExchangeStrategies.builder()
            .codecs { configurer ->
                configurer.defaultCodecs().maxInMemorySize(properties.maxInMemorySize.toInt())
            }
            .build()
        
        return WebClient.builder()
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .exchangeStrategies(exchangeStrategies)
            .defaultHeader("User-Agent", properties.userAgent)
    }
}


