package com.crawling.planet.app.controller

import com.crawling.planet.domain.entity.Company
import com.crawling.planet.domain.entity.Review
import com.crawling.planet.domain.repository.CompanyRepository
import com.crawling.planet.domain.repository.ReviewRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/ext")
class ExtensionApiController(
    private val companyRepository: CompanyRepository,
    private val reviewRepository: ReviewRepository
) {
    @GetMapping("/company/search")
    fun searchCompany(@RequestParam name: String): ResponseEntity<CompanyWithReviewsResponse?> {
        val company = companyRepository.findMostSimilarByName(name)
            ?: return ResponseEntity.ok(null)

        val reviews = reviewRepository.findTop3ByCompanyIdOrderByReviewCreatedAtDesc(company.id)

        return ResponseEntity.ok(toResponse(company, reviews))
    }

    @GetMapping("/company/{id}/reviews")
    fun getCompanyReviews(@PathVariable id: Long): ResponseEntity<CompanyWithReviewsResponse> {
        val company = companyRepository.findById(id)
            .orElse(null) ?: return ResponseEntity.notFound().build()

        val reviews = reviewRepository.findTop3ByCompanyIdOrderByReviewCreatedAtDesc(company.id)

        return ResponseEntity.ok(toResponse(company, reviews))
    }

    private fun toResponse(company: Company, reviews: List<Review>): CompanyWithReviewsResponse {
        return CompanyWithReviewsResponse(
            companyId = company.id,
            companyName = company.name,
            rating = company.averageRating,
            industry = company.industry,
            logoUrl = company.logoUrl,
            reviewCount = company.reviewCount,
            reviews = reviews.map { r ->
                ReviewSummary(
                    id = r.id,
                    rating = r.rating,
                    summary = r.summary,
                    pros = r.pros,
                    cons = r.cons,
                    toManagement = r.toManagement,
                    reviewYear = r.reviewYear,
                    occupationName = r.occupationName,
                    employStatusName = r.employStatusName
                )
            }
        )
    }

    data class CompanyWithReviewsResponse(
        val companyId: Long,
        val companyName: String,
        val rating: Double?,
        val industry: String?,
        val logoUrl: String?,
        val reviewCount: Int,
        val reviews: List<ReviewSummary>
    )

    data class ReviewSummary(
        val id: Long,
        val rating: Double?,
        val summary: String?,
        val pros: String?,
        val cons: String?,
        val toManagement: String?,
        val reviewYear: String?,
        val occupationName: String?,
        val employStatusName: String?
    )
}
