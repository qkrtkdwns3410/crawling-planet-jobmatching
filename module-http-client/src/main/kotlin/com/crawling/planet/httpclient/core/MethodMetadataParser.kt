package com.crawling.planet.httpclient.core

import com.crawling.planet.httpclient.annotation.*
import org.springframework.http.HttpMethod
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.valueParameters
import kotlin.reflect.jvm.javaMethod

/**
 * 메서드 메타데이터 파서
 * 인터페이스 메서드를 분석하여 HTTP 호출에 필요한 정보 추출
 * 
 * **주의**: 파라미터 이름 자동 감지를 사용하려면 프로젝트가 `-java-parameters` 옵션으로 
 * 컴파일되어야 합니다. 그렇지 않으면 어노테이션에 명시적으로 파라미터 이름을 지정해야 합니다.
 * 
 * 예시:
 * ```kotlin
 * // 어노테이션에 명시적 이름 지정 (권장)
 * @Get("/users/{id}")
 * suspend fun getUser(@PathVariable("id") userId: Long): User
 * 
 * // -java-parameters가 활성화된 경우에만 동작
 * @Get("/users/{id}")
 * suspend fun getUser(@PathVariable id: Long): User
 * ```
 */
object MethodMetadataParser {
    
    /**
     * 인터페이스의 모든 메서드 메타데이터 파싱
     */
    fun parse(interfaceClass: KClass<*>): Map<KFunction<*>, MethodMetadata> {
        return interfaceClass.members
            .filterIsInstance<KFunction<*>>()
            .filter { it.javaMethod?.declaringClass?.isInterface == true }
            .mapNotNull { function ->
                parseMethod(function)?.let { function to it }
            }
            .toMap()
    }
    
    /**
     * 단일 메서드 파싱
     */
    private fun parseMethod(function: KFunction<*>): MethodMetadata? {
        val (httpMethod, path, staticHeaders) = extractHttpMethodInfo(function) ?: return null
        
        val parameters = function.valueParameters.mapNotNull { param ->
            parseParameter(param)
        }
        
        return MethodMetadata(
            function = function,
            httpMethod = httpMethod,
            path = path,
            staticHeaders = staticHeaders,
            returnType = function.returnType,
            isSuspend = function.isSuspend,
            parameters = parameters
        )
    }
    
    /**
     * HTTP 메서드 어노테이션 추출
     */
    private fun extractHttpMethodInfo(function: KFunction<*>): HttpMethodInfo? {
        function.findAnnotation<Get>()?.let {
            return HttpMethodInfo(HttpMethod.GET, it.value, parseStaticHeaders(it.headers))
        }
        function.findAnnotation<Post>()?.let {
            return HttpMethodInfo(HttpMethod.POST, it.value, parseStaticHeaders(it.headers))
        }
        function.findAnnotation<Put>()?.let {
            return HttpMethodInfo(HttpMethod.PUT, it.value, parseStaticHeaders(it.headers))
        }
        function.findAnnotation<Delete>()?.let {
            return HttpMethodInfo(HttpMethod.DELETE, it.value, parseStaticHeaders(it.headers))
        }
        function.findAnnotation<Patch>()?.let {
            return HttpMethodInfo(HttpMethod.PATCH, it.value, parseStaticHeaders(it.headers))
        }
        return null
    }
    
    /**
     * 정적 헤더 파싱 ("Header-Name: value" 형식)
     */
    private fun parseStaticHeaders(headers: Array<String>): Map<String, String> {
        return headers.mapNotNull { header ->
            val colonIndex = header.indexOf(':')
            if (colonIndex > 0) {
                header.substring(0, colonIndex).trim() to header.substring(colonIndex + 1).trim()
            } else {
                null
            }
        }.toMap()
    }
    
