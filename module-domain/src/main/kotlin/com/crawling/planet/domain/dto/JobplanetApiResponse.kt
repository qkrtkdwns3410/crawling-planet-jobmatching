package com.crawling.planet.domain.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * 잡플래닛 API 응답 루트
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class JobplanetApiResponse(
    val status: String?,
    val code: Int?,
    val data: JobplanetData?,
    val csrf: String?
)

/**
 * 잡플래닛 데이터 영역
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class JobplanetData(
    val items: List<JobplanetItem>?,
    val page: Int?,
    
    @JsonProperty("total_page")
    val totalPage: Int?,
    
    @JsonProperty("total_count")
    val totalCount: Int?,
    
    @JsonProperty("filtered_count")
    val filteredCount: Int?,
    
    @JsonProperty("filtered_page")
    val filteredPage: Int?,
    
    @JsonProperty("approved_total_count")
    val approvedTotalCount: Int?,
    
    @JsonProperty("approved_filtered_count")
    val approvedFilteredCount: Int?,
    
    @JsonProperty("approved_filtered_page")
    val approvedFilteredPage: Int?,
    
    @JsonProperty("next_page_url")
    val nextPageUrl: String?,
    
    @JsonProperty("is_filtered")
    val isFiltered: Boolean?,
    
    @JsonProperty("is_review_view_status")
    val isReviewViewStatus: Boolean?,
    
    @JsonProperty("is_business_account")
    val isBusinessAccount: Boolean?,
    
    @JsonProperty("user_id")
    val userId: Long?
)

/**
 * 잡플래닛 아이템 (리뷰, 채용공고, 광고 등)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class JobplanetItem(
    val type: String?,
    val review: JobplanetReview?,
    
    @JsonProperty("similar_reviews")
    val similarReviews: List<Any>?,
    
    // JOB_POSTINGS 타입일 때 사용
    val company: JobplanetCompanyInfo?,
    val items: List<Any>?
)

/**
 * 잡플래닛 리뷰 정보
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class JobplanetReview(
    val id: Long?,
    val status: Int?,
    
    @JsonProperty("occupation_name")
    val occupationName: String?,
    
    @JsonProperty("employ_status_name")
    val employStatusName: Any?, // Boolean 또는 String
    
    @JsonProperty("employ_type_name")
    val employTypeName: String?,
    
    val rating: Double?,
    
    @JsonProperty("growth_rate")
    val growthRate: Double?,
    
    @JsonProperty("leadership_rate")
    val leadershipRate: Double?,
    
    @JsonProperty("reward_rate")
    val rewardRate: Double?,
    
    @JsonProperty("culture_rate")
    val cultureRate: Double?,
    
    @JsonProperty("balance_rate")
    val balanceRate: Double?,
    
    val summary: String?,
    val pros: String?,
    val cons: String?,
    
    @JsonProperty("to_management")
    val toManagement: String?,
    
    @JsonProperty("like_count")
    val likeCount: Int?,
    
    @JsonProperty("created_at")
    val createdAt: String?,
    
    @JsonProperty("updated_at")
    val updatedAt: String?,
    
    @JsonProperty("review_year")
    val reviewYear: String?,
    
    @JsonProperty("company_id")
    val companyId: Long?,
    
    @JsonProperty("company_name")
    val companyName: String?,
    
    @JsonProperty("company_logo")
    val companyLogo: String?,
    
    // 추가 필드들
    @JsonProperty("is_blinded")
    val isBlinded: Boolean?,
    
    @JsonProperty("is_verified")
    val isVerified: Boolean?,
    
    @JsonProperty("is_certified")
    val isCertified: Boolean?,
    
    @JsonProperty("can_report")
    val canReport: Boolean?,
    
    @JsonProperty("is_reported")
    val isReported: Boolean?
)

/**
 * 잡플래닛 회사 정보 (JOB_POSTINGS 내)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class JobplanetCompanyInfo(
    val id: Long?,
    val name: String?,
    
    @JsonProperty("logo_url")
    val logoUrl: String?,
    
    val industry: String?,
    
    @JsonProperty("average_rating")
    val averageRating: Double?,
    
    @JsonProperty("review_count")
    val reviewCount: Int?,
    
    @JsonProperty("company_size")
    val companySize: String?,
    
    @JsonProperty("established_year")
    val establishedYear: String?,
    
    val headquarters: String?,
    
    @JsonProperty("website_url")
    val websiteUrl: String?,
    
    val description: String?,
    
    // 추가 필드들
    @JsonProperty("is_following")
    val isFollowing: Boolean?,
    
    @JsonProperty("interview_count")
    val interviewCount: Int?,
    
    @JsonProperty("salary_count")
    val salaryCount: Int?
)

/**
 * 아이템 타입 상수
 */
object JobplanetItemType {
    const val COMPANY_REVIEW = "COMPANY_REVIEW"
    const val COMPANY_BLINDED_REVIEW = "COMPANY_BLINDED_REVIEW"
    const val JOB_POSTINGS = "JOB_POSTINGS"
    const val ADVERTISEMENT = "ADVERTISEMENT"
}

/**
 * 리뷰 상태 코드 상수
 */
object JobplanetReviewStatus {
    const val NORMAL = 3
    const val BLINDED = 12
}



