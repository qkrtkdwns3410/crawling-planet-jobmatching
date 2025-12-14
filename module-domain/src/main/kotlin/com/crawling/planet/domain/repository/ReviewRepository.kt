package com.crawling.planet.domain.repository

import com.crawling.planet.domain.entity.Review
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface ReviewRepository : JpaRepository<Review, Long> {
    
    /**
     * 잡플래닛 리뷰 ID로 리뷰 조회
     */
    fun findByJobplanetReviewId(jobplanetReviewId: Long): Optional<Review>
    
    /**
     * 잡플래닛 리뷰 ID 존재 여부 확인
     */
    fun existsByJobplanetReviewId(jobplanetReviewId: Long): Boolean
    
    /**
     * 잡플래닛 리뷰 ID 목록으로 존재하는 ID 조회
     */
    @Query("SELECT r.jobplanetReviewId FROM Review r WHERE r.jobplanetReviewId IN :ids")
    fun findExistingJobplanetReviewIds(@Param("ids") ids: List<Long>): List<Long>
    
    /**
     * 회사 ID로 리뷰 목록 조회 (페이징)
     */
    fun findByCompanyId(companyId: Long, pageable: Pageable): Page<Review>
    
    /**
     * 회사 ID로 리뷰 목록 조회 (전체)
     */
    fun findByCompanyId(companyId: Long): List<Review>
    
    /**
     * 회사의 잡플래닛 ID로 리뷰 목록 조회
     */
    fun findByCompanyJobplanetId(jobplanetId: Long): List<Review>
    
    /**
     * 특정 상태의 리뷰 목록 조회
     */
    fun findByStatus(status: Int): List<Review>
    
    /**
     * 회사별 리뷰 수 조회
     */
    @Query("SELECT COUNT(r) FROM Review r WHERE r.company.id = :companyId")
    fun countByCompanyId(@Param("companyId") companyId: Long): Long
    
    /**
     * 정상 리뷰만 조회 (status = 3)
     */
    @Query("SELECT r FROM Review r WHERE r.company.jobplanetId = :jobplanetId AND r.status = 3")
    fun findNormalReviewsByCompanyJobplanetId(@Param("jobplanetId") jobplanetId: Long): List<Review>
}