    /**
     * 파라미터 어노테이션 파싱
     */
    private fun parseParameter(param: KParameter): ParameterMetadata? {
        param.findAnnotation<PathVariable>()?.let { annotation ->
            val name = resolveParameterName(
                annotationValue = annotation.value,
                param = param,
                annotationType = "PathVariable"
            )
            return ParameterMetadata(
                kParameter = param,
                type = ParameterType.PATH_VARIABLE,
                name = name
            )
        }
        
        param.findAnnotation<RequestParam>()?.let { annotation ->
            val name = resolveParameterName(
                annotationValue = annotation.value,
                param = param,
                annotationType = "RequestParam"
            )
            return ParameterMetadata(
                kParameter = param,
                type = ParameterType.REQUEST_PARAM,
                name = name,
                required = annotation.required,
                defaultValue = annotation.defaultValue.ifEmpty { null }
            )
        }
        
        param.findAnnotation<RequestBody>()?.let {
            // RequestBody는 이름이 필수가 아님 (본문 전체를 사용)
            return ParameterMetadata(
                kParameter = param,
                type = ParameterType.REQUEST_BODY,
                name = param.name ?: "body"
            )
        }
        
        param.findAnnotation<Header>()?.let { annotation ->
            validateNonEmptyName(
                name = annotation.value,
                param = param,
                annotationType = "Header",
                isAnnotationValue = true
            )
            return ParameterMetadata(
                kParameter = param,
                type = ParameterType.HEADER,
                name = annotation.value
            )
        }
        
        param.findAnnotation<HeaderMap>()?.let {
            // HeaderMap은 이름이 필수가 아님 (Map 전체를 사용)
            return ParameterMetadata(
                kParameter = param,
                type = ParameterType.HEADER_MAP,
                name = param.name ?: "headers"
            )
        }
        
        return null
    }
    
    /**
     * 어노테이션 값 또는 파라미터 이름에서 파라미터 이름을 결정
     * 
     * @param annotationValue 어노테이션에 지정된 값
     * @param param Kotlin 파라미터
     * @param annotationType 어노테이션 타입명 (에러 메시지용)
     * @return 결정된 파라미터 이름
     * @throws IllegalStateException 파라미터 이름을 결정할 수 없는 경우
     */
    private fun resolveParameterName(
        annotationValue: String,
        param: KParameter,
        annotationType: String
    ): String {
        // 어노테이션에 명시적 값이 있으면 사용
        if (annotationValue.isNotEmpty()) {
            return annotationValue
        }
        
        // 어노테이션 값이 없으면 파라미터 이름 사용 시도
        val paramName = param.name
        
        // 파라미터 이름이 null이거나 비어있으면 에러
        validateNonEmptyName(
            name = paramName,
            param = param,
            annotationType = annotationType,
            isAnnotationValue = false
        )
        
        return paramName!!
    }
    
    /**
     * 파라미터 이름이 비어있지 않은지 검증
     * 
     * @param name 검증할 이름
     * @param param Kotlin 파라미터
     * @param annotationType 어노테이션 타입명
     * @param isAnnotationValue 어노테이션 값인지 여부
     * @throws IllegalStateException 이름이 비어있는 경우
     */
    private fun validateNonEmptyName(
        name: String?,
        param: KParameter,
        annotationType: String,
        isAnnotationValue: Boolean
    ) {
        if (name.isNullOrEmpty()) {
            val paramIndex = param.index
            val paramType = param.type
            
            val message = if (isAnnotationValue) {
                buildString {
                    appendLine("@$annotationType 어노테이션에 값이 지정되지 않았습니다.")
                    appendLine("  - 파라미터 인덱스: $paramIndex")
                    appendLine("  - 파라미터 타입: $paramType")
                    appendLine()
                    appendLine("해결 방법:")
                    appendLine("  @$annotationType(\"parameterName\") 형태로 명시적으로 이름을 지정하세요.")
                }
            } else {
                buildString {
                    appendLine("@$annotationType 파라미터의 이름을 결정할 수 없습니다.")
                    appendLine("  - 파라미터 인덱스: $paramIndex")
                    appendLine("  - 파라미터 타입: $paramType")
                    appendLine()
                    appendLine("원인:")
                    appendLine("  어노테이션에 명시적 값이 지정되지 않았고, 컴파일 시 파라미터 이름이 보존되지 않았습니다.")
                    appendLine()
                    appendLine("해결 방법 (둘 중 하나 선택):")
                    appendLine("  1. 어노테이션에 명시적으로 이름 지정: @$annotationType(\"parameterName\")")
                    appendLine("  2. 프로젝트를 -java-parameters 옵션으로 컴파일")
                    appendLine("     Gradle 설정 예시:")
                    appendLine("       tasks.withType<JavaCompile> {")
                    appendLine("           options.compilerArgs.add(\"-parameters\")")
                    appendLine("       }")
                }
            }
            
            throw IllegalStateException(message)
        }
    }
}

