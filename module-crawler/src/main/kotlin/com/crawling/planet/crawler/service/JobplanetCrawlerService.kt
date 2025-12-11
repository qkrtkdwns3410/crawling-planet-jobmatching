package com.crawling.planet.crawler.service

import com.crawling.planet.crawler.api.JobplanetCrawlerApi
import com.crawling.planet.crawler.config.JobplanetProperties
import io.github.oshai.kotlinlogging.KotlinLogging
import org.openqa.selenium.By
import org.openqa.selenium.WebDriver
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait
import org.springframework.stereotype.Service
import java.time.Duration

private val logger = KotlinLogging.logger {}

/**
 * 잡플래닛 크롤러 서비스
 */
@Service
class JobplanetCrawlerService(
    private val webDriver: WebDriver,
    private val jobplanetProperties: JobplanetProperties
) : JobplanetCrawlerApi {

    /**
     * 잡플래닛 로그인 수행
     */
    override fun login(): Boolean {
        logger.info { "잡플래닛 로그인 시작..." }
        logger.info { "로그인 URL: ${jobplanetProperties.loginUrl}" }
        logger.info { "이메일: ${jobplanetProperties.email}" }

        return try {
            // 1. 로그인 페이지 접속
            webDriver.get(jobplanetProperties.loginUrl)
            logger.info { "로그인 페이지 접속 완료" }

            val wait = WebDriverWait(webDriver, Duration.ofSeconds(jobplanetProperties.crawler.timeoutSeconds))

            // 2. 이메일 입력 필드 대기 및 입력
            val emailInput = wait.until(
                ExpectedConditions.presenceOfElementLocated(By.name("email"))
            )
            emailInput.clear()
            emailInput.sendKeys(jobplanetProperties.email)
            logger.info { "이메일 입력 완료" }

            // 3. 비밀번호 입력 필드 찾기 및 입력
            val passwordInput = wait.until(
                ExpectedConditions.presenceOfElementLocated(By.name("password"))
            )
            passwordInput.clear()
            passwordInput.sendKeys(jobplanetProperties.password)
            logger.info { "비밀번호 입력 완료" }

            // 4. 로그인 버튼 클릭
            val loginButton = wait.until(
                ExpectedConditions.elementToBeClickable(
                    By.xpath("//button[@type='submit' and contains(text(), '이메일로 로그인')]")
                )
            )
            loginButton.click()
            logger.info { "로그인 버튼 클릭 완료" }

            // 5. 로그인 성공 확인 (URL 변경 또는 특정 요소 확인)
            Thread.sleep(3000) // 로그인 처리 대기

            val currentUrl = webDriver.currentUrl
            val isLoginSuccessful = currentUrl?.let { !it.contains("sign-in") } == true

            if (isLoginSuccessful) {
                logger.info { "로그인 성공! 현재 URL: $currentUrl" }
            } else {
                logger.warn { "로그인 실패. 현재 URL: $currentUrl" }
            }

            isLoginSuccessful
        } catch (e: Exception) {
            logger.error(e) { "로그인 중 오류 발생: ${e.message}" }
            false
        }
    }

    /**
     * 현재 페이지 URL 반환
     */
    override fun getCurrentUrl(): String? = webDriver.currentUrl

    /**
     * 특정 URL로 이동
     */
    override fun navigateTo(url: String) {
        logger.info { "페이지 이동: $url" }
        webDriver.get(url)
    }

    /**
     * 브라우저 종료
     */
    override fun close() {
        logger.info { "브라우저 종료..." }
        try {
            webDriver.quit()
        } catch (e: Exception) {
            logger.warn { "브라우저 종료 중 오류: ${e.message}" }
        }
    }
}

