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
    val employStatusName: Any?,

    val overall: Int?,
    val score: JobplanetReviewScore?,
    val title: String?,
    val pros: String?,
    val cons: String?,

    @JsonProperty("message_to_management")
    val messageToManagement: String?,

    @JsonProperty("helpful_count")
    val helpfulCount: Int?,

    val date: String?,

    @JsonProperty("last_year_at_employer")
    val lastYearAtEmployer: Int?,

    @JsonProperty("recommend_to_friend")
    val recommendToFriend: Boolean?,

    @JsonProperty("is_blinded")
    val isBlinded: Boolean?
)

/**
 * 리뷰 점수 상세
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class JobplanetReviewScore(
    val overall: Int?,

    @JsonProperty("advancement_rating")
    val advancementRating: Int?,

    @JsonProperty("compensation_rating")
    val compensationRating: Int?,

    @JsonProperty("worklife_balance_rating")
    val worklifeBalanceRating: Int?,

    @JsonProperty("culture_rating")
    val cultureRating: Int?,

    @JsonProperty("management_rating")
    val managementRating: Int?
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



