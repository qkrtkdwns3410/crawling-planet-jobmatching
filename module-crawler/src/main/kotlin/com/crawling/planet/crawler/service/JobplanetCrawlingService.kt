package com.crawling.planet.crawler.service

import com.crawling.planet.crawler.auth.JobplanetLoginService
import com.crawling.planet.crawler.config.JobplanetApiProperties
import com.crawling.planet.domain.repository.CompanyRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong

private val logger = KotlinLogging.logger {}

@Service
class JobplanetCrawlingService(
    private val jobplanetApiService: JobplanetApiService,
    private val reviewDataService: ReviewDataService,
    private val apiProperties: JobplanetApiProperties,
    private val loginService: JobplanetLoginService,
    private val companyRepository: CompanyRepository,
    private val companyRatingUpdateService: CompanyRatingUpdateService
) {
    companion object {
        private const val PAGE_SIZE = 1000
    }

    fun startCrawling(): Mono<CrawlingResult> {
        return crawlRange(apiProperties.startCompanyId, apiProperties.endCompanyId)
            .flatMap { result ->
                logger.info { "전체 크롤링 완료, 평점 일괄 업데이트 시작" }
                companyRatingUpdateService.updateAllRatings()
                    .doOnSuccess { ratingResult ->
                        logger.info { "평점 업데이트 완료 - 성공: ${ratingResult.updated}, 실패: ${ratingResult.failed}" }
                    }
                    .thenReturn(result)
            }
    }

    fun crawlRange(startId: Long, endId: Long): Mono<CrawlingResult> {
        logger.info { "크롤링 시작 - 범위: $startId ~ $endId" }

        val totalCompanies = AtomicLong(0)
        val totalReviews = AtomicLong(0)
        val failedCompanies = AtomicLong(0)
        val processed = AtomicLong(0)
        val totalToProcess = endId - startId + 1

        return Flux.range(startId.toInt(), totalToProcess.toInt())
            .map { it.toLong() }
            .delayElements(Duration.ofMillis(apiProperties.delayMs))
            .flatMap(
                { companyId ->
                    crawlCompany(companyId)
                        .doOnSuccess { result ->
                            val count = processed.incrementAndGet()
                            if (result != null) {
                                totalCompanies.addAndGet(result.companiesSaved.toLong())
                                totalReviews.addAndGet(result.reviewsSaved.toLong())
                            } else {
                                failedCompanies.incrementAndGet()
                            }
                            if (count % 1000 == 0L) {
                                val progress = (count.toDouble() / totalToProcess * 100).toInt()
                                logger.info { "진행률: $progress% ($count/$totalToProcess) - 회사: ${totalCompanies.get()}, 리뷰: ${totalReviews.get()}, 실패: ${failedCompanies.get()}" }
                            }
                        }
                        .onErrorResume { Mono.empty() }
                },
                apiProperties.concurrency
            )
            .then(Mono.fromCallable {
                CrawlingResult(totalCompanies.get(), totalReviews.get(), failedCompanies.get()).also {
                    logger.info { "크롤링 완료 - 회사: ${it.totalCompanies}, 리뷰: ${it.totalReviews}, 실패: ${it.failedCompanies}" }
                }
            })
    }

    fun crawlCompany(companyId: Long): Mono<ReviewDataService.SaveResult?> {
        return fetchAndSaveCompany(companyId)
            .onErrorResume { error ->
                if (error is WebClientResponseException && error.statusCode.value() == 401) {
                    logger.warn { "401 인증 만료 - 토큰 갱신 후 재시도 (companyId: $companyId)" }
                    return@onErrorResume Mono.fromCallable { loginService.login() }
                        .then(fetchAndSaveCompany(companyId))
                        .onErrorResume { retryError ->
                            logger.error { "토큰 갱신 후에도 실패 - companyId: $companyId, error: ${retryError.message}" }
                            Mono.empty()
                        }
                }
                logger.warn { "크롤링 실패 - companyId: $companyId, error: ${error.message}" }
                Mono.empty()
            }
    }

    private fun fetchAndSaveCompany(companyId: Long): Mono<ReviewDataService.SaveResult> {
        return jobplanetApiService.getCompanyReviews(companyId, 1)
            .publishOn(Schedulers.boundedElastic())
            .map { response -> reviewDataService.saveFromApiResponse(companyId, response) }
            .flatMap { result ->
                companyRatingUpdateService.updateSingleCompanyRating(companyId)
                    .thenReturn(result)
            }
    }

    fun crawlReviewsForExistingCompanies(): Mono<CrawlingResult> {
        val totalReviews = AtomicLong(0)
        val failed = AtomicLong(0)
        val processed = AtomicLong(0)

        return Mono.fromCallable {
            companyRepository.findCompaniesNeedingReviews(PageRequest.of(0, 1)).totalElements
        }.flatMap { total ->
            logger.info { "DB 기반 리뷰 수집 시작 - 대상: $total" }
            val totalPages = ((total + PAGE_SIZE - 1) / PAGE_SIZE).toInt()

            Flux.range(0, totalPages)
                .concatMap { page ->
                    val companies = companyRepository.findCompaniesNeedingReviews(PageRequest.of(page, PAGE_SIZE)).content
                    Flux.fromIterable(companies)
                        .flatMap(
                            { company ->
                                crawlCompany(company.jobplanetId)
                                    .doOnSuccess { result ->
                                        val count = processed.incrementAndGet()
                                        if (result != null) totalReviews.addAndGet(result.reviewsSaved.toLong())
                                        if (count % 1000 == 0L) logger.info { "진행: $count/$total - 리뷰: ${totalReviews.get()}, 실패: ${failed.get()}" }
                                    }
                                    .doOnError { failed.incrementAndGet() }
                                    .onErrorResume { Mono.empty() }
                            },
                            apiProperties.concurrency
                        )
                }
                .then(Mono.fromCallable {
                    CrawlingResult(processed.get(), totalReviews.get(), failed.get())
                })
        }
    }

    data class CrawlingResult(
        val totalCompanies: Long,
        val totalReviews: Long,
        val failedCompanies: Long
    )
}
