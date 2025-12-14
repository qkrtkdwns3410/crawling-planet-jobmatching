package com.crawling.planet.domain.entity

import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * 크롤링 에러 로그 엔티티
 */
@Entity
@Table(
    name = "crawling_error_log",
    indexes = [
        Index(name = "idx_error_log_company_id", columnList = "companyId"),
        Index(name = "idx_error_log_created_at", columnList = "createdAt")
    ]
)
class CrawlingErrorLog(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    /**
     * 회사 ID
     */
    @Column(nullable = false)
    val companyId: Long,

    /**
     * 에러 타입
     */
    @Column(length = 100)
    val errorType: String? = null,

    /**
     * 에러 메시지
     */
    @Column(columnDefinition = "TEXT")
    val errorMessage: String? = null,

    /**
     * 스택 트레이스
     */
    @Column(columnDefinition = "TEXT")
    val stackTrace: String? = null,

    /**
     * 생성 시간
     */
    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
) {
    companion object {
        fun fromException(companyId: Long, exception: Throwable): CrawlingErrorLog {
            return CrawlingErrorLog(
                companyId = companyId,
                errorType = exception.javaClass.simpleName,
                errorMessage = exception.message,
                stackTrace = exception.stackTraceToString().take(4000) // 최대 4000자
            )
        }
    }

    override fun toString(): String {
        return "CrawlingErrorLog(id=$id, companyId=$companyId, errorType=$errorType)"
    }
}


