package com.crawling.planet.httpclient.core

import com.crawling.planet.httpclient.exception.WebClientInterfaceException
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.future
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.core.ParameterizedTypeReference
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import kotlin.coroutines.Continuation
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KType
import kotlin.reflect.full.callSuspend
import kotlin.reflect.jvm.javaType
import kotlin.reflect.jvm.kotlinFunction

private val logger = KotlinLogging.logger {}

/**
 * WebClient 기반 동적 프록시 InvocationHandler
 */
class WebClientInvocationHandler(
    private val interfaceClass: KClass<*>,
    private val webClient: WebClient,
    private val baseUrl: String,
    private val fallbackInstance: Any?,
    private val methodMetadataMap: Map<KFunction<*>, MethodMetadata>
) : InvocationHandler {
    
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    
    override fun invoke(proxy: Any, method: Method, args: Array<out Any>?): Any? {
        // Object 클래스 메서드 처리
        if (method.declaringClass == Object::class.java) {
            return handleObjectMethod(proxy, method, args)
        }
        
        val kotlinFunction = method.kotlinFunction
            ?: throw WebClientInterfaceException("Cannot find Kotlin function for method: ${method.name}")
        
        val metadata = methodMetadataMap[kotlinFunction]
            ?: throw WebClientInterfaceException("No metadata found for method: ${method.name}")
        
        return if (metadata.isSuspend) {
            invokeSuspend(metadata, args)
        } else {
            invokeBlocking(metadata, args)
        }
    }
    
    /**
     * suspend 함수 호출
     */
    private fun invokeSuspend(metadata: MethodMetadata, args: Array<out Any>?): Any? {
        val continuation = args?.lastOrNull() as? Continuation<*>
            ?: throw WebClientInterfaceException("Suspend function must have continuation")
        
        val actualArgs = args.dropLast(1).toTypedArray()
        
        // CompletableFuture를 사용하여 코루틴 결과를 continuation에 전달
        val future = coroutineScope.future {
            try {
                executeRequest(metadata, actualArgs)
            } catch (e: Exception) {
                val fallbackResult = tryFallback(metadata, actualArgs, e)
                if (fallbackResult !== FALLBACK_NOT_AVAILABLE) {
                    fallbackResult
                } else {
                    throw e
                }
            }
        }
        
        future.whenComplete { result, error ->
            @Suppress("UNCHECKED_CAST")
            val cont = continuation as Continuation<Any?>
            if (error != null) {
                cont.resumeWith(Result.failure(error.cause ?: error))
            } else {
                cont.resumeWith(Result.success(result))
            }
        }
        
        return kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
    }
    
    /**
     * 일반 함수 호출 (Mono 반환)
     */
    private fun invokeBlocking(metadata: MethodMetadata, args: Array<out Any>?): Any? {
        return executeRequestAsMono(metadata, args?.toList() ?: emptyList())
            .onErrorResume { e ->
                val fallbackResult = tryFallbackBlocking(metadata, args, e)
                if (fallbackResult !== FALLBACK_NOT_AVAILABLE) {
                    Mono.justOrEmpty(fallbackResult)
                } else {
                    Mono.error(e)
                }
            }
    }
    
    /**
     * HTTP 요청 실행 (suspend)
     */
    private suspend fun executeRequest(metadata: MethodMetadata, args: Array<out Any>): Any? {
        val uri = buildUri(metadata, args.toList())
        val headers = buildHeaders(metadata, args.toList())
        val body = extractBody(metadata, args.toList())
        
        logger.debug { "Executing ${metadata.httpMethod} $baseUrl$uri" }
        
        var requestSpec = webClient
            .method(metadata.httpMethod)
            .uri(uri)
        
        headers.forEach { (name, value) ->
            requestSpec = requestSpec.header(name, value)
        }
        
        val responseSpec = if (body != null) {
            requestSpec.bodyValue(body).retrieve()
        } else {
            requestSpec.retrieve()
        }
        
        return responseSpec
            .bodyToMono(createTypeReference(metadata.returnType))
            .awaitSingleOrNull()
    }
    
    /**
     * HTTP 요청 실행 (Mono)
     */
    private fun executeRequestAsMono(metadata: MethodMetadata, args: List<Any?>): Mono<Any?> {
        val uri = buildUri(metadata, args)
        val headers = buildHeaders(metadata, args)
        val body = extractBody(metadata, args)
        
        logger.debug { "Executing ${metadata.httpMethod} $baseUrl$uri" }
        
        var requestSpec = webClient
            .method(metadata.httpMethod)
            .uri(uri)
        
        headers.forEach { (name, value) ->
            requestSpec = requestSpec.header(name, value)
        }
        
        val responseSpec = if (body != null) {
            requestSpec.bodyValue(body).retrieve()
        } else {
            requestSpec.retrieve()
        }
        
        return responseSpec.bodyToMono(createTypeReference(metadata.returnType))
    }
    
    /**
     * URI 빌드 (Path Variable, Query Param 처리)
     */
    private fun buildUri(metadata: MethodMetadata, args: List<Any?>): String {
        var path = metadata.path
        val queryParams = mutableListOf<String>()
        
        metadata.parameters.forEachIndexed { index, param ->
            val value = args.getOrNull(index)
            
            when (param.type) {
                ParameterType.PATH_VARIABLE -> {
                    if (value != null) {
                        path = path.replace("{${param.name}}", value.toString())
                    }
                }
                ParameterType.REQUEST_PARAM -> {
                    val actualValue = value?.toString() ?: param.defaultValue
                    if (actualValue != null || param.required) {
                        actualValue?.let { queryParams.add("${param.name}=$it") }
                    }
                }
                else -> { /* ignore */ }
            }
        }
        
        return if (queryParams.isNotEmpty()) {
            "$path?${queryParams.joinToString("&")}"
        } else {
            path
        }
    }
    
    /**
     * 헤더 빌드
     */
    private fun buildHeaders(metadata: MethodMetadata, args: List<Any?>): Map<String, String> {
        val headers = metadata.staticHeaders.toMutableMap()
        
        metadata.parameters.forEachIndexed { index, param ->
            val value = args.getOrNull(index)
            
            when (param.type) {
                ParameterType.HEADER -> {
                    value?.let { headers[param.name] = it.toString() }
                }
                ParameterType.HEADER_MAP -> {
                    @Suppress("UNCHECKED_CAST")
                    (value as? Map<String, String>)?.let { headers.putAll(it) }
                }
                else -> { /* ignore */ }
            }
        }
        
        return headers
    }
    
    /**
     * Request Body 추출
     */
    private fun extractBody(metadata: MethodMetadata, args: List<Any?>): Any? {
        metadata.parameters.forEachIndexed { index, param ->
            if (param.type == ParameterType.REQUEST_BODY) {
                return args.getOrNull(index)
            }
        }
        return null
    }
    
    /**
     * Fallback 시도 (suspend)
     */
    private suspend fun tryFallback(metadata: MethodMetadata, args: Array<out Any>, exception: Exception): Any? {
        if (fallbackInstance == null) return FALLBACK_NOT_AVAILABLE
        
        logger.warn(exception) { "Request failed, trying fallback for ${metadata.function.name}" }
        
        return try {
            val fallbackMethod = fallbackInstance::class.members
                .filterIsInstance<KFunction<*>>()
                .find { it.name == metadata.function.name }
            
            if (fallbackMethod != null) {
                if (fallbackMethod.isSuspend) {
                    fallbackMethod.callSuspend(fallbackInstance, *args)
                } else {
                    fallbackMethod.call(fallbackInstance, *args)
                }
            } else {
                FALLBACK_NOT_AVAILABLE
            }
        } catch (e: Exception) {
            logger.error(e) { "Fallback also failed for ${metadata.function.name}" }
            FALLBACK_NOT_AVAILABLE
        }
    }
    
    /**
     * Fallback 시도 (blocking)
     */
    private fun tryFallbackBlocking(metadata: MethodMetadata, args: Array<out Any>?, exception: Throwable): Any? {
        if (fallbackInstance == null) return FALLBACK_NOT_AVAILABLE
        
        logger.warn(exception) { "Request failed, trying fallback for ${metadata.function.name}" }
        
        return try {
            val fallbackMethod = fallbackInstance::class.members
                .filterIsInstance<KFunction<*>>()
                .find { it.name == metadata.function.name }
            
            if (fallbackMethod != null && !fallbackMethod.isSuspend) {
                fallbackMethod.call(fallbackInstance, *(args ?: emptyArray()))
            } else {
                FALLBACK_NOT_AVAILABLE
            }
        } catch (e: Exception) {
            logger.error(e) { "Fallback also failed for ${metadata.function.name}" }
            FALLBACK_NOT_AVAILABLE
        }
    }
    
    /**
     * Object 클래스 메서드 처리
     */
    private fun handleObjectMethod(proxy: Any, method: Method, args: Array<out Any>?): Any? {
        return when (method.name) {
            "toString" -> "${interfaceClass.simpleName}Proxy(baseUrl=$baseUrl)"
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> proxy === args?.firstOrNull()
            else -> null
        }
    }
    
    /**
     * 반환 타입에 맞는 TypeReference 생성
     * KType을 java.lang.reflect.Type으로 변환하여 Spring WebClient가 올바르게 역직렬화할 수 있도록 함
     */
    @Suppress("UNCHECKED_CAST")
    @OptIn(ExperimentalStdlibApi::class)
    private fun createTypeReference(returnType: KType): ParameterizedTypeReference<Any?> {
        val javaType: Type = convertKTypeToJavaType(returnType)
        
        return object : ParameterizedTypeReference<Any?>() {
            override fun getType(): Type {
                return javaType
            }
        }
    }
    
    /**
     * KType을 java.lang.reflect.Type으로 변환
     * - 단순 타입: javaType extension 사용
     * - 제네릭 타입: ParameterizedType 구성
     * - Nullable 타입: Java에서는 nullability 정보가 없으므로 기본 타입으로 변환
     */
    @OptIn(ExperimentalStdlibApi::class)
    private fun convertKTypeToJavaType(kType: KType): Type {
        // 제네릭 인자가 있는 경우
        val arguments = kType.arguments
        if (arguments.isNotEmpty()) {
            val rawType = kType.classifier as? KClass<*>
                ?: return kType.javaType
            
            val typeArguments = arguments.map { projection ->
                projection.type?.let { convertKTypeToJavaType(it) } ?: Any::class.java
            }.toTypedArray()
            
            return KTypeParameterizedType(rawType.java, typeArguments)
        }
        
        // 단순 타입의 경우 javaType 사용
        return kType.javaType
    }
    
    /**
     * 제네릭 타입을 위한 ParameterizedType 구현
     */
    private class KTypeParameterizedType(
        private val rawType: Class<*>,
        private val typeArguments: Array<Type>
    ) : ParameterizedType {
        
        override fun getActualTypeArguments(): Array<Type> = typeArguments
        
        override fun getRawType(): Type = rawType
        
        override fun getOwnerType(): Type? = null
        
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ParameterizedType) return false
            
            return rawType == other.rawType &&
                    typeArguments.contentEquals(other.actualTypeArguments) &&
                    ownerType == other.ownerType
        }
        
        override fun hashCode(): Int {
            var result = rawType.hashCode()
            result = 31 * result + typeArguments.contentHashCode()
            return result
        }
        
        override fun toString(): String {
            return if (typeArguments.isEmpty()) {
                rawType.typeName
            } else {
                "${rawType.typeName}<${typeArguments.joinToString(", ") { it.typeName }}>"
            }
        }
    }
    
    companion object {
        private val FALLBACK_NOT_AVAILABLE = Any()
    }
}
