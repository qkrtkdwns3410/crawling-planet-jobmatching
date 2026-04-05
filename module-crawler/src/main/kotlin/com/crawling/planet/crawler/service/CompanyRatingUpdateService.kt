package com.crawling.planet.crawler.service

import com.crawling.planet.crawler.auth.CookieTokenStore
import com.crawling.planet.crawler.config.JobplanetApiProperties
import com.crawling.planet.domain.repository.CompanyRepository
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import okhttp3.OkHttpClient
import okhttp3.Request
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong

private val logger = KotlinLogging.logger {}

@Service
class CompanyRatingUpdateService(
    private val jobplanetOkHttpClient: OkHttpClient,
    private val cookieTokenStore: CookieTokenStore,
    private val companyRepository: CompanyRepository,
    private val apiProperties: JobplanetApiProperties,
    private val objectMapper: ObjectMapper
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
        return Mono.fromCallable {
            val url = "https://www.jobplanet.co.kr/api/v5/companies/$jobplanetId/landing/header"
            val request = Request.Builder()
                .url(url)
                .get()
                .header("Cookie", cookieTokenStore.buildCookieString())
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36")
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

            jobplanetOkHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val body = response.body?.string() ?: throw IOException("Empty body")
                objectMapper.readValue(body, LandingHeaderResponse::class.java)
            }
        }
            .subscribeOn(Schedulers.boundedElastic())
            .flatMap { response ->
                val data = response.data
                if (data != null && (data.rateTotalAvg != null || data.name != null)) Mono.just(data) else Mono.empty()
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
