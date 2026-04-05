package com.crawling.planet.crawler.service

import com.crawling.planet.crawler.auth.CookieTokenStore
import com.crawling.planet.crawler.config.JobplanetApiProperties
import com.crawling.planet.domain.entity.Company
import com.crawling.planet.domain.repository.CompanyRepository
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import reactor.test.StepVerifier
import java.util.Optional

class CompanyRatingUpdateServiceTest {

    private lateinit var wireMockServer: WireMockServer
    private lateinit var companyRepository: CompanyRepository
    private lateinit var cookieTokenStore: CookieTokenStore
    private lateinit var service: CompanyRatingUpdateService

    private val apiProperties = JobplanetApiProperties(
        maxRetries = 2,
        retryDelayMs = 100L,
        concurrency = 5
    )

    @BeforeEach
    fun setUp() {
        wireMockServer = WireMockServer(wireMockConfig().dynamicPort())
        wireMockServer.start()
        configureFor("localhost", wireMockServer.port())

        companyRepository = mock(CompanyRepository::class.java)
        cookieTokenStore = mock(CookieTokenStore::class.java)
        `when`(cookieTokenStore.buildCookieString()).thenReturn("")

        // WireMock 서버로 요청을 리다이렉트하는 OkHttpClient
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val original = chain.request()
                val redirectedUrl = original.url.newBuilder()
                    .scheme("http")
                    .host("localhost")
                    .port(wireMockServer.port())
                    .build()
                chain.proceed(original.newBuilder().url(redirectedUrl).build())
            }
            .build()

        service = CompanyRatingUpdateService(
            jobplanetOkHttpClient = okHttpClient,
            cookieTokenStore = cookieTokenStore,
            companyRepository = companyRepository,
            apiProperties = apiProperties,
            objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        )
    }

    @AfterEach
    fun tearDown() {
        wireMockServer.stop()
    }

    @Test
    fun `정상 응답 - updateCompanyDetails 호출됨`() {
        val jobplanetId = 12345L
        val company = Company(id = 1L, jobplanetId = jobplanetId, name = "기존회사")

        wireMockServer.stubFor(
            get(urlEqualTo("/api/v5/companies/$jobplanetId/landing/header"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """{"data":{"name":"테스트회사","rate_total_avg":4.2,"industry_name":"IT","logo_path":"/logo.png"}}"""
                        )
                )
        )

        `when`(companyRepository.findByJobplanetId(jobplanetId)).thenReturn(Optional.of(company))
        `when`(companyRepository.updateCompanyDetails(anyLong(), any(), any(), any(), any())).thenReturn(1)

        StepVerifier.create(service.updateSingleCompanyRating(jobplanetId))
            .verifyComplete()

        verify(companyRepository).updateCompanyDetails(
            eq(company.id),
            eq("테스트회사"),
            eq(4.2),
            eq("IT"),
            eq("/logo.png")
        )
    }

    @Test
    fun `name이 null이고 rateTotalAvg가 있으면 업데이트됨`() {
        val jobplanetId = 22222L
        val company = Company(id = 2L, jobplanetId = jobplanetId, name = "기존회사")

        wireMockServer.stubFor(
            get(urlEqualTo("/api/v5/companies/$jobplanetId/landing/header"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """{"data":{"name":null,"rate_total_avg":3.7,"industry_name":"제조","logo_path":null}}"""
                        )
                )
        )

        `when`(companyRepository.findByJobplanetId(jobplanetId)).thenReturn(Optional.of(company))
        `when`(companyRepository.updateCompanyDetails(anyLong(), any(), any(), any(), any())).thenReturn(1)

        StepVerifier.create(service.updateSingleCompanyRating(jobplanetId))
            .verifyComplete()

        verify(companyRepository).updateCompanyDetails(
            eq(company.id),
            isNull(),
            eq(3.7),
            eq("제조"),
            isNull()
        )
    }

    @Test
    fun `500 에러 - onErrorResume으로 empty 반환`() {
        val jobplanetId = 33333L

        wireMockServer.stubFor(
            get(urlEqualTo("/api/v5/companies/$jobplanetId/landing/header"))
                .willReturn(
                    aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"error":"Internal Server Error"}""")
                )
        )

        StepVerifier.create(service.updateSingleCompanyRating(jobplanetId))
            .verifyComplete()

        verify(companyRepository, never()).updateCompanyDetails(anyLong(), any(), any(), any(), any())
    }

    @Test
    fun `404 에러 - onErrorResume으로 empty 반환`() {
        val jobplanetId = 44444L

        wireMockServer.stubFor(
            get(urlEqualTo("/api/v5/companies/$jobplanetId/landing/header"))
                .willReturn(
                    aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"error":"Not Found"}""")
                )
        )

        StepVerifier.create(service.updateSingleCompanyRating(jobplanetId))
            .verifyComplete()

        verify(companyRepository, never()).updateCompanyDetails(anyLong(), any(), any(), any(), any())
    }
}
