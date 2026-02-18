package com.crawling.planet.app.controller

import com.crawling.planet.crawler.auth.JobplanetLoginService
import com.crawling.planet.crawler.service.JobplanetCrawlingService
import com.crawling.planet.crawler.service.ReviewDataService
import com.crawling.planet.domain.repository.CompanyRepository
import com.crawling.planet.domain.repository.ReviewRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Mono

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/api/crawling")
class CrawlingController(
    private val crawlingService: JobplanetCrawlingService,
    private val companyRepository: CompanyRepository,
    private val reviewRepository: ReviewRepository,
    private val loginService: JobplanetLoginService
) {
    /**
     * 크롤링 상태 조회
     */
    @GetMapping("/status")
    fun getStatus(): ResponseEntity<StatusResponse> {
        val companyCount = companyRepository.count()
        val reviewCount = reviewRepository.count()
        
        return ResponseEntity.ok(
            StatusResponse(
                totalCompanies = companyCount,
                totalReviews = reviewCount
            )
        )
    }

    /**
     * 단일 회사 크롤링
     */
    @PostMapping("/company/{companyId}")
    fun crawlSingleCompany(
        @PathVariable companyId: Long
    ): ResponseEntity<CrawlResponse> {
        logger.info { "단일 회사 크롤링 요청 - companyId: $companyId" }

        loginService.loginIfNeeded()

        val result = crawlingService.crawlSingleCompany(companyId)
        
        return if (result != null) {
            ResponseEntity.ok(
                CrawlResponse(
                    success = true,
                    message = "크롤링 완료",
                    companiesSaved = result.companiesSaved,
                    reviewsSaved = result.reviewsSaved,
                    reviewsSkipped = result.reviewsSkipped
                )
            )
        } else {
            ResponseEntity.ok(
                CrawlResponse(
                    success = false,
                    message = "크롤링 실패 또는 데이터 없음",
                    companiesSaved = 0,
                    reviewsSaved = 0,
                    reviewsSkipped = 0
                )
            )
        }
    }

    /**
     * 범위 크롤링 (비동기)
     */
    @PostMapping("/range")
    fun crawlRange(
        @RequestParam startId: Long,
        @RequestParam endId: Long
    ): Mono<ResponseEntity<CrawlRangeResponse>> {
        logger.info { "범위 크롤링 요청 - $startId ~ $endId" }

        loginService.loginIfNeeded()

        return crawlingService.crawlRange(startId, endId)
            .map { result ->
                ResponseEntity.ok(
                    CrawlRangeResponse(
                        success = true,
                        message = "범위 크롤링 완료",
                        totalCompanies = result.totalCompanies,
                        totalReviews = result.totalReviews,
                        skippedReviews = result.skippedReviews,
                        failedCompanies = result.failedCompanies
                    )
                )
            }
    }

    /**
     * 전체 크롤링 시작 (비동기, 백그라운드)
     */
    @PostMapping("/start")
    fun startFullCrawling(): ResponseEntity<Map<String, String>> {
        logger.info { "전체 크롤링 시작 요청" }

        loginService.loginIfNeeded()

        crawlingService.startCrawling()
            .subscribe(
                { result ->
                    logger.info { "전체 크롤링 완료: $result" }
                },
                { error ->
                    logger.error(error) { "전체 크롤링 실패" }
                }
            )
        
        return ResponseEntity.ok(
            mapOf(
                "status" to "started",
                "message" to "크롤링이 백그라운드에서 시작되었습니다. /api/crawling/status 에서 진행 상황을 확인하세요."
            )
        )
    }

    // Response DTOs
    data class StatusResponse(
        val totalCompanies: Long,
        val totalReviews: Long
    )

    data class CrawlResponse(
        val success: Boolean,
        val message: String,
        val companiesSaved: Int,
        val reviewsSaved: Int,
        val reviewsSkipped: Int
    )

    data class CrawlRangeResponse(
        val success: Boolean,
        val message: String,
        val totalCompanies: Long,
        val totalReviews: Long,
        val skippedReviews: Long,
        val failedCompanies: Long
    )
}



