package com.crawling.planet.crawler.api

/**
 * 잡플래닛 크롤러 모듈의 외부 노출 API
 *
 * 다른 모듈에서 크롤러 기능을 사용할 때 이 인터페이스를 통해 접근합니다.
 */
interface JobplanetCrawlerApi {

    /**
     * 잡플래닛 로그인 수행
     * @return 로그인 성공 여부
     */
    fun login(): Boolean

    /**
     * 현재 페이지 URL 반환
     * @return 현재 URL
     */
    fun getCurrentUrl(): String?

    /**
     * 특정 URL로 이동
     * @param url 이동할 URL
     */
    fun navigateTo(url: String)

    /**
     * 브라우저 종료
     */
    fun close()
}

