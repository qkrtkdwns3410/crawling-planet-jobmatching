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
        val (result, company) = saveFromApiResponseInternal(companyId, response)
        
        // 단일 응답 처리 시 회사의 리뷰 수 원자적으로 증가
        if (company != null && result.reviewsSaved > 0) {
            companyRepository.incrementReviewCount(company.id, result.reviewsSaved)
        }

        logger.info { 
            "데이터 저장 완료 - companyId: $companyId, " +
            "회사저장: ${result.companiesSaved}, 리뷰저장: ${result.reviewsSaved}, 리뷰스킵: ${result.reviewsSkipped}" 
        }

        return result
    }

    /**
     * API 응답에서 회사 및 리뷰 데이터 저장 (내부용 - 회사 카운트 업데이트 없음)
     */
    private fun saveFromApiResponseInternal(companyId: Long, response: JobplanetApiResponse): Pair<SaveResult, Company?> {
        val items = response.data?.items ?: return Pair(SaveResult(0, 0, 0), null)

        var companySaved = 0
        var reviewsSaved = 0
        var reviewsSkipped = 0

        // 1. JOB_POSTINGS에서 회사 정보 추출 및 저장
        val companyInfo = extractCompanyInfo(items)
        val reviews = extractReviews(items)

        // 회사 정보도 없고 리뷰도 없으면 스킵
        if (companyInfo == null && reviews.isEmpty()) {
            logger.debug { "회사 정보 및 리뷰 없음 - companyId: $companyId, 스킵" }
            return Pair(SaveResult(0, 0, 0), null)
        }

        val company = if (companyInfo != null) {
            saveOrUpdateCompany(companyInfo)?.also { companySaved = 1 }
        } else {
            // 리뷰는 있지만 회사 정보가 없는 경우에만 기본 정보로 생성
            getOrCreateCompany(companyId)
        }

        if (company == null) {
            logger.warn { "회사 정보 저장 실패 - companyId: $companyId" }
            return Pair(SaveResult(0, 0, 0), null)
        }

        val newReviews = reviews.take(MAX_REVIEWS_PER_COMPANY)
        val newReviewIds = newReviews.mapNotNull { it.id }.toSet()

        // 기존 리뷰 ID 목록 조회 (가벼운 쿼리)
        val existingReviews = reviewRepository.findByCompanyId(company.id)
        val existingReviewIds = existingReviews.map { it.jobplanetReviewId }.toSet()

        // 동일하면 스킵
        if (newReviewIds == existingReviewIds) {
            logger.debug { "리뷰 변경 없음 - companyId: $companyId, 스킵" }
            return Pair(SaveResult(companySaved, 0, 0), company)
        }

        // 변경 감지 → 기존 삭제 후 새로 삽입
        if (existingReviews.isNotEmpty()) {
            reviewRepository.deleteAll(existingReviews)
            // reviewCount 차감
            companyRepository.incrementReviewCount(company.id, -existingReviews.size)
        }

        for (reviewDto in newReviews) {
            try {
                val reviewId = reviewDto.id ?: continue
                val review = createReviewEntity(reviewDto, company)
                reviewRepository.save(review)
                reviewsSaved++
            } catch (e: Exception) {
                logger.error(e) { "리뷰 저장 실패 - reviewId: ${reviewDto.id}" }
            }
        }

        return Pair(SaveResult(companySaved, reviewsSaved, reviewsSkipped), company)
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
        val reviewsSaved: Int,
        val reviewsSkipped: Int
    )
}

