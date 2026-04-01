package com.crawling.planet.crawler.service

import com.crawling.planet.domain.dto.*
import com.crawling.planet.domain.entity.Company
import com.crawling.planet.domain.entity.Review
import com.crawling.planet.domain.repository.CompanyRepository
import com.crawling.planet.domain.repository.ReviewRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.autoconfigure.web.reactive.WebFluxAutoConfiguration
import org.springframework.boot.autoconfigure.web.reactive.error.ErrorWebFluxAutoConfiguration
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientAutoConfiguration
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.ComponentScan.Filter
import org.springframework.context.annotation.FilterType
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * ReviewDataService E2E 테스트
 *
 * Testcontainers PostgreSQL을 사용하여 실제 DB에서 리뷰 저장 로직을 검증합니다.
 * - saveFromApiResponse: 회사 + 리뷰 저장
 * - 리뷰 교체: 기존 리뷰 3개가 있을 때 새 리뷰로 교체
 * - 동일 리뷰 스킵: 리뷰 ID가 같으면 저장 생략
 * - Unknown Company fallback: 회사 정보 없을 때 기본 이름으로 생성
 */
@SpringBootTest(classes = [ReviewDataServiceE2ETest.TestConfig::class], webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class ReviewDataServiceE2ETest {

    /**
     * 테스트용 Spring Boot 애플리케이션 설정
     *
     * module-crawler는 standalone 라이브러리 모듈이므로
     * @SpringBootApplication 없이 테스트에 필요한 컴포넌트만 스캔합니다.
     * JobplanetLoginService(Selenium 의존)는 제외합니다.
     */
    @SpringBootConfiguration
    @EnableAutoConfiguration(
        exclude = [
            WebMvcAutoConfiguration::class,
            WebFluxAutoConfiguration::class,
            ErrorWebFluxAutoConfiguration::class,
            WebClientAutoConfiguration::class
        ]
    )
    @EnableJpaRepositories(basePackages = ["com.crawling.planet.domain.repository"])
    @EntityScan(basePackages = ["com.crawling.planet.domain.entity"])
    @ComponentScan(
        basePackages = ["com.crawling.planet.crawler.service"],
        useDefaultFilters = false,
        includeFilters = [
            ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = [ReviewDataService::class]
            )
        ]
    )
    class TestConfig

    @Autowired
    lateinit var reviewDataService: ReviewDataService

    @Autowired
    lateinit var companyRepository: CompanyRepository

    @Autowired
    lateinit var reviewRepository: ReviewRepository

    @AfterEach
    fun tearDown() {
        // 테스트 후 데이터 정리
        reviewRepository.deleteAll()
        companyRepository.deleteAll()
    }

    // ========== saveFromApiResponse 기본 저장 테스트 ==========

    @Test
    fun `회사와 리뷰를 API 응답으로부터 저장한다`() {
        // given: JOB_POSTINGS(회사 정보) + COMPANY_REVIEW(리뷰 2개)로 구성된 응답
        val response = buildApiResponse(
            companyId = 500001L,
            companyName = "현대자동차",
            reviews = listOf(
                buildReview(id = 600001L, rating = 4, title = "좋은 직장"),
                buildReview(id = 600002L, rating = 3, title = "평범한 직장")
            )
        )

        // when
        val result = reviewDataService.saveFromApiResponse(500001L, response)

        // then
        assertEquals(1, result.companiesSaved, "회사 1개 저장 기대")
        assertEquals(2, result.reviewsSaved, "리뷰 2개 저장 기대")

        // DB 검증
        val savedCompany = companyRepository.findByJobplanetId(500001L)
        assertTrue(savedCompany.isPresent)
        assertEquals("현대자동차", savedCompany.get().name)

        val savedReviews = reviewRepository.findByCompanyId(savedCompany.get().id)
        assertEquals(2, savedReviews.size)
    }

    @Test
    fun `리뷰가 3개를 초과하면 최대 3개만 저장한다`() {
        // given: 리뷰 5개 포함 응답 (MAX_REVIEWS_PER_COMPANY = 3)
        val response = buildApiResponse(
            companyId = 500002L,
            companyName = "LG전자",
            reviews = listOf(
                buildReview(id = 600010L, rating = 5, title = "최고"),
                buildReview(id = 600011L, rating = 4, title = "좋음"),
                buildReview(id = 600012L, rating = 3, title = "보통"),
                buildReview(id = 600013L, rating = 2, title = "별로"),
                buildReview(id = 600014L, rating = 1, title = "최악")
            )
        )

        // when
        val result = reviewDataService.saveFromApiResponse(500002L, response)

        // then
        assertEquals(3, result.reviewsSaved, "최대 3개까지만 저장되어야 함")

        val company = companyRepository.findByJobplanetId(500002L).get()
        val savedReviews = reviewRepository.findByCompanyId(company.id)
        assertEquals(3, savedReviews.size)
    }

    // ========== 리뷰 교체 테스트 ==========

    @Test
    fun `리뷰 3개가 이미 있을 때 새 리뷰 세트로 교체된다`() {
        // given: 기존 회사 + 리뷰 3개 직접 삽입
        val existingCompany = companyRepository.save(
            Company(
                jobplanetId = 500003L,
                name = "SK하이닉스",
                reviewCount = 0
            )
        )
        val existingReviews = reviewRepository.saveAll(
            listOf(
                Review(jobplanetReviewId = 700001L, company = existingCompany, status = 3, rating = 3.0, summary = "기존 리뷰 1"),
                Review(jobplanetReviewId = 700002L, company = existingCompany, status = 3, rating = 3.5, summary = "기존 리뷰 2"),
                Review(jobplanetReviewId = 700003L, company = existingCompany, status = 3, rating = 4.0, summary = "기존 리뷰 3")
            )
        )
        companyRepository.incrementReviewCount(existingCompany.id, 3)

        // when: 완전히 다른 리뷰 ID 세트로 교체 요청
        val newResponse = buildApiResponse(
            companyId = 500003L,
            companyName = "SK하이닉스",
            reviews = listOf(
                buildReview(id = 800001L, rating = 4, title = "새 리뷰 1"),
                buildReview(id = 800002L, rating = 5, title = "새 리뷰 2"),
                buildReview(id = 800003L, rating = 3, title = "새 리뷰 3")
            )
        )
        val result = reviewDataService.saveFromApiResponse(500003L, newResponse)

        // then: 새 리뷰 3개가 저장되었어야 함
        assertEquals(3, result.reviewsSaved, "새 리뷰 3개 저장 기대")

        val company = companyRepository.findByJobplanetId(500003L).get()
        val savedReviews = reviewRepository.findByCompanyId(company.id)
        assertEquals(3, savedReviews.size)

        // 기존 리뷰 ID는 존재하지 않아야 함
        val savedReviewIds = savedReviews.map { it.jobplanetReviewId }.toSet()
        assertTrue(savedReviewIds.contains(800001L), "새 리뷰 800001이 저장되어야 함")
        assertTrue(savedReviewIds.contains(800002L), "새 리뷰 800002가 저장되어야 함")
        assertTrue(!savedReviewIds.contains(700001L), "기존 리뷰 700001은 삭제되어야 함")
    }

    // ========== 동일 리뷰 스킵 테스트 ==========

    @Test
    fun `동일한 리뷰 ID 세트면 저장을 생략한다`() {
        // given: 기존 리뷰 저장 (첫 번째 호출)
        val firstResponse = buildApiResponse(
            companyId = 500004L,
            companyName = "포스코",
            reviews = listOf(
                buildReview(id = 900001L, rating = 4, title = "첫 리뷰"),
                buildReview(id = 900002L, rating = 3, title = "두 번째 리뷰")
            )
        )
        reviewDataService.saveFromApiResponse(500004L, firstResponse)

        // when: 동일한 리뷰 ID 세트로 다시 호출
        val sameResponse = buildApiResponse(
            companyId = 500004L,
            companyName = "포스코",
            reviews = listOf(
                buildReview(id = 900001L, rating = 4, title = "첫 리뷰 (내용 동일)"),
                buildReview(id = 900002L, rating = 3, title = "두 번째 리뷰 (내용 동일)")
            )
        )
        val result = reviewDataService.saveFromApiResponse(500004L, sameResponse)

        // then: 동일한 리뷰 ID이므로 저장 생략
        assertEquals(0, result.reviewsSaved, "동일 리뷰이므로 저장 생략 기대")

        // DB 리뷰 수는 여전히 2개
        val company = companyRepository.findByJobplanetId(500004L).get()
        val reviews = reviewRepository.findByCompanyId(company.id)
        assertEquals(2, reviews.size)
    }

    // ========== Unknown Company fallback 테스트 ==========

    @Test
    fun `회사 정보가 없을 때 Unknown Company 이름으로 기본 회사를 생성한다`() {
        // given: JOB_POSTINGS 없이 리뷰만 있는 응답 (회사 정보 미포함)
        val companyId = 500005L
        val responseWithNoCompanyInfo = JobplanetApiResponse(
            status = "success",
            code = 200,
            data = JobplanetData(
                items = listOf(
                    // 리뷰만 존재, JOB_POSTINGS 없음
                    JobplanetItem(
                        type = JobplanetItemType.COMPANY_REVIEW,
                        review = buildReview(id = 950001L, rating = 4, title = "회사 없이 리뷰만"),
                        similarReviews = null,
                        company = null,
                        items = null
                    )
                ),
                page = 1,
                totalPage = 1,
                totalCount = 1,
                filteredCount = null,
                filteredPage = null,
                approvedTotalCount = null,
                approvedFilteredCount = null,
                approvedFilteredPage = null,
                nextPageUrl = null,
                isFiltered = null,
                isReviewViewStatus = null,
                isBusinessAccount = null,
                userId = null
            ),
            csrf = null
        )

        // when
        val result = reviewDataService.saveFromApiResponse(companyId, responseWithNoCompanyInfo)

        // then: 리뷰는 저장되고 fallback 회사가 생성되어야 함
        assertEquals(1, result.reviewsSaved, "리뷰 1개 저장 기대")

        // Unknown Company 이름으로 회사가 생성되어야 함
        val company = companyRepository.findByJobplanetId(companyId)
        assertTrue(company.isPresent)
        assertTrue(
            company.get().name.contains("Unknown Company") || company.get().name.contains("$companyId"),
            "Unknown Company 이름 형식이어야 함. 실제: ${company.get().name}"
        )
    }

    @Test
    fun `회사 정보도 없고 리뷰도 없으면 아무것도 저장하지 않는다`() {
        // given: 빈 응답
        val emptyResponse = JobplanetApiResponse(
            status = "success",
            code = 200,
            data = JobplanetData(
                items = emptyList(),
                page = 1,
                totalPage = 0,
                totalCount = 0,
                filteredCount = null,
                filteredPage = null,
                approvedTotalCount = null,
                approvedFilteredCount = null,
                approvedFilteredPage = null,
                nextPageUrl = null,
                isFiltered = null,
                isReviewViewStatus = null,
                isBusinessAccount = null,
                userId = null
            ),
            csrf = null
        )

        // when
        val result = reviewDataService.saveFromApiResponse(999999L, emptyResponse)

        // then
        assertEquals(0, result.companiesSaved)
        assertEquals(0, result.reviewsSaved)

        // DB에 아무것도 저장되지 않아야 함
        val company = companyRepository.findByJobplanetId(999999L)
        assertTrue(company.isEmpty)
    }

    // ========== 헬퍼 메서드 ==========

    /**
     * 테스트용 JobplanetApiResponse 빌더
     * JOB_POSTINGS(회사 정보) + COMPANY_REVIEW 아이템 목록으로 구성
     */
    private fun buildApiResponse(
        companyId: Long,
        companyName: String,
        reviews: List<JobplanetReview>
    ): JobplanetApiResponse {
        val items = mutableListOf<JobplanetItem>()

        // 회사 정보 아이템 추가 (JOB_POSTINGS 타입)
        items.add(
            JobplanetItem(
                type = JobplanetItemType.JOB_POSTINGS,
                review = null,
                similarReviews = null,
                company = JobplanetCompanyInfo(
                    id = companyId,
                    name = companyName,
                    logoUrl = "https://example.com/$companyId.png",
                    industry = "제조",
                    averageRating = 3.8,
                    reviewCount = reviews.size,
                    companySize = "대기업",
                    establishedYear = "1990",
                    headquarters = "서울",
                    websiteUrl = "https://example.com",
                    description = "테스트 회사",
                    isFollowing = false,
                    interviewCount = 0,
                    salaryCount = 0
                ),
                items = null
            )
        )

        // 리뷰 아이템 추가
        reviews.forEach { review ->
            items.add(
                JobplanetItem(
                    type = JobplanetItemType.COMPANY_REVIEW,
                    review = review,
                    similarReviews = null,
                    company = null,
                    items = null
                )
            )
        }

        return JobplanetApiResponse(
            status = "success",
            code = 200,
            data = JobplanetData(
                items = items,
                page = 1,
                totalPage = 1,
                totalCount = reviews.size,
                filteredCount = null,
                filteredPage = null,
                approvedTotalCount = null,
                approvedFilteredCount = null,
                approvedFilteredPage = null,
                nextPageUrl = null,
                isFiltered = null,
                isReviewViewStatus = null,
                isBusinessAccount = null,
                userId = null
            ),
            csrf = null
        )
    }

    /**
     * 테스트용 JobplanetReview 빌더
     */
    private fun buildReview(
        id: Long,
        rating: Int,
        title: String
    ): JobplanetReview {
        return JobplanetReview(
            id = id,
            status = 3, // NORMAL
            occupationName = "소프트웨어 개발",
            employStatusName = "현직원",
            overall = rating,
            score = JobplanetReviewScore(
                overall = rating,
                advancementRating = rating,
                compensationRating = rating,
                worklifeBalanceRating = rating,
                cultureRating = rating,
                managementRating = rating
            ),
            title = title,
            pros = "장점 내용",
            cons = "단점 내용",
            messageToManagement = "경영진에게",
            helpfulCount = 0,
            date = "2024-03-01T00:00:00.000Z",
            lastYearAtEmployer = 2024,
            recommendToFriend = true,
            isBlinded = false
        )
    }
}
