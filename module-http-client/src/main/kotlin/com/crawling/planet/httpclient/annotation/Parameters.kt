package com.crawling.planet.httpclient.annotation

/**
 * URL 경로 변수
 *
 * 사용 예시:
 * ```
 * @Get("/users/{id}")
 * suspend fun getUser(@PathVariable("id") userId: Long): User
 * ```
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class PathVariable(
    val value: String = ""
)

/**
 * 쿼리 파라미터
 *
 * 사용 예시:
 * ```
 * @Get("/users")
 * suspend fun searchUsers(
 *     @RequestParam("name") name: String,
 *     @RequestParam("page") page: Int = 1
 * ): List<User>
 * ```
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class RequestParam(
    val value: String = "",
    val required: Boolean = true,
    val defaultValue: String = ""
)

/**
 * 요청 본문
 *
 * 사용 예시:
 * ```
 * @Post("/users")
 * suspend fun createUser(@RequestBody user: CreateUserRequest): User
 * ```
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class RequestBody

/**
 * 동적 헤더 값
 *
 * 사용 예시:
 * ```
 * @Get("/protected")
 * suspend fun getProtected(@Header("Authorization") token: String): Data
 * ```
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class Header(
    val value: String
)

/**
 * 다중 헤더 (Map 타입 파라미터에 사용)
 *
 * 사용 예시:
 * ```
 * @Get("/api")
 * suspend fun callApi(@HeaderMap headers: Map<String, String>): Data
 * ```
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class HeaderMap


