package com.crawling.planet.crawler.service

import com.crawling.planet.crawler.config.JobplanetApiProperties
import com.crawling.planet.domain.dto.JobplanetApiResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import java.time.Duration

private val logger = KotlinLogging.logger {}

@Service
class JobplanetApiService(
    private val jobplanetWebClient: WebClient,
    private val apiProperties: JobplanetApiProperties
) {
    companion object {
        private const val REVIEWS_API_PATH = "/api/v4/companies/reviews/list"
    }

    fun getCompanyReviews(companyId: Long, page: Int = 1): Mono<JobplanetApiResponse> {
        logger.debug { "회사 리뷰 조회 시작 - companyId: $companyId, page: $page" }

        return jobplanetWebClient.get()
            .uri { uriBuilder ->
                uriBuilder
                    .path(REVIEWS_API_PATH)
                    .queryParam("device", "desktop")
                    .queryParam("company_id", companyId)
                    .queryParam("page", page)
                    .build()
            }
            .retrieve()
            .onStatus(HttpStatusCode::is4xxClientError) { response ->
                logger.warn { "4xx 에러 발생 - companyId: $companyId, status: ${response.statusCode()}" }
                Mono.error(
                    WebClientResponseException.create(
                        response.statusCode().value(),
                        "Client Error",
                        HttpHeaders.EMPTY,
                        ByteArray(0),
                        null
                    )
                )
            }
            .onStatus(HttpStatusCode::is5xxServerError) { response ->
                logger.warn { "5xx 에러 발생 - companyId: $companyId, status: ${response.statusCode()}" }
                Mono.error(
                    WebClientResponseException.create(
                        response.statusCode().value(),
                        "Server Error",
                        HttpHeaders.EMPTY,
                        ByteArray(0),
                        null
                    )
                )
            }
            .bodyToMono(JobplanetApiResponse::class.java)
            .retryWhen(
                Retry.backoff(apiProperties.maxRetries.toLong(), Duration.ofMillis(apiProperties.retryDelayMs))
                    .filter { it is WebClientResponseException && it.statusCode.value() != 401 }
                    .doBeforeRetry { signal ->
                        logger.warn { "재시도 중 - companyId: $companyId, attempt: ${signal.totalRetries() + 1}" }
                    }
            )
            .doOnSuccess { response ->
                logger.debug { "회사 리뷰 조회 성공 - companyId: $companyId, totalCount: ${response?.data?.totalCount}" }
            }
            .doOnError { error ->
                logger.error(error) { "회사 리뷰 조회 실패 - companyId: $companyId" }
            }
            .onErrorResume { error ->
                if (error is WebClientResponseException && error.statusCode.value() == 401) {
                    return@onErrorResume Mono.error(error)
                }
                logger.warn { "회사 리뷰 조회 실패로 빈 응답 반환 - companyId: $companyId, error: ${error.message}" }
                Mono.empty()
            }
    }

    fun getAllCompanyReviews(companyId: Long): Flux<JobplanetApiResponse> {
        return getCompanyReviews(companyId, 1)
            .expand { response ->
                val currentPage = response.data?.page ?: 1
                val totalPage = response.data?.totalPage ?: 1

                if (currentPage < totalPage) {
                    Mono.delay(Duration.ofMillis(apiProperties.delayMs))
                        .flatMap { getCompanyReviews(companyId, currentPage + 1) }
                } else {
                    Mono.empty()
                }
            }
    }
}
