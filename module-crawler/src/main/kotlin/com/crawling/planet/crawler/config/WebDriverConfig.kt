package com.crawling.planet.crawler.config

import io.github.bonigarcia.wdm.WebDriverManager
import io.github.oshai.kotlinlogging.KotlinLogging
import org.openqa.selenium.WebDriver
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

private val logger = KotlinLogging.logger {}

/**
 * WebDriver 설정 클래스
 */
@Configuration
@EnableConfigurationProperties(JobplanetProperties::class)
class WebDriverConfig(
    private val jobplanetProperties: JobplanetProperties
) {

    @Bean
    fun webDriver(): WebDriver {
        logger.info { "ChromeDriver 설정 시작..." }
        
        // WebDriverManager로 ChromeDriver 자동 설정
        WebDriverManager.chromedriver().setup()

        val options = ChromeOptions().apply {
            // 헤드리스 모드 설정
            if (jobplanetProperties.crawler.headless) {
                addArguments("--headless=new")
            }
            
            // 기본 옵션
            addArguments("--no-sandbox")
            addArguments("--disable-dev-shm-usage")
            addArguments("--disable-gpu")
            addArguments("--window-size=1920,1080")
            addArguments("--disable-blink-features=AutomationControlled")
            
            // User-Agent 설정 (봇 탐지 회피)
            addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
            
            // 자동화 탐지 비활성화
            setExperimentalOption("excludeSwitches", listOf("enable-automation"))
            setExperimentalOption("useAutomationExtension", false)
        }

        val driver = ChromeDriver(options)
        
        // 암묵적 대기 설정
        driver.manage().timeouts().implicitlyWait(
            Duration.ofSeconds(jobplanetProperties.crawler.implicitWaitSeconds)
        )
        
        // 페이지 로드 타임아웃 설정
        driver.manage().timeouts().pageLoadTimeout(
            Duration.ofSeconds(jobplanetProperties.crawler.timeoutSeconds)
        )

        logger.info { "ChromeDriver 설정 완료" }
        return driver
    }
}

