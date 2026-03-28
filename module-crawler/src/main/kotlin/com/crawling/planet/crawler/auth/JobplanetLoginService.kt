package com.crawling.planet.crawler.auth

import io.github.bonigarcia.wdm.WebDriverManager
import io.github.oshai.kotlinlogging.KotlinLogging
import org.openqa.selenium.By
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait
import org.springframework.stereotype.Service
import java.time.Duration

private val logger = KotlinLogging.logger {}

@Service
class JobplanetLoginService(
    private val authProperties: JobplanetAuthProperties,
    private val cookieTokenStore: CookieTokenStore
) {
    init {
        WebDriverManager.chromedriver().setup()
    }

    fun loginIfNeeded(): TokenPair {
        if (cookieTokenStore.hasToken() && !cookieTokenStore.isExpired()) {
            logger.info { "기존 토큰이 유효하여 재사용" }
            return cookieTokenStore.get()!!
        }
        return login()
    }

    fun login(): TokenPair {
        logger.info { "잡플래닛 로그인 시작 - email: ${authProperties.email}" }

        val options = ChromeOptions().apply {
            if (authProperties.headless) {
                addArguments("--headless=new")
            }
            addArguments("--no-sandbox")
            addArguments("--disable-dev-shm-usage")
            addArguments("--disable-gpu")
            addArguments("--window-size=1920,1080")
            addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36")
        }

        val driver = ChromeDriver(options)
        try {
            driver.get(authProperties.loginUrl)

            val wait = WebDriverWait(driver, Duration.ofSeconds(authProperties.loginTimeoutSeconds))

            val emailInput = wait.until(
                ExpectedConditions.presenceOfElementLocated(By.cssSelector("input[name='email']"))
            )
            emailInput.clear()
            emailInput.sendKeys(authProperties.email)

            val passwordInput = driver.findElement(
                By.cssSelector("input[name='password']")
            )
            passwordInput.clear()
            passwordInput.sendKeys(authProperties.password)

            val submitButton = driver.findElement(
                By.cssSelector("button[type='submit']")
            )
            submitButton.click()

            wait.until(
                ExpectedConditions.not(ExpectedConditions.urlContains("sign-in"))
            )

            Thread.sleep(2000)

            val cookies = driver.manage().cookies
            val accessToken = cookies.find { it.name == "access_token" }?.value
            val refreshToken = cookies.find { it.name == "refresh_token" }?.value

            if (accessToken.isNullOrBlank()) {
                val allCookieNames = cookies.map { it.name }
                throw IllegalStateException("로그인 후 access_token 쿠키를 찾을 수 없습니다. 쿠키 목록: $allCookieNames")
            }

            val allCookieMap = cookies.associate { it.name to it.value }
            logger.info { "수집된 쿠키 목록: ${allCookieMap.keys}" }

            val tokenPair = TokenPair(
                accessToken = accessToken,
                refreshToken = refreshToken ?: "",
                allCookies = allCookieMap
            )

            cookieTokenStore.store(tokenPair)
            logger.info { "잡플래닛 로그인 성공" }

            return tokenPair
        } catch (e: Exception) {
            logger.error(e) { "잡플래닛 로그인 실패: ${e.message}" }
            throw e
        } finally {
            driver.quit()
        }
    }
}
