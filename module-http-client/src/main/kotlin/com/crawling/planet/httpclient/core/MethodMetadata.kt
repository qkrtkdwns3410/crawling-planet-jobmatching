package com.crawling.planet.httpclient.core

import org.springframework.http.HttpMethod
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.KType

/**
 * 메서드 메타데이터
 * 리플렉션 비용을 줄이기 위해 파싱 결과를 캐시
 */
data class MethodMetadata(
    val function: KFunction<*>,
    val httpMethod: HttpMethod,
    val path: String,
    val staticHeaders: Map<String, String>,
    val returnType: KType,
    val isSuspend: Boolean,
    val parameters: List<ParameterMetadata>
)

/**
 * 파라미터 메타데이터
 */
data class ParameterMetadata(
    val kParameter: KParameter,
    val type: ParameterType,
    val name: String,
    val required: Boolean = true,
    val defaultValue: String? = null
)

/**
 * 파라미터 타입
 */
enum class ParameterType {
    PATH_VARIABLE,
    REQUEST_PARAM,
    REQUEST_BODY,
    HEADER,
    HEADER_MAP
}

