package com.crawling.planet.crawler.runner

import com.crawling.planet.crawler.auth.JobplanetLoginService
import com.crawling.planet.crawler.config.JobplanetApiProperties
import com.crawling.planet.crawler.service.JobplanetCrawlingService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDateTime

private val logger = KotlinLogging.logger {}

@Component
@ConditionalOnProperty(
    prefix = "jobplanet.api",
    name = ["auto-start"],
    havingValue = "true",
    matchIfMissing = false
)
class CrawlerRunner(
    private val crawlingService: JobplanetCrawlingService,
    private val apiProperties: JobplanetApiProperties,
    private val loginService: JobplanetLoginService
) : ApplicationRunner {

    override fun run(args: ApplicationArguments?) {
        logger.info { "=".repeat(60) }
        logger.info { "잡플래닛 API 크롤러 시작" }
        logger.info { "=".repeat(60) }

        val startTime = LocalDateTime.now()
        logger.info { "시작 시간: $startTime" }
        logger.info { "크롤링 범위: ${apiProperties.startCompanyId} ~ ${apiProperties.endCompanyId}" }
        logger.info { "배치 크기: ${apiProperties.batchSize}, 동시 처리: ${apiProperties.concurrency}" }
        logger.info { "=".repeat(60) }

        try {
            loginService.loginIfNeeded()
            logger.info { "로그인 완료, 크롤링을 시작합니다" }

            val result = crawlingService.startCrawling().block()

            val endTime = LocalDateTime.now()
            val duration = Duration.between(startTime, endTime)

            logger.info { "=".repeat(60) }
            logger.info { "크롤링 완료!" }
            logger.info { "=".repeat(60) }
            logger.info { "소요 시간: ${duration.toHours()}시간 ${duration.toMinutesPart()}분 ${duration.toSecondsPart()}초" }
            logger.info { "수집된 회사: ${result?.totalCompanies ?: 0}" }
            logger.info { "수집된 리뷰: ${result?.totalReviews ?: 0}" }
            logger.info { "스킵된 리뷰 (중복): ${result?.skippedReviews ?: 0}" }
            logger.info { "실패한 회사: ${result?.failedCompanies ?: 0}" }
            logger.info { "=".repeat(60) }

        } catch (e: Exception) {
            logger.error(e) { "크롤러 실행 중 오류 발생: ${e.message}" }
        }
    }
}

