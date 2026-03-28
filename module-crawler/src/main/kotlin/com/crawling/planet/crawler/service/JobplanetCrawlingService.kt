package com.crawling.planet.crawler.service

import com.crawling.planet.crawler.auth.JobplanetLoginService
import com.crawling.planet.crawler.config.JobplanetApiProperties
import com.crawling.planet.domain.repository.CompanyRepository
import io.github.oshai.kotlinlogging.KotlinLogging
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
    private val companyRepository: CompanyRepository
) {
    /**
     * 전체 크롤링 실행 (비동기)
     */
    fun startCrawling(): Mono<CrawlingResult> {
        val startId = apiProperties.startCompanyId
        val endId = apiProperties.endCompanyId

        logger.info { "크롤링 시작 - 범위: $startId ~ $endId" }

        val totalCompanies = AtomicLong(0)
        val totalReviews = AtomicLong(0)
        val totalSkipped = AtomicLong(0)
        val failedCompanies = AtomicLong(0)
        val processedCompanies = AtomicLong(0)

        val totalToProcess = endId - startId + 1

        return Flux.range(startId.toInt(), (endId - startId + 1).toInt())
            .map { it.toLong() }
            .delayElements(Duration.ofMillis(apiProperties.delayMs))
            .flatMap(
                { companyId ->
                    crawlCompany(companyId)
                        .doOnSuccess { result ->
                            val processed = processedCompanies.incrementAndGet()
                            if (result != null) {
                                totalCompanies.addAndGet(result.companiesSaved.toLong())
                                totalReviews.addAndGet(result.reviewsSaved.toLong())
                                totalSkipped.addAndGet(result.reviewsSkipped.toLong())
                            } else {
                                failedCompanies.incrementAndGet()
                            }

                            // 진행 상황 로깅 (1000개마다)
                            if (processed % 1000 == 0L) {
                                val progress = (processed.toDouble() / totalToProcess * 100).toInt()
                                logger.info {
                                    "진행률: $progress% ($processed/$totalToProcess) - " +
                                    "회사: ${totalCompanies.get()}, 리뷰: ${totalReviews.get()}, " +
                                    "실패: ${failedCompanies.get()}"
                                }
                            }
                        }
                        .doOnError { error ->
                            failedCompanies.incrementAndGet()
                            logger.error(error) { "크롤링 실패 - companyId: $companyId" }
                        }
                        .onErrorResume { Mono.empty() }
                },
                apiProperties.concurrency
            )
            .then(Mono.fromCallable {
                CrawlingResult(
                    totalCompanies = totalCompanies.get(),
                    totalReviews = totalReviews.get(),
                    skippedReviews = totalSkipped.get(),
                    failedCompanies = failedCompanies.get()
                )
            })
            .doOnSuccess { result ->
                logger.info { 
                    "크롤링 완료 - 총 회사: ${result.totalCompanies}, " +
                    "총 리뷰: ${result.totalReviews}, " +
                    "스킵: ${result.skippedReviews}, " +
                    "실패: ${result.failedCompanies}" 
                }
            }
    }

    fun crawlCompany(companyId: Long): Mono<ReviewDataService.SaveResult?> {
        return jobplanetApiService.getCompanyReviews(companyId, 1)
            .publishOn(Schedulers.boundedElastic())
            .map { response ->
                reviewDataService.saveFromApiResponse(companyId, response)
            }
            .onErrorResume { error ->
                if (error is WebClientResponseException && error.statusCode.value() == 401) {
                    logger.warn { "401 인증 만료 - 토큰 갱신 후 재시도 (companyId: $companyId)" }
                    return@onErrorResume Mono.fromCallable { loginService.login() }
                        .then(jobplanetApiService.getCompanyReviews(companyId, 1))
                        .publishOn(Schedulers.boundedElastic())
                        .map { response -> reviewDataService.saveFromApiResponse(companyId, response) }
                        .onErrorResume { retryError ->
                            logger.error { "토큰 갱신 후에도 실패 - companyId: $companyId, error: ${retryError.message}" }
                            Mono.empty()
                        }
                }
                logger.warn { "회사 크롤링 실패 - companyId: $companyId, error: ${error.message}" }
                Mono.empty()
            }
    }

    /**
     * 특정 범위 크롤링
     */
    fun crawlRange(startId: Long, endId: Long): Mono<CrawlingResult> {
        logger.info { "범위 크롤링 시작 - $startId ~ $endId" }

        val totalCompanies = AtomicLong(0)
        val totalReviews = AtomicLong(0)
        val totalSkipped = AtomicLong(0)
        val failedCompanies = AtomicLong(0)

        return Flux.range(startId.toInt(), (endId - startId + 1).toInt())
            .map { it.toLong() }
            .delayElements(Duration.ofMillis(apiProperties.delayMs))
            .flatMap(
                { companyId ->
                    crawlCompany(companyId)
                        .doOnSuccess { result ->
                            if (result != null) {
                                totalCompanies.addAndGet(result.companiesSaved.toLong())
                                totalReviews.addAndGet(result.reviewsSaved.toLong())
                                totalSkipped.addAndGet(result.reviewsSkipped.toLong())
                            } else {
                                failedCompanies.incrementAndGet()
                            }
                        }
                        .onErrorResume { Mono.empty() }
                },
                apiProperties.concurrency
            )
            .then(Mono.fromCallable {
                CrawlingResult(
                    totalCompanies = totalCompanies.get(),
                    totalReviews = totalReviews.get(),
                    skippedReviews = totalSkipped.get(),
                    failedCompanies = failedCompanies.get()
                )
            })
    }

    /**
     * 단일 회사 크롤링 (동기)
     */
    fun crawlSingleCompany(companyId: Long): ReviewDataService.SaveResult? {
        return crawlCompany(companyId).block()
    }

    /**
     * DB에 있는 회사 기준으로 리뷰 수집
     */
    fun crawlReviewsForExistingCompanies(): Mono<CrawlingResult> {
        val companies = companyRepository.findCompaniesNeedingReviews()
        val total = companies.size
        logger.info { "DB 기반 리뷰 수집 시작 - 대상 회사: $total" }

        val totalReviews = AtomicLong(0)
        val failed = AtomicLong(0)
        val processed = AtomicLong(0)

        return Flux.fromIterable(companies)
            .flatMap(
                { company ->
                    crawlCompany(company.jobplanetId)
                        .doOnSuccess { result ->
                            val count = processed.incrementAndGet()
                            if (result != null) {
                                totalReviews.addAndGet(result.reviewsSaved.toLong())
                            }
                            if (count % 1000 == 0L) {
                                logger.info { "진행: $count/$total - 리뷰: ${totalReviews.get()}, 실패: ${failed.get()}" }
                            }
                        }
                        .doOnError { failed.incrementAndGet() }
                        .onErrorResume { Mono.empty() }
                },
                apiProperties.concurrency
            )
            .then(Mono.fromCallable {
                CrawlingResult(
                    totalCompanies = processed.get(),
                    totalReviews = totalReviews.get(),
                    skippedReviews = 0,
                    failedCompanies = failed.get()
                )
            })
    }

    data class CrawlingResult(
        val totalCompanies: Long,
        val totalReviews: Long,
        val skippedReviews: Long,
        val failedCompanies: Long
    )
}



