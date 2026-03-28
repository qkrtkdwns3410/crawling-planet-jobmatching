package com.crawling.planet.crawler.service

import com.crawling.planet.crawler.config.JobplanetApiProperties
import com.crawling.planet.domain.repository.CompanyRepository
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.util.concurrent.atomic.AtomicLong

private val logger = KotlinLogging.logger {}

@Service
class CompanyRatingUpdateService(
    private val jobplanetWebClient: WebClient,
    private val companyRepository: CompanyRepository,
    private val apiProperties: JobplanetApiProperties
) {
    data class UpdateResult(val updated: Long, val failed: Long)

    fun updateAllRatings(): Mono<UpdateResult> {
        val companies = companyRepository.findCompaniesNeedingRatingUpdate()
        val total = companies.size
        logger.info { "회사 상세 업데이트 시작 - 대상: $total" }

        val updated = AtomicLong(0)
        val failed = AtomicLong(0)
        val processed = AtomicLong(0)

        return Flux.fromIterable(companies)
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
                        .doOnError { failed.incrementAndGet() }
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
            .then(Mono.fromCallable {
                val result = UpdateResult(updated.get(), failed.get())
                logger.info { "회사 상세 업데이트 완료 - 성공: ${result.updated}, 실패: ${result.failed}" }
                result
            })
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
            .onErrorResume { Mono.empty() }
    }

    private fun fetchLandingHeader(jobplanetId: Long): Mono<LandingHeaderData> {
        return jobplanetWebClient.get()
            .uri("/api/v5/companies/$jobplanetId/landing/header")
            .retrieve()
            .bodyToMono(LandingHeaderResponse::class.java)
            .flatMap { response ->
                val data = response.data
                if (data?.name != null) Mono.just(data) else Mono.empty()
            }
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
