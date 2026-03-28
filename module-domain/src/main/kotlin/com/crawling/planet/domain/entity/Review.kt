package com.crawling.planet.domain.entity

import jakarta.persistence.*
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 리뷰 상태
 */
enum class ReviewStatus(val code: Int, val description: String) {
    NORMAL(3, "정상"),
    BLINDED(12, "게시중단"),
    UNKNOWN(-1, "알 수 없음");

    companion object {
        fun fromCode(code: Int): ReviewStatus {
            return entries.find { it.code == code } ?: UNKNOWN
        }
    }
}

/**
 * 리뷰 엔티티
 */
@Entity
@Table(
    name = "reviews",
    indexes = [
        Index(name = "idx_review_jobplanet_id", columnList = "jobplanetReviewId", unique = true),
        Index(name = "idx_review_company_id_created_at", columnList = "company_id, reviewCreatedAt DESC"),
        Index(name = "idx_review_company_id_status", columnList = "company_id, status")
    ]
)
class Review(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    /**
     * 잡플래닛 리뷰 ID
     */
    @Column(nullable = false, unique = true)
    val jobplanetReviewId: Long,

    /**
     * 연관된 회사
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    var company: Company,

    /**
     * 리뷰 상태 (3: 정상, 12: 게시중단)
     */
    @Column(nullable = false)
    var status: Int,

    /**
     * 직무/직종
     */
    @Column(length = 200)
    var occupationName: String? = null,

    /**
     * 재직 상태 (현직/전직)
     */
    @Column(length = 100)
    var employStatusName: String? = null,

    /**
     * 리뷰 타입 (COMPANY_REVIEW, COMPANY_BLINDED_REVIEW 등)
     */
    @Column(length = 100)
    var reviewType: String? = null,

    /**
     * 총점 (별점)
     */
    @Column
    var rating: Double? = null,

    /**
     * 성장 가능성 점수
     */
    @Column
    var growthScore: Double? = null,

    /**
     * 급여/복지 점수
     */
    @Column
    var salaryScore: Double? = null,

    /**
     * 워라밸 점수
     */
    @Column
    var workLifeBalanceScore: Double? = null,

    /**
     * 사내 문화 점수
     */
    @Column
    var cultureScore: Double? = null,

    /**
     * 경영진 점수
     */
    @Column
    var managementScore: Double? = null,

    /**
     * 한 줄 요약
     */
    @Column(length = 1000)
    var summary: String? = null,

    /**
     * 장점
     */
    @Column(columnDefinition = "TEXT")
    var pros: String? = null,

    /**
     * 단점
     */
    @Column(columnDefinition = "TEXT")
    var cons: String? = null,

    /**
     * 경영진에게 바라는 점
     */
    @Column(columnDefinition = "TEXT")
    var toManagement: String? = null,

    /**
     * 좋아요 수
     */
    @Column
    var likeCount: Int = 0,

    /**
     * 리뷰 작성 연도
     */
    @Column(length = 20)
    var reviewYear: String? = null,

    /**
     * 잡플래닛에서 리뷰 작성 일시
     */
    @Column
    var reviewCreatedAt: LocalDate? = null,

    /**
     * 데이터 수집 일시
     */
    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    /**
     * 데이터 수정 일시
     */
    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    @PreUpdate
    fun onPreUpdate() {
        updatedAt = LocalDateTime.now()
    }

    /**
     * 리뷰 상태 Enum 반환
     */
    fun getReviewStatus(): ReviewStatus = ReviewStatus.fromCode(status)

    /**
     * 정상 리뷰 여부
     */
    fun isNormal(): Boolean = status == ReviewStatus.NORMAL.code

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Review) return false
        return jobplanetReviewId == other.jobplanetReviewId
    }

    override fun hashCode(): Int = jobplanetReviewId.hashCode()

    override fun toString(): String {
        return "Review(id=$id, jobplanetReviewId=$jobplanetReviewId, status=$status, rating=$rating)"
    }
}



