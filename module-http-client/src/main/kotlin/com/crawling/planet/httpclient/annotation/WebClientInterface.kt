package com.crawling.planet.httpclient.annotation

import kotlin.reflect.KClass

/**
 * WebClient 기반 선언적 HTTP 클라이언트 인터페이스 마커
 *
 * @property name 클라이언트 이름 (Bean 이름으로 사용)
 * @property baseUrl 기본 URL (SpEL 표현식 지원: ${property.name})
 * @property fallback Fallback 구현 클래스 (인터페이스를 구현해야 함)
 *
 * 사용 예시:
 * ```
 * @WebClientInterface(
 *     name = "userApi",
 *     baseUrl = "\${api.user.base-url}",
 *     fallback = UserApiFallback::class
 * )
 * interface UserApi {
 *     @Get("/users/{id}")
 *     suspend fun getUser(@PathVariable id: Long): User
 * }
 * ```
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class WebClientInterface(
    val name: String = "",
    val baseUrl: String = "",
    val fallback: KClass<*> = Unit::class
)


