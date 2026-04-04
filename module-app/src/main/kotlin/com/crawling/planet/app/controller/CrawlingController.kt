package com.crawling.planet.app.controller

import com.crawling.planet.app.dto.CrawlResponse
import com.crawling.planet.app.dto.StatusResponse
import com.crawling.planet.crawler.auth.JobplanetLoginService
import com.crawling.planet.crawler.diagnostics.CrawlerDiagnosticsService
import com.crawling.planet.crawler.service.CompanyRatingUpdateService
import com.crawling.planet.crawler.service.JobplanetCrawlingService
import com.crawling.planet.domain.repository.CompanyRepository
import com.crawling.planet.domain.repository.ReviewRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/api/crawling")
class CrawlingController(
    private val crawlingService: JobplanetCrawlingService,
    private val companyRepository: CompanyRepository,
    private val reviewRepository: ReviewRepository,
    private val loginService: JobplanetLoginService,
    private val companyRatingUpdateService: CompanyRatingUpdateService,
    private val diagnosticsService: CrawlerDiagnosticsService
) {
    @GetMapping("/status")
    fun getStatus(): ResponseEntity<StatusResponse> {
        val companyCount = companyRepository.count()
        val reviewCount = reviewRepository.count()
        return ResponseEntity.ok(StatusResponse(totalCompanies = companyCount, totalReviews = reviewCount))
    }

    @PostMapping("/company/{companyId}")
    fun crawlSingleCompany(@PathVariable companyId: Long): ResponseEntity<CrawlResponse> {
        logger.info { "단일 회사 크롤링 요청 - companyId: $companyId" }
        diagnosticsService.recordJobStarted("single-company", "companyId=$companyId")
        loginService.loginIfNeeded()
        val result = crawlingService.crawlSingleCompany(companyId)
        return if (result != null) {
            diagnosticsService.recordJobFinished(
                "single-company",
                true,
                "companies=${result.companiesSaved}, reviews=${result.reviewsSaved}"
            )
            ResponseEntity.ok(CrawlResponse(true, "크롤링 완료", result.companiesSaved, result.reviewsSaved))
        } else {
            diagnosticsService.recordJobFinished("single-company", false, "no data or failed")
            ResponseEntity.ok(CrawlResponse(false, "크롤링 실패 또는 데이터 없음", 0, 0))
        }
    }

    @PostMapping("/range")
    fun crawlRange(@RequestParam startId: Long, @RequestParam endId: Long): ResponseEntity<Map<String, String>> {
        if (startId <= 0 || endId < startId || endId - startId > 100_000) {
            return ResponseEntity.badRequest().body(mapOf("error" to "유효하지 않은 범위: startId > 0, endId >= startId, 범위 <= 100,000"))
        }
        logger.info { "범위 크롤링 요청 - $startId ~ $endId" }
        diagnosticsService.recordJobStarted("range", "$startId~$endId")
        loginService.loginIfNeeded()
        crawlingService.crawlRange(startId, endId)
            .subscribe(
                { result ->
                    diagnosticsService.recordJobFinished(
                        "range",
                        true,
                        "companies=${result.totalCompanies}, reviews=${result.totalReviews}, failed=${result.failedCompanies}"
                    )
                    logger.info { "범위 크롤링 완료 ($startId~$endId): 회사=${result.totalCompanies}, 리뷰=${result.totalReviews}, 실패=${result.failedCompanies}" }
                },
                { error ->
                    diagnosticsService.recordJobFinished("range", false, error.message)
                    logger.error(error) { "범위 크롤링 실패 ($startId~$endId)" }
                }
            )
        return ResponseEntity.ok(mapOf("status" to "started", "message" to "크롤링이 백그라운드에서 시작되었습니다 ($startId ~ $endId)."))
    }

    @PostMapping("/start")
    fun startFullCrawling(): ResponseEntity<Map<String, String>> {
        logger.info { "전체 크롤링 시작 요청" }
        diagnosticsService.recordJobStarted("full-crawling")
        loginService.loginIfNeeded()
        crawlingService.startCrawling()
            .subscribe(
                { result ->
                    diagnosticsService.recordJobFinished(
                        "full-crawling",
                        true,
                        "companies=${result.totalCompanies}, reviews=${result.totalReviews}, failed=${result.failedCompanies}"
                    )
                    logger.info { "전체 크롤링 완료: $result" }
                },
                { error ->
                    diagnosticsService.recordJobFinished("full-crawling", false, error.message)
                    logger.error(error) { "전체 크롤링 실패" }
                }
            )
        return ResponseEntity.ok(mapOf("status" to "started", "message" to "크롤링이 백그라운드에서 시작되었습니다."))
    }

    @PostMapping("/update-reviews")
    fun updateReviews(): ResponseEntity<Map<String, String>> {
        logger.info { "DB 기반 리뷰 수집 요청" }
        diagnosticsService.recordJobStarted("update-reviews")
        loginService.loginIfNeeded()
        crawlingService.crawlReviewsForExistingCompanies()
            .subscribe(
                { result ->
                    diagnosticsService.recordJobFinished(
                        "update-reviews",
                        true,
                        "companies=${result.totalCompanies}, reviews=${result.totalReviews}, failed=${result.failedCompanies}"
                    )
                    logger.info { "리뷰 수집 완료: 회사=${result.totalCompanies}, 리뷰=${result.totalReviews}, 실패=${result.failedCompanies}" }
                },
                { error ->
                    diagnosticsService.recordJobFinished("update-reviews", false, error.message)
                    logger.error(error) { "리뷰 수집 실패" }
                }
            )
        return ResponseEntity.ok(mapOf("status" to "started", "message" to "DB 기반 리뷰 수집이 시작되었습니다."))
    }

    @PostMapping("/update-ratings")
    fun updateRatings(): ResponseEntity<Map<String, String>> {
        logger.info { "회사 상세 업데이트 요청" }
        diagnosticsService.recordJobStarted("update-ratings")
        loginService.loginIfNeeded()
        companyRatingUpdateService.updateAllRatings()
            .subscribe(
                { result ->
                    diagnosticsService.recordJobFinished(
                        "update-ratings",
                        true,
                        "updated=${result.updated}, failed=${result.failed}"
                    )
                    logger.info { "회사 상세 업데이트 완료: 성공=${result.updated}, 실패=${result.failed}" }
                },
                { error ->
                    diagnosticsService.recordJobFinished("update-ratings", false, error.message)
                    logger.error(error) { "회사 상세 업데이트 실패" }
                }
            )
        return ResponseEntity.ok(mapOf("status" to "started", "message" to "회사 상세 업데이트가 백그라운드에서 시작되었습니다."))
    }

    @GetMapping("/diagnostics")
    fun getDiagnostics(): ResponseEntity<CrawlerDiagnosticsService.Snapshot> {
        return ResponseEntity.ok(diagnosticsService.snapshot())
    }

}
