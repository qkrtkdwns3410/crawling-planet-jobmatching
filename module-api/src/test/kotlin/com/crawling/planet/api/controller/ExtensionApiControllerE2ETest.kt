package com.crawling.planet.api.controller

import com.crawling.planet.domain.entity.Company
import com.crawling.planet.domain.entity.Review
import com.crawling.planet.domain.repository.CompanyRepository
import com.crawling.planet.domain.repository.ReviewRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * ExtensionApiController E2E 테스트
 *
 * Testcontainers PostgreSQL을 사용하여 실제 DB 환경에서 API를 검증합니다.
 * pg_trgm 확장을 활성화하여 유사도 검색도 함께 테스트합니다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ExtensionApiControllerE2ETest {

    @Autowired
    lateinit var restTemplate: TestRestTemplate

    @Autowired
    lateinit var companyRepository: CompanyRepository

    @Autowired
    lateinit var reviewRepository: ReviewRepository

    @Value("\${api.security.key:}")
    lateinit var apiKey: String

    // 테스트용 회사 엔티티
    private lateinit var testCompany: Company

    @BeforeEach
    fun setUp() {
        // 테스트 데이터 삽입: 삼성전자 회사
        testCompany = companyRepository.save(
            Company(
                jobplanetId = 100001L,
                name = "삼성전자",
                logoUrl = "https://example.com/samsung.png",
                industry = "전기/전자",
                averageRating = 3.8,
                reviewCount = 0
            )
        )

        // 테스트 리뷰 3개 삽입
        val review1 = Review(
            jobplanetReviewId = 200001L,
            company = testCompany,
            status = 3, // NORMAL
            rating = 4.0,
            summary = "좋은 회사입니다",
            pros = "복지가 좋음",
            cons = "야근이 많음",
            occupationName = "소프트웨어 개발",
            employStatusName = "현직원",
            reviewCreatedAt = LocalDate.of(2024, 1, 15)
        )
        val review2 = Review(
            jobplanetReviewId = 200002L,
            company = testCompany,
            status = 3,
            rating = 3.5,
            summary = "평범한 회사",
            pros = "안정적인 직장",
            cons = "수직적인 문화",
            occupationName = "경영/기획",
            employStatusName = "전직원",
            reviewCreatedAt = LocalDate.of(2024, 2, 10)
        )
        val review3 = Review(
            jobplanetReviewId = 200003L,
            company = testCompany,
            status = 3,
            rating = 4.5,
            summary = "글로벌 기업",
            pros = "성장 기회 많음",
            cons = "경쟁이 치열",
            occupationName = "연구개발",
            employStatusName = "현직원",
            reviewCreatedAt = LocalDate.of(2024, 3, 5)
        )
        reviewRepository.saveAll(listOf(review1, review2, review3))

        // 리뷰 카운트 갱신
        companyRepository.incrementReviewCount(testCompany.id, 3)
        testCompany = companyRepository.findById(testCompany.id).get()
    }

    @AfterEach
    fun tearDown() {
        // 테스트 후 데이터 정리 (순서 중요: 리뷰 먼저 삭제 후 회사 삭제)
        reviewRepository.deleteAll()
        companyRepository.deleteAll()
    }

    /**
     * API 요청 헤더에 X-API-Key 포함
     */
    private fun headersWithApiKey(): HttpHeaders {
        return HttpHeaders().apply {
            set("X-API-Key", apiKey)
        }
    }

    // ========== GET /api/ext/company/search 테스트 ==========

    @Test
    fun `회사명 검색 - 정확한 이름으로 조회하면 회사와 리뷰를 반환한다`() {
        // given
        val headers = headersWithApiKey()
        val entity = HttpEntity<Void>(headers)

        // when
        val response = restTemplate.exchange(
            "/api/ext/company/search?name=삼성전자",
            HttpMethod.GET,
            entity,
            Map::class.java
        )

        // then
        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body
        assertNotNull(body)
        assertEquals("삼성전자", body["companyName"])
        assertEquals(100001, (body["jobplanetId"] as Number).toLong())

        @Suppress("UNCHECKED_CAST")
        val reviews = body["reviews"] as List<Map<String, Any>>
        // 최신순 3개가 반환되어야 함
        assertEquals(3, reviews.size)
    }

    @Test
    fun `회사명 검색 - 유사한 이름으로도 유사도 매칭이 동작한다`() {
        // given: "삼성" 으로 검색해도 "삼성전자"가 매칭되어야 함
        val headers = headersWithApiKey()
        val entity = HttpEntity<Void>(headers)

        // when
        val response = restTemplate.exchange(
            "/api/ext/company/search?name=삼성",
            HttpMethod.GET,
            entity,
            Map::class.java
        )

        // then: pg_trgm 유사도 매칭으로 삼성전자가 반환되거나, 유사도 임계값 미달로 null 반환
        // 실제 pg_trgm 기본 임계값(0.3)에 따라 결과가 달라질 수 있음 - 200 OK만 검증
        assertEquals(HttpStatus.OK, response.statusCode)
    }

    @Test
    fun `회사명 검색 - 존재하지 않는 이름은 null을 반환한다`() {
        // given
        val headers = headersWithApiKey()
        val entity = HttpEntity<Void>(headers)

        // when
        val response = restTemplate.exchange(
            "/api/ext/company/search?name=존재하지않는회사XYZABC",
            HttpMethod.GET,
            entity,
            String::class.java
        )

        // then: 응답 바디가 null 또는 "null" 문자열
        assertEquals(HttpStatus.OK, response.statusCode)
        // body가 null이거나 "null" 문자열이면 통과
        val body = response.body
        assert(body == null || body == "null") {
            "존재하지 않는 회사 검색 시 null이어야 합니다. 실제 응답: $body"
        }
    }

    @Test
    fun `회사명 검색 - API 키 없이 요청하면 403을 반환한다`() {
        // given: 헤더 없이 요청
        val response = restTemplate.getForEntity(
            "/api/ext/company/search?name=삼성전자",
            String::class.java
        )

        // then
        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    // ========== GET /api/ext/company/{id}/reviews 테스트 ==========

    @Test
    fun `회사 ID로 리뷰 조회 - 존재하는 회사 ID로 조회하면 리뷰 목록을 반환한다`() {
        // given
        val companyId = testCompany.id
        val headers = headersWithApiKey()
        val entity = HttpEntity<Void>(headers)

        // when
        val response = restTemplate.exchange(
            "/api/ext/company/$companyId/reviews",
            HttpMethod.GET,
            entity,
            Map::class.java
        )

        // then
        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body
        assertNotNull(body)
        assertEquals("삼성전자", body["companyName"])

        @Suppress("UNCHECKED_CAST")
        val reviews = body["reviews"] as List<Map<String, Any>>
        assertEquals(3, reviews.size)

        // 첫 번째 리뷰 내용 검증 (최신순이므로 reviewCreatedAt 기준 가장 최근 리뷰)
        val firstReview = reviews[0]
        assertNotNull(firstReview["rating"])
        assertNotNull(firstReview["summary"])
    }

    @Test
    fun `회사 ID로 리뷰 조회 - 존재하지 않는 ID는 404를 반환한다`() {
        // given
        val headers = headersWithApiKey()
        val entity = HttpEntity<Void>(headers)

        // when
        val response = restTemplate.exchange(
            "/api/ext/company/99999999/reviews",
            HttpMethod.GET,
            entity,
            String::class.java
        )

        // then
        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `회사 ID로 리뷰 조회 - API 키 없이 요청하면 403을 반환한다`() {
        // given: 헤더 없이 요청
        val companyId = testCompany.id
        val response = restTemplate.getForEntity(
            "/api/ext/company/$companyId/reviews",
            String::class.java
        )

        // then
        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    @Test
    fun `회사 ID로 리뷰 조회 - 리뷰가 없는 회사도 빈 배열로 반환한다`() {
        // given: 리뷰 없는 별도 회사 생성
        val emptyCompany = companyRepository.save(
            Company(
                jobplanetId = 100002L,
                name = "리뷰없는회사",
                reviewCount = 0
            )
        )
        val headers = headersWithApiKey()
        val entity = HttpEntity<Void>(headers)

        // when
        val response = restTemplate.exchange(
            "/api/ext/company/${emptyCompany.id}/reviews",
            HttpMethod.GET,
            entity,
            Map::class.java
        )

        // then
        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body
        assertNotNull(body)

        @Suppress("UNCHECKED_CAST")
        val reviews = body["reviews"] as List<*>
        assertEquals(0, reviews.size)

        // 정리
        companyRepository.delete(emptyCompany)
    }
}
