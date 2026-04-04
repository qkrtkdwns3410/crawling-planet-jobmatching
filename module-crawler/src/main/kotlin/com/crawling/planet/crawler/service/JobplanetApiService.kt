package com.crawling.planet.crawler.service

import com.crawling.planet.crawler.auth.CookieTokenStore
import com.crawling.planet.crawler.config.JobplanetApiProperties
import com.crawling.planet.crawler.diagnostics.CrawlerDiagnosticsService
import com.crawling.planet.domain.dto.JobplanetApiResponse
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import okhttp3.OkHttpClient
import okhttp3.Request
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import reactor.util.retry.Retry
import java.io.IOException
import java.time.Duration

private val logger = KotlinLogging.logger {}

class HttpStatusException(val statusCode: Int) : IOException("HTTP $statusCode")

@Service
class JobplanetApiService(
    private val jobplanetOkHttpClient: OkHttpClient,
    private val cookieTokenStore: CookieTokenStore,
    private val apiProperties: JobplanetApiProperties,
    private val diagnosticsService: CrawlerDiagnosticsService,
    private val objectMapper: ObjectMapper
) {
    companion object {
        private const val BASE_URL = "https://www.jobplanet.co.kr"
        private const val REVIEWS_API_PATH = "/api/v4/companies/reviews/list"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36"
    }

    fun getCompanyReviews(companyId: Long, page: Int = 1): Mono<JobplanetApiResponse> {
        logger.debug { "회사 리뷰 조회 시작 - companyId: $companyId, page: $page" }

        return Mono.fromCallable { executeRequest(companyId, page) }
            .subscribeOn(Schedulers.boundedElastic())
            .retryWhen(
                Retry.backoff(apiProperties.maxRetries.toLong(), Duration.ofMillis(apiProperties.retryDelayMs))
                    .filter { it is HttpStatusException && it.statusCode != 401 }
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
                if (error is HttpStatusException && error.statusCode == 401) {
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

    private fun executeRequest(companyId: Long, page: Int): JobplanetApiResponse {
        val url = "$BASE_URL$REVIEWS_API_PATH?device=desktop&company_id=$companyId&page=$page"

        val request = Request.Builder()
            .url(url)
            .get()
            .header("Cookie", buildCookieString())
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .header("Accept-Language", "ko,en;q=0.9,en-US;q=0.8")
            .header("jp-os-type", "web")
            .header("jp-ssr-auth", apiProperties.ssrAuth)
            .header("sec-ch-ua", "\"Microsoft Edge\";v=\"143\", \"Chromium\";v=\"143\", \"Not A(Brand\";v=\"24\"")
            .header("sec-ch-ua-mobile", "?0")
            .header("sec-ch-ua-platform", "\"Windows\"")
            .header("sec-fetch-dest", "empty")
            .header("sec-fetch-mode", "cors")
            .header("sec-fetch-site", "same-origin")
            .build()

        diagnosticsService.recordApiRequest(REVIEWS_API_PATH, companyId)

        return jobplanetOkHttpClient.newCall(request).execute().use { response ->
            val statusCode = response.code
            diagnosticsService.recordApiResponse(REVIEWS_API_PATH, companyId, statusCode)

            if (!response.isSuccessful) {
                if (statusCode in 400..499) {
                    logger.warn { "4xx 에러 발생 - companyId: $companyId, status: $statusCode" }
                } else {
                    logger.warn { "5xx 에러 발생 - companyId: $companyId, status: $statusCode" }
                }
                throw HttpStatusException(statusCode)
            }

            val body = response.body?.string() ?: throw IOException("Empty response body - companyId: $companyId")
            objectMapper.readValue(body, JobplanetApiResponse::class.java)
        }
    }

    private fun buildCookieString(): String {
        val allCookies = cookieTokenStore.getAllCookies()
        if (allCookies.isNotEmpty()) {
            return allCookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
        }
        val cookies = mutableListOf<String>()
        cookieTokenStore.getAccessToken()?.let { cookies.add("access_token=$it") }
        cookieTokenStore.getRefreshToken()?.let { cookies.add("refresh_token=$it") }
        return cookies.joinToString("; ")
    }
}
