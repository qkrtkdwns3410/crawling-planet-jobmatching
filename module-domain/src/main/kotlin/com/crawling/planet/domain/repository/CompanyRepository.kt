package com.crawling.planet.domain.repository

import com.crawling.planet.domain.entity.Company
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Repository
interface CompanyRepository : JpaRepository<Company, Long> {

    fun findByJobplanetId(jobplanetId: Long): Optional<Company>

    fun existsByJobplanetId(jobplanetId: Long): Boolean

    fun findByNameContainingIgnoreCase(name: String): List<Company>

    fun findByJobplanetIdIn(jobplanetIds: List<Long>): List<Company>

    @Transactional
    @Modifying
    @Query("UPDATE Company c SET c.reviewCount = c.reviewCount + :increment WHERE c.id = :id")
    fun incrementReviewCount(id: Long, increment: Int): Int

    @Query(
        value = "SELECT * FROM companies WHERE name % :searchName ORDER BY similarity(name, :searchName) DESC LIMIT 1",
        nativeQuery = true
    )
    fun findMostSimilarByName(@Param("searchName") searchName: String): Company?

    @Query(
        value = "SELECT * FROM companies WHERE name % :searchName ORDER BY similarity(name, :searchName) DESC LIMIT :limitCount",
        nativeQuery = true
    )
    fun findSimilarByName(
        @Param("searchName") searchName: String,
        @Param("limitCount") limitCount: Int = 5
    ): List<Company>

    @Query("SELECT c FROM Company c WHERE c.averageRating IS NULL AND c.reviewCount > 0")
    fun findCompaniesNeedingRatingUpdate(): List<Company>

    @Query("SELECT c FROM Company c WHERE c.reviewCount < 3")
    fun findCompaniesNeedingReviews(): List<Company>

    @Transactional
    @Modifying
    @Query(
        value = "UPDATE companies SET average_rating = :rating, industry = COALESCE(:industry, industry), logo_url = COALESCE(:logoUrl, logo_url) WHERE id = :id",
        nativeQuery = true
    )
    fun updateCompanyDetails(
        @Param("id") id: Long,
        @Param("rating") rating: Double?,
        @Param("industry") industry: String?,
        @Param("logoUrl") logoUrl: String?
    ): Int
}
