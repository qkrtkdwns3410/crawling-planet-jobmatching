package com.crawling.planet.crawler.client

import com.crawling.planet.domain.dto.JobplanetApiResponse
import com.crawling.planet.httpclient.annotation.*

/**
 * 잡플래닛 리뷰 API 선언적 클라이언트
 *
 * 사용 예시:
 * ```
 * @Service
 * class ReviewService(
 *     private val jobplanetReviewClient: JobplanetReviewClient
 * ) {
 *     suspend fun fetchReviews(companyId: Long): JobplanetApiResponse? {
 *         return jobplanetReviewClient.getCompanyReviews(
 *             companyId = companyId,
 *             page = 1
 *         )
 *     }
 * }
 * ```
 */
@WebClientInterface(
    name = "jobplanetReviewClient",
    baseUrl = "\${jobplanet.base-url}",
    fallback = JobplanetReviewClientFallback::class
)
interface JobplanetReviewClient {
    
    /**
     * 특정 회사의 리뷰 목록 조회
     *
     * @param companyId 회사 ID
     * @param page 페이지 번호 (기본값: 1)
     * @param ssrAuth SSR 인증 토큰
     * @param cookie 인증 쿠키
     * @return 리뷰 응답 (실패 시 null)
     */
    @Get(
        value = "/api/v4/companies/reviews/list",
        headers = [
            "sec-ch-ua: \"Microsoft Edge\";v=\"143\", \"Chromium\";v=\"143\"",
            "sec-ch-ua-mobile: ?0",
            "sec-ch-ua-platform: \"Windows\"",
            "sec-fetch-dest: empty",
            "sec-fetch-mode: cors",
            "sec-fetch-site: same-origin"
        ]
    )
    suspend fun getCompanyReviews(
        @RequestParam("company_id") companyId: Long,
        @RequestParam("page") page: Int = 1,
        @RequestParam("device") device: String = "desktop",
        @Header("jp-ssr-auth") ssrAuth: String,
        @Header("Cookie") cookie: String
    ): JobplanetApiResponse?
}

/**
 * Fallback 구현
 * API 호출 실패 시 기본 응답 반환
 */
class JobplanetReviewClientFallback : JobplanetReviewClient {
    
    override suspend fun getCompanyReviews(
        companyId: Long,
        page: Int,
        device: String,
        ssrAuth: String,
        cookie: String
    ): JobplanetApiResponse? {
        // 실패 시 null 반환 (또는 캐시된 데이터 반환 등 대체 로직)
        return null
    }
}


