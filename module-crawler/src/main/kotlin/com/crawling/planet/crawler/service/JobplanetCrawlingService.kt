package com.crawling.planet.crawler.service

import com.crawling.planet.crawler.config.JobplanetApiProperties
import com.crawling.planet.domain.dto.JobplanetApiResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong

private val logger = KotlinLogging.logger {}

/**
 * 잡플래닛 크롤링 조율 서비스
 */
@Service
class JobplanetCrawlingService(
    private val jobplanetApiService: JobplanetApiService,
    private val reviewDataService: ReviewDataService,
    private val apiProperties: JobplanetApiProperties
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

    /**
     * 특정 회사 크롤링
     */
    fun crawlCompany(companyId: Long): Mono<ReviewDataService.SaveResult?> {
        return jobplanetApiService.getAllCompanyReviews(companyId)
            .collectList()
            .filter { it.isNotEmpty() }
            .publishOn(Schedulers.boundedElastic())
            .map { responses ->
                reviewDataService.saveFromApiResponses(companyId, responses)
            }
            .onErrorResume { error ->
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
     * 크롤링 결과
     */
    data class CrawlingResult(
        val totalCompanies: Long,
        val totalReviews: Long,
        val skippedReviews: Long,
        val failedCompanies: Long
    )
}


