package com.crawling.planet.app.controller

import com.crawling.planet.app.dto.CrawlResponse
import com.crawling.planet.app.dto.StatusResponse
import com.crawling.planet.crawler.auth.JobplanetLoginService
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
    private val companyRatingUpdateService: CompanyRatingUpdateService
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
        loginService.loginIfNeeded()
        val result = crawlingService.crawlSingleCompany(companyId)
        return if (result != null) {
            ResponseEntity.ok(CrawlResponse(true, "크롤링 완료", result.companiesSaved, result.reviewsSaved, result.reviewsSkipped))
        } else {
            ResponseEntity.ok(CrawlResponse(false, "크롤링 실패 또는 데이터 없음", 0, 0, 0))
        }
    }

    @PostMapping("/range")
    fun crawlRange(@RequestParam startId: Long, @RequestParam endId: Long): ResponseEntity<Map<String, String>> {
        logger.info { "범위 크롤링 요청 - $startId ~ $endId" }
        loginService.loginIfNeeded()
        crawlingService.crawlRange(startId, endId)
            .subscribe(
                { result -> logger.info { "범위 크롤링 완료 ($startId~$endId): 회사=${result.totalCompanies}, 리뷰=${result.totalReviews}, 실패=${result.failedCompanies}" } },
                { error -> logger.error(error) { "범위 크롤링 실패 ($startId~$endId)" } }
            )
        return ResponseEntity.ok(mapOf("status" to "started", "message" to "크롤링이 백그라운드에서 시작되었습니다 ($startId ~ $endId)."))
    }

    @PostMapping("/start")
    fun startFullCrawling(): ResponseEntity<Map<String, String>> {
        logger.info { "전체 크롤링 시작 요청" }
        loginService.loginIfNeeded()
        crawlingService.startCrawling()
            .subscribe(
                { result -> logger.info { "전체 크롤링 완료: $result" } },
                { error -> logger.error(error) { "전체 크롤링 실패" } }
            )
        return ResponseEntity.ok(mapOf("status" to "started", "message" to "크롤링이 백그라운드에서 시작되었습니다."))
    }

    @PostMapping("/update-reviews")
    fun updateReviews(): ResponseEntity<Map<String, String>> {
        logger.info { "DB 기반 리뷰 수집 요청" }
        loginService.loginIfNeeded()
        crawlingService.crawlReviewsForExistingCompanies()
            .subscribe(
                { result -> logger.info { "리뷰 수집 완료: 회사=${result.totalCompanies}, 리뷰=${result.totalReviews}, 실패=${result.failedCompanies}" } },
                { error -> logger.error(error) { "리뷰 수집 실패" } }
            )
        return ResponseEntity.ok(mapOf("status" to "started", "message" to "DB 기반 리뷰 수집이 시작되었습니다."))
    }

    @PostMapping("/update-ratings")
    fun updateRatings(): ResponseEntity<Map<String, String>> {
        logger.info { "회사 상세 업데이트 요청" }
        loginService.loginIfNeeded()
        companyRatingUpdateService.updateAllRatings()
            .subscribe(
                { result -> logger.info { "회사 상세 업데이트 완료: 성공=${result.updated}, 실패=${result.failed}" } },
                { error -> logger.error(error) { "회사 상세 업데이트 실패" } }
            )
        return ResponseEntity.ok(mapOf("status" to "started", "message" to "회사 상세 업데이트가 백그라운드에서 시작되었습니다."))
    }

}
