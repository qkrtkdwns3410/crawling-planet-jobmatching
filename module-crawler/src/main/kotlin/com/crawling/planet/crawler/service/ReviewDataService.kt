package com.crawling.planet.crawler.service

import com.crawling.planet.domain.dto.*
import com.crawling.planet.domain.entity.Company
import com.crawling.planet.domain.entity.Review
import com.crawling.planet.domain.repository.CompanyRepository
import com.crawling.planet.domain.repository.ReviewRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

private val logger = KotlinLogging.logger {}

/**
 * 리뷰 데이터 저장 서비스
 */
@Service
class ReviewDataService(
    private val companyRepository: CompanyRepository,
    private val reviewRepository: ReviewRepository
) {
    companion object {
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        private const val MAX_REVIEWS_PER_COMPANY = 3
    }

    /**
     * API 응답에서 회사 및 리뷰 데이터 저장
     */
    @Transactional
    fun saveFromApiResponse(companyId: Long, response: JobplanetApiResponse): SaveResult {
        val items = response.data?.items ?: return SaveResult(0, 0)

        var companySaved = 0
        var reviewsSaved = 0

        val companyInfo = extractCompanyInfo(items)
        val reviews = extractReviews(items)

        if (companyInfo == null && reviews.isEmpty()) {
            return SaveResult(0, 0)
        }

        val company = if (companyInfo != null) {
            saveOrUpdateCompany(companyInfo)?.also { companySaved = 1 }
        } else {
            getOrCreateCompany(companyId)
        } ?: return SaveResult(0, 0)

        val newReviews = reviews.take(MAX_REVIEWS_PER_COMPANY)
        val newReviewIds = newReviews.mapNotNull { it.id }.toSet()

        val existingReviews = reviewRepository.findByCompanyId(company.id)
        val existingReviewIds = existingReviews.map { it.jobplanetReviewId }.toSet()

        if (newReviewIds == existingReviewIds) {
            return SaveResult(companySaved, 0)
        }

        if (existingReviews.isNotEmpty()) {
            reviewRepository.deleteAll(existingReviews)
            companyRepository.incrementReviewCount(company.id, -existingReviews.size)
        }

        for (reviewDto in newReviews) {
            try {
                reviewDto.id ?: continue
                reviewRepository.save(createReviewEntity(reviewDto, company))
                reviewsSaved++
            } catch (e: Exception) {
                logger.error(e) { "리뷰 저장 실패 - reviewId: ${reviewDto.id}" }
            }
        }

        if (reviewsSaved > 0) {
            companyRepository.incrementReviewCount(company.id, reviewsSaved)
        }

        logger.info { "저장 완료 - companyId: $companyId, 회사: $companySaved, 리뷰: $reviewsSaved" }
        return SaveResult(companySaved, reviewsSaved)
    }

    /**
     * JOB_POSTINGS에서 회사 정보 추출
     */
    private fun extractCompanyInfo(items: List<JobplanetItem>): JobplanetCompanyInfo? {
        return items
            .firstOrNull { it.type == JobplanetItemType.JOB_POSTINGS }
            ?.company
    }

    /**
     * 리뷰 데이터 추출
     */
    private fun extractReviews(items: List<JobplanetItem>): List<JobplanetReview> {
        return items
            .filter {
                it.type == JobplanetItemType.COMPANY_REVIEW ||
                it.type == JobplanetItemType.COMPANY_BLINDED_REVIEW
            }
            .mapNotNull { it.review }
            .take(MAX_REVIEWS_PER_COMPANY)
    }

    /**
     * 회사 저장 또는 업데이트
     */
    private fun saveOrUpdateCompany(companyInfo: JobplanetCompanyInfo): Company? {
        val jobplanetId = companyInfo.id ?: return null
        val name = companyInfo.name ?: return null

        val company = companyRepository.findByJobplanetId(jobplanetId)
            .orElseGet {
                Company(
                    jobplanetId = jobplanetId,
                    name = name
                )
            }

        // 회사 정보 업데이트
        company.apply {
            this.name = name
            logoUrl = companyInfo.logoUrl
            industry = companyInfo.industry
            averageRating = companyInfo.averageRating
            companySize = companyInfo.companySize
            establishedYear = companyInfo.establishedYear
            headquarters = companyInfo.headquarters
            websiteUrl = companyInfo.websiteUrl
            description = companyInfo.description
        }

        return companyRepository.save(company)
    }

    /**
     * 회사 조회 또는 기본 정보로 생성
     */
    private fun getOrCreateCompany(companyId: Long): Company {
        return companyRepository.findByJobplanetId(companyId)
            .orElseGet {
                companyRepository.save(
                    Company(
                        jobplanetId = companyId,
                        name = "Unknown Company (ID: $companyId)"
                    )
                )
            }
    }

    /**
     * Review 엔티티 생성
     */
    private fun createReviewEntity(reviewDto: JobplanetReview, company: Company): Review {
        val reviewId = requireNotNull(reviewDto.id) { "Review ID cannot be null" }
        return Review(
            jobplanetReviewId = reviewId,
            company = company,
            status = reviewDto.status ?: -1,
            occupationName = reviewDto.occupationName,
            employStatusName = reviewDto.employStatusName?.toString(),
            reviewType = if (reviewDto.isBlinded == true) {
                JobplanetItemType.COMPANY_BLINDED_REVIEW
            } else {
                JobplanetItemType.COMPANY_REVIEW
            },
            rating = reviewDto.overall?.toDouble(),
            growthScore = reviewDto.score?.advancementRating?.toDouble(),
            salaryScore = reviewDto.score?.compensationRating?.toDouble(),
            workLifeBalanceScore = reviewDto.score?.worklifeBalanceRating?.toDouble(),
            cultureScore = reviewDto.score?.cultureRating?.toDouble(),
            managementScore = reviewDto.score?.managementRating?.toDouble(),
            summary = reviewDto.title,
            pros = reviewDto.pros,
            cons = reviewDto.cons,
            toManagement = reviewDto.messageToManagement,
            likeCount = reviewDto.helpfulCount ?: 0,
            reviewYear = reviewDto.lastYearAtEmployer?.toString(),
            reviewCreatedAt = parseDate(reviewDto.date)
        )
    }

    /**
     * 날짜 문자열 파싱
     */
    private fun parseDate(dateStr: String?): LocalDate? {
        if (dateStr.isNullOrBlank()) return null

        return try {
            // "2024-01-15T00:00:00.000Z" 형식 처리
            val dateOnly = dateStr.substringBefore("T")
            LocalDate.parse(dateOnly, DATE_FORMATTER)
        } catch (e: DateTimeParseException) {
            logger.debug { "날짜 파싱 실패: $dateStr" }
            null
        }
    }

    /**
     * 저장 결과
     */
    data class SaveResult(
        val companiesSaved: Int,
        val reviewsSaved: Int
    )
}

