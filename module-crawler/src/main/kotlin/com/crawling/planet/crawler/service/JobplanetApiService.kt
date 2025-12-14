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

/**
 * 잡플래닛 API 호출 서비스
 */
@Service
class JobplanetApiService(
    private val jobplanetWebClient: WebClient,
    private val apiProperties: JobplanetApiProperties
) {
    companion object {
        private const val REVIEWS_API_PATH = "/api/v4/companies/reviews/list"
    }

    /**
     * 특정 회사의 리뷰 목록 조회 (단일 페이지)
     */
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
            .headers { headers -> setupHeaders(headers) }
            .retrieve()
            .onStatus(HttpStatusCode::is4xxClientError) { response ->
                logger.warn { "4xx 에러 발생 - companyId: $companyId, status: ${response.statusCode()}" }
                Mono.error(WebClientResponseException.create(
                    response.statusCode().value(),
                    "Client Error",
                    HttpHeaders.EMPTY,
                    ByteArray(0),
                    null
                ))
            }
            .onStatus(HttpStatusCode::is5xxServerError) { response ->
                logger.warn { "5xx 에러 발생 - companyId: $companyId, status: ${response.statusCode()}" }
                Mono.error(WebClientResponseException.create(
                    response.statusCode().value(),
                    "Server Error",
                    HttpHeaders.EMPTY,
                    ByteArray(0),
                    null
                ))
            }
            .bodyToMono(JobplanetApiResponse::class.java)
            .retryWhen(
                Retry.backoff(apiProperties.maxRetries.toLong(), Duration.ofMillis(apiProperties.retryDelayMs))
                    .filter { it is WebClientResponseException }
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
                logger.warn { "회사 리뷰 조회 실패로 빈 응답 반환 - companyId: $companyId, error: ${error.message}" }
                Mono.empty()
            }
    }

    /**
     * 특정 회사의 모든 리뷰 조회 (모든 페이지)
     */
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

    /**
     * 여러 회사의 리뷰 조회 (병렬 처리)
     */
    fun getMultipleCompanyReviews(companyIds: List<Long>): Flux<Pair<Long, JobplanetApiResponse>> {
        return Flux.fromIterable(companyIds)
            .delayElements(Duration.ofMillis(apiProperties.delayMs))
            .flatMap(
                { companyId ->
                    getCompanyReviews(companyId)
                        .map { response -> companyId to response }
                },
                apiProperties.concurrency // 동시 처리 수 제한
            )
    }

    /**
     * 회사 ID 범위로 리뷰 조회
     */
    fun getCompanyReviewsInRange(startId: Long, endId: Long): Flux<Pair<Long, JobplanetApiResponse>> {
        val companyIds = (startId..endId).toList()
        return getMultipleCompanyReviews(companyIds)
    }

    /**
     * 헤더 설정
     */
    private fun setupHeaders(headers: HttpHeaders) {
        if (apiProperties.accessToken.isNotBlank()) {
            headers.set(HttpHeaders.COOKIE, buildCookieString())
        }
        headers.set("jp-ssr-auth", apiProperties.ssrAuth)
        headers.set("sec-ch-ua", "\"Microsoft Edge\";v=\"143\", \"Chromium\";v=\"143\", \"Not A(Brand\";v=\"24\"")
        headers.set("sec-ch-ua-mobile", "?0")
        headers.set("sec-ch-ua-platform", "\"Windows\"")
        headers.set("sec-fetch-dest", "empty")
        headers.set("sec-fetch-mode", "cors")
        headers.set("sec-fetch-site", "same-origin")
    }

    /**
     * 쿠키 문자열 생성
     */
    private fun buildCookieString(): String {
        val cookies = mutableListOf<String>()
        
        if (apiProperties.accessToken.isNotBlank()) {
            cookies.add("access_token=${apiProperties.accessToken}")
        }
        if (apiProperties.refreshToken.isNotBlank()) {
            cookies.add("refresh_token=${apiProperties.refreshToken}")
        }
        
        return cookies.joinToString("; ")
    }
}

