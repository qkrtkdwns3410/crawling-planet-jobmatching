package com.crawling.planet.crawler.service

import com.crawling.planet.domain.dto.*
import com.crawling.planet.domain.entity.Company
import com.crawling.planet.domain.entity.Review
import com.crawling.planet.domain.repository.CompanyRepository
import com.crawling.planet.domain.repository.ReviewRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.*
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReviewDataServiceTest {

    private lateinit var companyRepository: CompanyRepository
    private lateinit var reviewRepository: ReviewRepository
    private lateinit var reviewDataService: ReviewDataService

    @BeforeEach
    fun setUp() {
        companyRepository = mock(CompanyRepository::class.java)
        reviewRepository = mock(ReviewRepository::class.java)
        reviewDataService = ReviewDataService(companyRepository, reviewRepository)
    }

    @Test
    fun `items가 비어있으면 SaveResult(0,0) 반환`() {
        val response = JobplanetApiResponse(
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

        val result = reviewDataService.saveFromApiResponse(999L, response)

        assertEquals(0, result.companiesSaved)
        assertEquals(0, result.reviewsSaved)
        verify(companyRepository, never()).save(any())
    }

    @Test
    fun `JOB_POSTINGS 있을 때 averageRating이 Company에 매핑됨`() {
        val companyId = 100L
        val savedCompany = Company(id = 1L, jobplanetId = companyId, name = "테스트회사")

        `when`(companyRepository.findByJobplanetId(companyId)).thenReturn(Optional.empty())
        `when`(companyRepository.save(any(Company::class.java))).thenReturn(savedCompany)
        `when`(reviewRepository.findByCompanyId(savedCompany.id)).thenReturn(emptyList())

        val response = buildApiResponse(
            companyId = companyId,
            companyName = "테스트회사",
            averageRating = 4.2,
            reviews = listOf(buildReview(id = 1L, rating = 4, title = "좋음"))
        )

        reviewDataService.saveFromApiResponse(companyId, response)

        val captor = ArgumentCaptor.forClass(Company::class.java)
        verify(companyRepository).save(captor.capture())
        assertEquals(4.2, captor.value.averageRating)
    }

    @Test
    fun `JOB_POSTINGS 없을 때 getOrCreateCompany 경로 - averageRating null`() {
        val companyId = 200L
        val fallbackCompany = Company(id = 2L, jobplanetId = companyId, name = "Unknown Company (ID: $companyId)")

        `when`(companyRepository.findByJobplanetId(companyId)).thenReturn(Optional.empty())
        `when`(companyRepository.save(any(Company::class.java))).thenReturn(fallbackCompany)
        `when`(reviewRepository.findByCompanyId(fallbackCompany.id)).thenReturn(emptyList())

        val response = JobplanetApiResponse(
            status = "success",
            code = 200,
            data = JobplanetData(
                items = listOf(
                    JobplanetItem(
                        type = JobplanetItemType.COMPANY_REVIEW,
                        review = buildReview(id = 10L, rating = 3, title = "리뷰"),
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

        reviewDataService.saveFromApiResponse(companyId, response)

        val captor = ArgumentCaptor.forClass(Company::class.java)
        verify(companyRepository).save(captor.capture())
        assertNull(captor.value.averageRating)
    }

    @Test
    fun `리뷰 3개 초과 시 MAX_REVIEWS_PER_COMPANY(3)개만 저장됨`() {
        val companyId = 300L
        val savedCompany = Company(id = 3L, jobplanetId = companyId, name = "LG전자")

        `when`(companyRepository.findByJobplanetId(companyId)).thenReturn(Optional.empty())
        `when`(companyRepository.save(any(Company::class.java))).thenReturn(savedCompany)
        `when`(reviewRepository.findByCompanyId(savedCompany.id)).thenReturn(emptyList())
        `when`(reviewRepository.save(any(Review::class.java))).thenAnswer { it.arguments[0] }

        val response = buildApiResponse(
            companyId = companyId,
            companyName = "LG전자",
            averageRating = 3.5,
            reviews = listOf(
                buildReview(id = 1L, rating = 5, title = "최고"),
                buildReview(id = 2L, rating = 4, title = "좋음"),
                buildReview(id = 3L, rating = 3, title = "보통"),
                buildReview(id = 4L, rating = 2, title = "별로"),
                buildReview(id = 5L, rating = 1, title = "최악")
            )
        )

        val result = reviewDataService.saveFromApiResponse(companyId, response)

        assertEquals(3, result.reviewsSaved)
    }

    // ========== 헬퍼 메서드 ==========

    private fun buildApiResponse(
        companyId: Long,
        companyName: String,
        averageRating: Double = 3.8,
        reviews: List<JobplanetReview>
    ): JobplanetApiResponse {
        val items = mutableListOf<JobplanetItem>()

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
                    averageRating = averageRating,
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

    private fun buildReview(id: Long, rating: Int, title: String): JobplanetReview {
        return JobplanetReview(
            id = id,
            status = 3,
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
            pros = "장점",
            cons = "단점",
            messageToManagement = "경영진에게",
            helpfulCount = 0,
            date = "2024-03-01T00:00:00.000Z",
            lastYearAtEmployer = 2024,
            recommendToFriend = true,
            isBlinded = false
        )
    }
}
