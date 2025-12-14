package com.crawling.planet.httpclient.annotation

import com.crawling.planet.httpclient.config.WebClientInterfaceRegistrar
import org.springframework.context.annotation.Import

/**
 * WebClient 인터페이스 자동 스캔 및 등록 활성화
 *
 * @property basePackages 스캔할 패키지 목록 (비어있으면 어노테이션이 선언된 클래스의 패키지 사용)
 * @property basePackageClasses 스캔할 패키지를 결정할 클래스들
 *
 * 사용 예시:
 * ```
 * @SpringBootApplication
 * @EnableWebClients(basePackages = ["com.example.api"])
 * class Application
 * ```
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@Import(WebClientInterfaceRegistrar::class)
annotation class EnableWebClients(
    val basePackages: Array<String> = [],
    val basePackageClasses: Array<kotlin.reflect.KClass<*>> = []
)

