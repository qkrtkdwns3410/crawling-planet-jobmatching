package com.crawling.planet.crawler.service

import com.crawling.planet.crawler.config.JobplanetApiProperties
import com.crawling.planet.domain.repository.CompanyRepository
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import reactor.util.retry.Retry
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong

private val logger = KotlinLogging.logger {}

@Service
class CompanyRatingUpdateService(
    private val jobplanetWebClient: WebClient,
    private val companyRepository: CompanyRepository,
    private val apiProperties: JobplanetApiProperties
) {
    data class UpdateResult(val updated: Long, val failed: Long)

    companion object {
        private const val PAGE_SIZE = 1000
    }

    fun updateAllRatings(): Mono<UpdateResult> {
        val updated = AtomicLong(0)
        val failed = AtomicLong(0)
        val processed = AtomicLong(0)

        return Mono.fromCallable {
            companyRepository.findCompaniesNeedingRatingUpdate(PageRequest.of(0, 1)).totalElements
        }
            .flatMap { total ->
                logger.info { "회사 상세 업데이트 시작 - 대상: $total" }
                val totalPages = ((total + PAGE_SIZE - 1) / PAGE_SIZE).toInt()

                Flux.range(0, totalPages)
                    .concatMap { page ->
                        val companies = companyRepository.findCompaniesNeedingRatingUpdate(PageRequest.of(page, PAGE_SIZE)).content
                        Flux.fromIterable(companies)
                            .flatMap(
                                { company ->
                                    fetchLandingHeader(company.jobplanetId)
                                        .publishOn(Schedulers.boundedElastic())
                                        .doOnNext { header ->
                                            companyRepository.updateCompanyDetails(
                                                company.id,
                                                header.name,
                                                header.rateTotalAvg,
                                                header.industryName,
                                                header.logoPath
                                            )
                                            updated.incrementAndGet()
                                        }
                                        .doOnError { e ->
                                            failed.incrementAndGet()
                                            logger.warn { "평점 업데이트 실패 - companyId: ${company.jobplanetId}, error: ${e.message}" }
                                        }
                                        .doFinally {
                                            val count = processed.incrementAndGet()
                                            if (count % 1000 == 0L) {
                                                logger.info { "진행: $count/$total - 성공: ${updated.get()}, 실패: ${failed.get()}" }
                                            }
                                        }
                                        .onErrorResume { Mono.empty() }
                                },
                                apiProperties.concurrency
                            )
                    }
                    .then(Mono.fromCallable {
                        val result = UpdateResult(updated.get(), failed.get())
                        logger.info { "회사 상세 업데이트 완료 - 성공: ${result.updated}, 실패: ${result.failed}" }
                        result
                    })
            }
    }

    fun updateSingleCompanyRating(jobplanetId: Long): Mono<Void> {
        return fetchLandingHeader(jobplanetId)
            .publishOn(Schedulers.boundedElastic())
            .doOnNext { header ->
                companyRepository.findByJobplanetId(jobplanetId).ifPresent { company ->
                    companyRepository.updateCompanyDetails(
                        company.id,
                        header.name,
                        header.rateTotalAvg,
                        header.industryName,
                        header.logoPath
                    )
                }
            }
            .then()
            .onErrorResume {
                logger.warn { "단일 평점 업데이트 실패 - jobplanetId: $jobplanetId, error: ${it.message}" }
                Mono.empty()
            }
    }

    private fun fetchLandingHeader(jobplanetId: Long): Mono<LandingHeaderData> {
        return jobplanetWebClient.get()
            .uri("/api/v5/companies/$jobplanetId/landing/header")
            .retrieve()
            .bodyToMono(LandingHeaderResponse::class.java)
            .flatMap { response ->
                val data = response.data
                if (data != null && (data.rateTotalAvg != null || data.name != null)) Mono.just(data) else Mono.empty()
            }
            .retryWhen(
                Retry.backoff(apiProperties.maxRetries.toLong(), Duration.ofMillis(apiProperties.retryDelayMs))
                    .filter { (it is WebClientResponseException && it.statusCode.is5xxServerError) || it is java.io.IOException }
                    .doBeforeRetry { signal ->
                        logger.warn { "평점 헤더 재시도 - jobplanetId: $jobplanetId, attempt: ${signal.totalRetries() + 1}" }
                    }
            )
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class LandingHeaderResponse(val data: LandingHeaderData?)

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class LandingHeaderData(
        val name: String?,
        @JsonProperty("rate_total_avg") val rateTotalAvg: Double?,
        @JsonProperty("industry_name") val industryName: String?,
        @JsonProperty("logo_path") val logoPath: String?,
        @JsonProperty("approved_reviews_cache") val approvedReviewsCache: Int?,
        @JsonProperty("web_site") val webSite: String?
    )
}
