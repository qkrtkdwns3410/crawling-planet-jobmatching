package com.crawling.planet.crawler.diagnostics

import com.crawling.planet.crawler.auth.CookieTokenStore
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicReference

@Component
class CrawlerDiagnosticsService(
    private val cookieTokenStore: CookieTokenStore
) {
    private val startedAt = Instant.now()
    private val loginState = AtomicReference(LoginState())
    private val apiState = AtomicReference(ApiState())
    private val jobState = AtomicReference(JobState())

    fun recordLoginReuse(source: String) {
        val now = Instant.now()
        val cookieCount = currentCookieCount()
        loginState.updateAndGet {
            it.copy(
                lastAttemptAt = now,
                lastSuccessAt = now,
                lastReuseAt = now,
                lastSource = source,
                lastFailureMessage = null,
                cookieCount = cookieCount
            )
        }
    }

    fun recordLoginAttempt(source: String) {
        val now = Instant.now()
        loginState.updateAndGet {
            it.copy(
                lastAttemptAt = now,
                lastSource = source,
                lastFailureMessage = null
            )
        }
    }

    fun recordLoginSuccess(source: String) {
        val now = Instant.now()
        val cookieCount = currentCookieCount()
        loginState.updateAndGet {
            it.copy(
                lastSuccessAt = now,
                lastSource = source,
                lastFailureMessage = null,
                cookieCount = cookieCount
            )
        }
    }

    fun recordLoginFailure(source: String, message: String?) {
        val now = Instant.now()
        loginState.updateAndGet {
            it.copy(
                lastFailureAt = now,
                lastSource = source,
                lastFailureMessage = message?.take(300)
            )
        }
    }

    fun recordApiRequest(path: String, companyId: Long?) {
        val now = Instant.now()
        val cookieCount = currentCookieCount()
        apiState.updateAndGet {
            it.copy(
                lastRequestAt = now,
                lastPath = path,
                lastCompanyId = companyId,
                lastCookieCount = cookieCount,
                totalRequests = it.totalRequests + 1
            )
        }
    }

    fun recordApiResponse(path: String, companyId: Long?, statusCode: Int) {
        val now = Instant.now()
        apiState.updateAndGet {
            val failure = statusCode >= 400
            it.copy(
                lastPath = path,
                lastCompanyId = companyId,
                lastStatusCode = statusCode,
                lastResponseAt = now,
                lastSuccessAt = if (failure) it.lastSuccessAt else now,
                lastFailureAt = if (failure) now else it.lastFailureAt,
                lastFailureMessage = if (failure) "HTTP $statusCode" else null,
                consecutiveFailures = if (failure) it.consecutiveFailures + 1 else 0
            )
        }
    }

    fun recordTransportError(path: String, companyId: Long?, message: String?) {
        val now = Instant.now()
        apiState.updateAndGet {
            it.copy(
                lastPath = path,
                lastCompanyId = companyId,
                lastStatusCode = null,
                lastResponseAt = now,
                lastFailureAt = now,
                lastFailureMessage = message?.take(300),
                consecutiveFailures = it.consecutiveFailures + 1
            )
        }
    }

    fun recordJobStarted(type: String, detail: String? = null) {
        jobState.set(
            JobState(
                type = type,
                status = "RUNNING",
                startedAt = Instant.now(),
                detail = detail
            )
        )
    }

    fun recordJobFinished(type: String, success: Boolean, summary: String? = null) {
        val now = Instant.now()
        jobState.updateAndGet {
            it.copy(
                type = type,
                status = if (success) "SUCCESS" else "FAILED",
                finishedAt = now,
                summary = summary?.take(500)
            )
        }
    }

    fun snapshot(): Snapshot {
        val token = cookieTokenStore.get()
        return Snapshot(
            serviceStartedAt = startedAt,
            token = TokenSnapshot(
                present = token != null,
                expired = cookieTokenStore.isExpired(),
                ageSeconds = token?.obtainedAt?.until(Instant.now(), ChronoUnit.SECONDS),
                cookieCount = currentCookieCount()
            ),
            login = loginState.get().toSnapshot(),
            api = apiState.get().toSnapshot(),
            job = jobState.get().toSnapshot()
        )
    }

    private fun currentCookieCount(): Int {
        val allCookies = cookieTokenStore.getAllCookies()
        if (allCookies.isNotEmpty()) {
            return allCookies.size
        }

        var count = 0
        if (!cookieTokenStore.getAccessToken().isNullOrBlank()) count++
        if (!cookieTokenStore.getRefreshToken().isNullOrBlank()) count++
        return count
    }

    data class Snapshot(
        val serviceStartedAt: Instant,
        val token: TokenSnapshot,
        val login: LoginSnapshot,
        val api: ApiSnapshot,
        val job: JobSnapshot
    )

    data class TokenSnapshot(
        val present: Boolean,
        val expired: Boolean,
        val ageSeconds: Long?,
        val cookieCount: Int
    )

    data class LoginSnapshot(
        val lastAttemptAt: Instant?,
        val lastSuccessAt: Instant?,
        val lastFailureAt: Instant?,
        val lastReuseAt: Instant?,
        val lastSource: String?,
        val lastFailureMessage: String?,
        val cookieCount: Int
    )

    data class ApiSnapshot(
        val lastRequestAt: Instant?,
        val lastResponseAt: Instant?,
        val lastSuccessAt: Instant?,
        val lastFailureAt: Instant?,
        val lastPath: String?,
        val lastCompanyId: Long?,
        val lastStatusCode: Int?,
        val lastFailureMessage: String?,
        val lastCookieCount: Int,
        val consecutiveFailures: Long,
        val totalRequests: Long
    )

    data class JobSnapshot(
        val type: String?,
        val status: String?,
        val startedAt: Instant?,
        val finishedAt: Instant?,
        val detail: String?,
        val summary: String?
    )

    private data class LoginState(
        val lastAttemptAt: Instant? = null,
        val lastSuccessAt: Instant? = null,
        val lastFailureAt: Instant? = null,
        val lastReuseAt: Instant? = null,
        val lastSource: String? = null,
        val lastFailureMessage: String? = null,
        val cookieCount: Int = 0
    ) {
        fun toSnapshot() = LoginSnapshot(
            lastAttemptAt = lastAttemptAt,
            lastSuccessAt = lastSuccessAt,
            lastFailureAt = lastFailureAt,
            lastReuseAt = lastReuseAt,
            lastSource = lastSource,
            lastFailureMessage = lastFailureMessage,
            cookieCount = cookieCount
        )
    }

    private data class ApiState(
        val lastRequestAt: Instant? = null,
        val lastResponseAt: Instant? = null,
        val lastSuccessAt: Instant? = null,
        val lastFailureAt: Instant? = null,
        val lastPath: String? = null,
        val lastCompanyId: Long? = null,
        val lastStatusCode: Int? = null,
        val lastFailureMessage: String? = null,
        val lastCookieCount: Int = 0,
        val consecutiveFailures: Long = 0,
        val totalRequests: Long = 0
    ) {
        fun toSnapshot() = ApiSnapshot(
            lastRequestAt = lastRequestAt,
            lastResponseAt = lastResponseAt,
            lastSuccessAt = lastSuccessAt,
            lastFailureAt = lastFailureAt,
            lastPath = lastPath,
            lastCompanyId = lastCompanyId,
            lastStatusCode = lastStatusCode,
            lastFailureMessage = lastFailureMessage,
            lastCookieCount = lastCookieCount,
            consecutiveFailures = consecutiveFailures,
            totalRequests = totalRequests
        )
    }

    private data class JobState(
        val type: String? = null,
        val status: String? = null,
        val startedAt: Instant? = null,
        val finishedAt: Instant? = null,
        val detail: String? = null,
        val summary: String? = null
    ) {
        fun toSnapshot() = JobSnapshot(
            type = type,
            status = status,
            startedAt = startedAt,
            finishedAt = finishedAt,
            detail = detail,
            summary = summary
        )
    }
}
