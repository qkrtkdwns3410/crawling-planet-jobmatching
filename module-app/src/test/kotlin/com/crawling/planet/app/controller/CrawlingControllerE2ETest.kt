package com.crawling.planet.app.controller

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

/**
 * CrawlingController E2E 테스트
 *
 * - GET /api/crawling/status: 회사/리뷰 카운트 검증
 * - POST 요청 인증 필터: Bearer 토큰 없음 → 403
 * - POST 요청 인증 필터: 올바른 Bearer 토큰 → 정상 응답
 * - GET /api/crawling/diagnostics: 관리자 토큰 필요
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CrawlingControllerE2ETest {

    @Autowired
    lateinit var restTemplate: TestRestTemplate

    @Autowired
    lateinit var companyRepository: CompanyRepository

    @Autowired
    lateinit var reviewRepository: ReviewRepository

    @Value("\${crawling.admin.token:}")
    lateinit var adminToken: String

    @BeforeEach
    fun setUp() {
        // 테스트용 회사 2개 삽입
        val company1 = companyRepository.save(
            Company(
                jobplanetId = 300001L,
                name = "카카오",
                industry = "IT/인터넷",
                averageRating = 4.0,
                reviewCount = 0
            )
        )
        val company2 = companyRepository.save(
            Company(
                jobplanetId = 300002L,
                name = "네이버",
                industry = "IT/인터넷",
                averageRating = 3.9,
                reviewCount = 0
            )
        )

        // 리뷰 3개 삽입
        reviewRepository.saveAll(
            listOf(
                Review(
                    jobplanetReviewId = 400001L,
                    company = company1,
                    status = 3,
                    rating = 4.0,
                    summary = "좋은 복지",
                    pros = "카페테리아 좋음",
                    cons = "경쟁 치열",
                    reviewCreatedAt = LocalDate.of(2024, 1, 1)
                ),
                Review(
                    jobplanetReviewId = 400002L,
                    company = company1,
                    status = 3,
                    rating = 3.5,
                    summary = "평범",
                    reviewCreatedAt = LocalDate.of(2024, 2, 1)
                ),
                Review(
                    jobplanetReviewId = 400003L,
                    company = company2,
                    status = 3,
                    rating = 4.2,
                    summary = "안정적",
                    pros = "워라밸 좋음",
                    reviewCreatedAt = LocalDate.of(2024, 3, 1)
                )
            )
        )

        // 리뷰 카운트 갱신
        companyRepository.incrementReviewCount(company1.id, 2)
        companyRepository.incrementReviewCount(company2.id, 1)
    }

    @AfterEach
    fun tearDown() {
        reviewRepository.deleteAll()
        companyRepository.deleteAll()
    }

    // ========== GET /api/crawling/status 테스트 ==========

    @Test
    fun `상태 조회 - 회사와 리뷰 카운트를 정확하게 반환한다`() {
        // when: GET /api/crawling/status는 인증 없이 접근 가능
        val response = restTemplate.getForEntity(
            "/api/crawling/status",
            Map::class.java
        )

        // then
        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body
        assertNotNull(body)

        val totalCompanies = (body["totalCompanies"] as Number).toLong()
        val totalReviews = (body["totalReviews"] as Number).toLong()

        // setUp에서 회사 2개, 리뷰 3개 삽입
        assertEquals(2L, totalCompanies)
        assertEquals(3L, totalReviews)
    }

    @Test
    fun `상태 조회 - 빈 DB에서는 0을 반환한다`() {
        // given: 데이터 모두 삭제
        reviewRepository.deleteAll()
        companyRepository.deleteAll()

        // when
        val response = restTemplate.getForEntity(
            "/api/crawling/status",
            Map::class.java
        )

        // then
        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body
        assertNotNull(body)
        assertEquals(0L, (body["totalCompanies"] as Number).toLong())
        assertEquals(0L, (body["totalReviews"] as Number).toLong())
    }

    // ========== POST 요청 인증 필터 테스트 ==========

    @Test
    fun `인증 필터 - Bearer 토큰 없이 POST 요청하면 403을 반환한다`() {
        // given: Authorization 헤더 없이 POST 요청
        val response = restTemplate.exchange(
            "/api/crawling/company/12345",
            HttpMethod.POST,
            HttpEntity.EMPTY,
            String::class.java
        )

        // then: CrawlingAuthFilter가 403 반환
        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    @Test
    fun `인증 필터 - 잘못된 Bearer 토큰으로 POST 요청하면 403을 반환한다`() {
        // given: 잘못된 토큰으로 요청
        val headers = HttpHeaders().apply {
            set("Authorization", "Bearer wrong-token-12345")
        }
        val entity = HttpEntity<Void>(headers)

        val response = restTemplate.exchange(
            "/api/crawling/company/12345",
            HttpMethod.POST,
            entity,
            String::class.java
        )

        // then
        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    @Test
    fun `인증 필터 - 올바른 Bearer 토큰으로 POST 요청하면 인증을 통과한다`() {
        // given: 올바른 관리자 토큰으로 요청
        val headers = HttpHeaders().apply {
            set("Authorization", "Bearer $adminToken")
        }
        val entity = HttpEntity<Void>(headers)

        // when: 실제 크롤링 호출은 외부 의존성이 있으므로
        //       인증 필터 통과 여부만 검증 (403이 아닌 응답이면 통과)
        val response = restTemplate.exchange(
            "/api/crawling/company/99999999",
            HttpMethod.POST,
            entity,
            String::class.java
        )

        // then: 403 Forbidden이 아니면 인증 필터를 통과한 것
        // (실제 크롤링 실패 시 200 + 실패 메시지 반환)
        assert(response.statusCode != HttpStatus.FORBIDDEN) {
            "올바른 토큰으로 요청했는데 403이 반환되었습니다. 응답: ${response.statusCode}"
        }
    }

    @Test
    fun `인증 필터 - GET status 요청은 토큰 없이도 허용된다`() {
        // when
        val response = restTemplate.getForEntity(
            "/api/crawling/status",
            Map::class.java
        )

        // then: 공개 상태 조회는 인증 없이 통과
        assertEquals(HttpStatus.OK, response.statusCode)
    }

    @Test
    fun `진단 조회 - 토큰 없이 요청하면 403을 반환한다`() {
        val response = restTemplate.getForEntity(
            "/api/crawling/diagnostics",
            String::class.java
        )

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    @Test
    fun `진단 조회 - 올바른 토큰으로 요청하면 진단 정보를 반환한다`() {
        val headers = HttpHeaders().apply {
            set("Authorization", "Bearer $adminToken")
        }
        val entity = HttpEntity<Void>(headers)

        val response = restTemplate.exchange(
            "/api/crawling/diagnostics",
            HttpMethod.GET,
            entity,
            Map::class.java
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body
        assertNotNull(body)
        assertNotNull(body["serviceStartedAt"])
        assertNotNull(body["token"])
        assertNotNull(body["login"])
        assertNotNull(body["api"])
        assertNotNull(body["job"])
    }
}
