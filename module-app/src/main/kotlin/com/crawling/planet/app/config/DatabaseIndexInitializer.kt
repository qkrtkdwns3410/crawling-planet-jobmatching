package com.crawling.planet.app.config

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

@Component
class DatabaseIndexInitializer {

    @PersistenceContext
    private lateinit var entityManager: EntityManager

    @EventListener(ApplicationReadyEvent::class)
    @Transactional
    fun createTrigramIndex() {
        try {
            entityManager.createNativeQuery(
                "CREATE INDEX IF NOT EXISTS idx_company_name_trgm ON companies USING GIN (name gin_trgm_ops)"
            ).executeUpdate()
            logger.info { "pg_trgm GIN 인덱스 생성 완료 (idx_company_name_trgm)" }
        } catch (e: Exception) {
            logger.warn(e) { "pg_trgm GIN 인덱스 생성 실패 - pg_trgm 확장 미설치 가능" }
        }
    }
}
