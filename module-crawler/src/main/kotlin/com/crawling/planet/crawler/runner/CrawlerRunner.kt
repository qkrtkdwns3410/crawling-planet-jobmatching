package com.crawling.planet.crawler.runner

import com.crawling.planet.crawler.api.JobplanetCrawlerApi
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

/**
 * 애플리케이션 시작 시 크롤러 실행
 */
@Component
class CrawlerRunner(
    private val jobplanetCrawlerApi: JobplanetCrawlerApi
) : ApplicationRunner {

    override fun run(args: ApplicationArguments?) {
        logger.info { "=".repeat(50) }
        logger.info { "잡플래닛 크롤러 시작" }
        logger.info { "=".repeat(50) }

        try {
            // 로그인 수행
            val loginSuccess = jobplanetCrawlerApi.login()

            if (loginSuccess) {
                logger.info { "로그인 성공! 크롤링 준비 완료" }
                // 여기에 추가 크롤링 로직 구현
                // 예: 채용 정보 수집, 기업 리뷰 수집 등
            } else {
                logger.error { "로그인 실패. 크롤링을 진행할 수 없습니다." }
            }
        } catch (e: Exception) {
            logger.error(e) { "크롤러 실행 중 오류 발생: ${e.message}" }
        }

        logger.info { "=".repeat(50) }
        logger.info { "잡플래닛 크롤러 종료" }
        logger.info { "=".repeat(50) }
    }
}

