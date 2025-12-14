package com.crawling.planet.httpclient.annotation

/**
 * HTTP GET 요청
 *
 * @property value 요청 경로 (baseUrl 기준 상대 경로)
 * @property headers 정적 헤더 (예: ["Accept: application/json", "X-Custom: value"])
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class Get(
    val value: String = "",
    val headers: Array<String> = []
)

/**
 * HTTP POST 요청
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class Post(
    val value: String = "",
    val headers: Array<String> = []
)

/**
 * HTTP PUT 요청
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class Put(
    val value: String = "",
    val headers: Array<String> = []
)

/**
 * HTTP DELETE 요청
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class Delete(
    val value: String = "",
    val headers: Array<String> = []
)

/**
 * HTTP PATCH 요청
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class Patch(
    val value: String = "",
    val headers: Array<String> = []
)

