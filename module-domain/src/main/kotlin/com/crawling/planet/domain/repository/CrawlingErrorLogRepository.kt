package com.crawling.planet.domain.repository

import com.crawling.planet.domain.entity.CrawlingErrorLog
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface CrawlingErrorLogRepository : JpaRepository<CrawlingErrorLog, Long> {
    
    /**
     * 회사 ID로 에러 로그 조회
     */
    fun findByCompanyId(companyId: Long): List<CrawlingErrorLog>
    
    /**
     * 에러 타입으로 에러 로그 조회
     */
    fun findByErrorType(errorType: String, pageable: Pageable): Page<CrawlingErrorLog>
    
    /**
     * 최근 에러 로그 조회
     */
    fun findTop100ByOrderByCreatedAtDesc(): List<CrawlingErrorLog>
}


