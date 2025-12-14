package com.crawling.planet.domain.entity

import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * 회사 엔티티
 */
@Entity
@Table(
    name = "companies",
    indexes = [
        Index(name = "idx_company_jobplanet_id", columnList = "jobplanetId", unique = true),
        Index(name = "idx_company_name", columnList = "name")
    ]
)
class Company(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    /**
     * 잡플래닛 회사 ID
     */
    @Column(nullable = false, unique = true)
    val jobplanetId: Long,

    /**
     * 회사명
     */
    @Column(nullable = false, length = 500)
    var name: String,

    /**
     * 회사 로고 URL
     */
    @Column(length = 1000)
    var logoUrl: String? = null,

    /**
     * 산업 분류
     */
    @Column(length = 200)
    var industry: String? = null,

    /**
     * 평균 평점
     */
    @Column
    var averageRating: Double? = null,

    /**
     * 리뷰 총 개수
     */
    @Column
    var reviewCount: Int = 0,

    /**
     * 회사 규모
     */
    @Column(length = 100)
    var companySize: String? = null,

    /**
     * 설립 연도
     */
    @Column(length = 50)
    var establishedYear: String? = null,

    /**
     * 본사 위치
     */
    @Column(length = 500)
    var headquarters: String? = null,

    /**
     * 회사 홈페이지 URL
     */
    @Column(length = 500)
    var websiteUrl: String? = null,

    /**
     * 회사 소개 (간략)
     */
    @Column(columnDefinition = "TEXT")
    var description: String? = null,

    /**
     * 생성 일시
     */
    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    /**
     * 수정 일시
     */
    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),

    /**
     * 리뷰 목록
     */
    @OneToMany(mappedBy = "company", cascade = [CascadeType.ALL], orphanRemoval = true)
    val reviews: MutableList<Review> = mutableListOf()
) {
    @PreUpdate
    fun onPreUpdate() {
        updatedAt = LocalDateTime.now()
    }

    fun addReview(review: Review) {
        reviews.add(review)
        review.company = this
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Company) return false
        return jobplanetId == other.jobplanetId
    }

    override fun hashCode(): Int = jobplanetId.hashCode()

    override fun toString(): String {
        return "Company(id=$id, jobplanetId=$jobplanetId, name='$name')"
    }
}


