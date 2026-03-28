package com.crawling.planet.domain.entity

import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * 크롤링 상태
 */
enum class CrawlingStatus {
    PENDING,
    RUNNING,
    PAUSED,
    COMPLETED,
    FAILED
}

/**
 * 크롤링 진행 상태 엔티티
 */
@Entity
@Table(name = "crawling_progress")
class CrawlingProgress(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    /**
     * 시작 회사 ID
     */
    @Column(nullable = false)
    val startCompanyId: Long,

    /**
     * 종료 회사 ID
     */
    @Column(nullable = false)
    val endCompanyId: Long,

    /**
     * 현재 처리 중인 회사 ID
     */
    @Column(nullable = false)
    var currentCompanyId: Long,

    /**
     * 크롤링 상태
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    var status: CrawlingStatus = CrawlingStatus.PENDING,

    /**
     * 처리된 회사 수
     */
    @Column
    var companiesProcessed: Long = 0,

    /**
     * 수집된 리뷰 수
     */
    @Column
    var reviewsCollected: Long = 0,

    /**
     * 에러 수
     */
    @Column
    var errorsCount: Long = 0,

    /**
     * 시작 시간
     */
    @Column(nullable = false, updatable = false)
    val startedAt: LocalDateTime = LocalDateTime.now(),

    /**
     * 업데이트 시간
     */
    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),

    /**
     * 완료 시간
     */
    @Column
    var completedAt: LocalDateTime? = null
) {
    @PreUpdate
    fun onPreUpdate() {
        updatedAt = LocalDateTime.now()
    }

    /**
     * 진행률 계산 (%)
     */
    fun calculateProgress(): Double {
        val total = endCompanyId - startCompanyId + 1
        val processed = currentCompanyId - startCompanyId
        return if (total > 0) (processed.toDouble() / total * 100) else 0.0
    }

    /**
     * 상태 업데이트
     */
    fun updateProgress(
        currentId: Long,
        companiesProcessed: Long,
        reviewsCollected: Long,
        errorsCount: Long
    ) {
        this.currentCompanyId = currentId
        this.companiesProcessed = companiesProcessed
        this.reviewsCollected = reviewsCollected
        this.errorsCount = errorsCount
    }

    /**
     * 완료 처리
     */
    fun complete() {
        this.status = CrawlingStatus.COMPLETED
        this.completedAt = LocalDateTime.now()
    }

    /**
     * 실패 처리
     */
    fun fail() {
        this.status = CrawlingStatus.FAILED
        this.completedAt = LocalDateTime.now()
    }

    override fun toString(): String {
        return "CrawlingProgress(id=$id, status=$status, progress=${calculateProgress()}%)"
    }
}



