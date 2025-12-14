package com.crawling.planet.httpclient.core

import com.crawling.planet.httpclient.annotation.WebClientInterface
import com.crawling.planet.httpclient.exception.WebClientInterfaceException
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.BeanFactory
import org.springframework.core.env.Environment
import org.springframework.web.reactive.function.client.WebClient
import java.lang.reflect.Proxy
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.isSubclassOf

private val logger = KotlinLogging.logger {}

/**
 * WebClient 프록시 팩토리
 * 인터페이스를 분석하여 WebClient 기반 프록시 인스턴스 생성
 */
class WebClientProxyFactory(
    private val webClientBuilder: WebClient.Builder,
    private val environment: Environment,
    private val beanFactory: BeanFactory
) {
    
    /**
     * 프록시 인스턴스 생성
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> create(interfaceClass: KClass<T>): T {
        require(interfaceClass.java.isInterface) {
            "${interfaceClass.simpleName} must be an interface"
        }
        
        val annotation = interfaceClass.findAnnotation<WebClientInterface>()
            ?: throw WebClientInterfaceException(
                "${interfaceClass.simpleName} must be annotated with @WebClientInterface"
            )
        
        // baseUrl 처리 (SpEL/프로퍼티 플레이스홀더 지원)
        val baseUrl = resolveBaseUrl(annotation.baseUrl)
        
        // WebClient 생성
        val webClient = webClientBuilder
            .clone()
            .baseUrl(baseUrl)
            .build()
        
        // Fallback 인스턴스 생성
        val fallbackInstance = createFallbackInstance(annotation, interfaceClass)
        
        // 메서드 메타데이터 파싱
        val methodMetadataMap = MethodMetadataParser.parse(interfaceClass)
        
        logger.info { 
            "Creating WebClient proxy for ${interfaceClass.simpleName} " +
            "(baseUrl=$baseUrl, fallback=${fallbackInstance?.javaClass?.simpleName ?: "none"})" 
        }
        
        // 프록시 생성
        val handler = WebClientInvocationHandler(
            interfaceClass = interfaceClass,
            webClient = webClient,
            baseUrl = baseUrl,
            fallbackInstance = fallbackInstance,
            methodMetadataMap = methodMetadataMap
        )
        
        return Proxy.newProxyInstance(
            interfaceClass.java.classLoader,
            arrayOf(interfaceClass.java),
            handler
        ) as T
    }
    
    /**
     * baseUrl 해석 (프로퍼티 플레이스홀더 처리)
     */
    private fun resolveBaseUrl(baseUrl: String): String {
        // 먼저 placeholder 해석 수행
        val resolvedUrl = environment.resolvePlaceholders(baseUrl)
        
        // 해석된 URL 검증
        when {
            resolvedUrl.isBlank() -> {
                throw WebClientInterfaceException(
                    "Resolved baseUrl is blank. Original value: '$baseUrl'"
                )
            }
            resolvedUrl == baseUrl && baseUrl.startsWith("\${") -> {
                throw WebClientInterfaceException(
                    "baseUrl placeholder was not resolved. " +
                    "Original: '$baseUrl'. " +
                    "Please ensure the property is defined in your configuration."
                )
            }
            Regex("\\$\\{[^}]*\\}").containsMatchIn(resolvedUrl) -> {
                throw WebClientInterfaceException(
                    "Resolved baseUrl contains unresolved placeholders. " +
                    "Original: '$baseUrl', Resolved: '$resolvedUrl'. " +
                    "Please check your configuration properties."
                )
            }
        }
        
        return resolvedUrl
    }
    
    /**
     * Fallback 인스턴스 생성
     */
    private fun <T : Any> createFallbackInstance(
        annotation: WebClientInterface, 
        interfaceClass: KClass<T>
    ): Any? {
        val fallbackClass = annotation.fallback
        
        if (fallbackClass == Unit::class) {
            return null
        }
        
        // Fallback 클래스가 인터페이스를 구현하는지 확인
        if (!fallbackClass.isSubclassOf(interfaceClass)) {
            throw WebClientInterfaceException(
                "Fallback class ${fallbackClass.simpleName} must implement ${interfaceClass.simpleName}"
            )
        }
        
        return try {
            // Spring Bean으로 등록된 경우 가져오기 시도
            beanFactory.getBean(fallbackClass.java)
        } catch (e: Exception) {
            // Bean이 없으면 직접 인스턴스 생성
            try {
                fallbackClass.java.getDeclaredConstructor().newInstance()
            } catch (e2: Exception) {
                logger.warn { 
                    "Cannot create fallback instance for ${fallbackClass.simpleName}. " +
                    "Make sure it has a no-arg constructor or is registered as a Spring bean." 
                }
                null
            }
        }
    }
}

