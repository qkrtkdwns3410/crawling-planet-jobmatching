package com.crawling.planet.domain.repository

import com.crawling.planet.domain.entity.CrawlingProgress
import com.crawling.planet.domain.entity.CrawlingStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface CrawlingProgressRepository : JpaRepository<CrawlingProgress, Long> {
    
    /**
     * 특정 상태의 진행 상황 조회
     */
    fun findByStatus(status: CrawlingStatus): List<CrawlingProgress>
    
    /**
     * 가장 최근 진행 상황 조회
     */
    fun findTopByOrderByStartedAtDesc(): Optional<CrawlingProgress>
    
    /**
     * 실행 중인 진행 상황 조회
     */
    fun findByStatusIn(statuses: List<CrawlingStatus>): List<CrawlingProgress>
}


