package com.crawling.planet.domain.repository

import com.crawling.planet.domain.entity.Company
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Repository
interface CompanyRepository : JpaRepository<Company, Long> {
    
    /**
     * 잡플래닛 회사 ID로 회사 조회
     */
    fun findByJobplanetId(jobplanetId: Long): Optional<Company>
    
    /**
     * 잡플래닛 회사 ID 존재 여부 확인
     */
    fun existsByJobplanetId(jobplanetId: Long): Boolean
    
    /**
     * 회사명으로 회사 목록 조회 (LIKE 검색)
     */
    fun findByNameContainingIgnoreCase(name: String): List<Company>
    
    /**
     * 잡플래닛 회사 ID 목록으로 회사 목록 조회
     */
    fun findByJobplanetIdIn(jobplanetIds: List<Long>): List<Company>
    
    /**
     * 회사의 리뷰 수를 원자적으로 증가시킵니다.
     * 동시성 환경에서 lost update를 방지합니다.
     *
     * @param id 회사 ID
     * @param increment 증가시킬 리뷰 수
     * @return 업데이트된 행 수
     */
    @Transactional
    @Modifying
    @Query("UPDATE Company c SET c.reviewCount = c.reviewCount + :increment WHERE c.id = :id")
    fun incrementReviewCount(id: Long, increment: Int): Int
}


