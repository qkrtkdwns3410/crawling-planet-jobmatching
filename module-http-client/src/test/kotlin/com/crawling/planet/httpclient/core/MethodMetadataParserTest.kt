package com.crawling.planet.httpclient.core

import com.crawling.planet.httpclient.annotation.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.reflect.KFunction
import kotlin.reflect.full.functions

/**
 * MethodMetadataParser 단위 테스트
 */
class MethodMetadataParserTest {
    
    @Nested
    @DisplayName("파라미터 이름 검증 테스트")
    inner class ParameterNameValidationTest {
        
        @Test
        @DisplayName("어노테이션에 명시적 이름이 있으면 해당 이름을 사용한다")
        fun `should use explicit annotation value when provided`() {
            // given
            val metadata = MethodMetadataParser.parse(ExplicitNameInterface::class)
            
            // when
            val method = metadata.entries.first()
            val pathParam = method.value.parameters.find { it.type == ParameterType.PATH_VARIABLE }
            val queryParam = method.value.parameters.find { it.type == ParameterType.REQUEST_PARAM }
            
            // then
            assertNotNull(pathParam)
            assertEquals("userId", pathParam!!.name)
            
            assertNotNull(queryParam)
            assertEquals("searchQuery", queryParam!!.name)
        }
        
        @Test
        @DisplayName("어노테이션 값이 비어있으면 파라미터 이름을 사용한다 (-java-parameters 활성화 시)")
        fun `should use parameter name when annotation value is empty`() {
            // given - 이 테스트는 -java-parameters가 활성화된 상태에서 실행됨
            val metadata = MethodMetadataParser.parse(ImplicitNameInterface::class)
            
            // when
            val method = metadata.entries.first()
            val pathParam = method.value.parameters.find { it.type == ParameterType.PATH_VARIABLE }
            
            // then
            // -java-parameters가 활성화되어 있으므로 파라미터 이름이 보존됨
            assertNotNull(pathParam)
            assertTrue(pathParam!!.name.isNotEmpty(), "파라미터 이름이 비어있지 않아야 합니다")
        }
        
        @Test
        @DisplayName("Header 어노테이션에 값이 있으면 정상 처리된다")
        fun `should handle Header annotation with explicit value`() {
            // given
            val metadata = MethodMetadataParser.parse(HeaderInterface::class)
            
            // when
            val method = metadata.entries.first()
            val headerParam = method.value.parameters.find { it.type == ParameterType.HEADER }
            
            // then
            assertNotNull(headerParam)
            assertEquals("Authorization", headerParam!!.name)
        }
        
        @Test
        @DisplayName("RequestBody는 이름이 없어도 정상 처리된다")
        fun `should handle RequestBody without explicit name`() {
            // given
            val metadata = MethodMetadataParser.parse(RequestBodyInterface::class)
            
            // when
            val method = metadata.entries.first()
            val bodyParam = method.value.parameters.find { it.type == ParameterType.REQUEST_BODY }
            
            // then
            assertNotNull(bodyParam)
            assertTrue(bodyParam!!.name.isNotEmpty())
        }
        
        @Test
        @DisplayName("HeaderMap은 이름이 없어도 정상 처리된다")
        fun `should handle HeaderMap without explicit name`() {
            // given
            val metadata = MethodMetadataParser.parse(HeaderMapInterface::class)
            
            // when
            val method = metadata.entries.first()
            val headerMapParam = method.value.parameters.find { it.type == ParameterType.HEADER_MAP }
            
            // then
            assertNotNull(headerMapParam)
            assertTrue(headerMapParam!!.name.isNotEmpty())
        }
    }
    
    @Nested
    @DisplayName("HTTP 메서드 파싱 테스트")
    inner class HttpMethodParsingTest {
        
        @Test
        @DisplayName("GET 메서드가 올바르게 파싱된다")
        fun `should parse GET method correctly`() {
            // given
            val metadata = MethodMetadataParser.parse(AllHttpMethodsInterface::class)
            
            // when
            val getMethod = metadata.entries.find { it.key.name == "getMethod" }
            
            // then
            assertNotNull(getMethod)
            assertEquals(org.springframework.http.HttpMethod.GET, getMethod!!.value.httpMethod)
            assertEquals("/get", getMethod.value.path)
        }
        
        @Test
        @DisplayName("POST 메서드가 올바르게 파싱된다")
        fun `should parse POST method correctly`() {
            // given
            val metadata = MethodMetadataParser.parse(AllHttpMethodsInterface::class)
            
            // when
            val postMethod = metadata.entries.find { it.key.name == "postMethod" }
            
            // then
            assertNotNull(postMethod)
            assertEquals(org.springframework.http.HttpMethod.POST, postMethod!!.value.httpMethod)
            assertEquals("/post", postMethod.value.path)
        }
        
        @Test
        @DisplayName("PUT 메서드가 올바르게 파싱된다")
        fun `should parse PUT method correctly`() {
            // given
            val metadata = MethodMetadataParser.parse(AllHttpMethodsInterface::class)
            
            // when
            val putMethod = metadata.entries.find { it.key.name == "putMethod" }
            
            // then
            assertNotNull(putMethod)
            assertEquals(org.springframework.http.HttpMethod.PUT, putMethod!!.value.httpMethod)
        }
        
        @Test
        @DisplayName("DELETE 메서드가 올바르게 파싱된다")
        fun `should parse DELETE method correctly`() {
            // given
            val metadata = MethodMetadataParser.parse(AllHttpMethodsInterface::class)
            
            // when
            val deleteMethod = metadata.entries.find { it.key.name == "deleteMethod" }
            
            // then
            assertNotNull(deleteMethod)
            assertEquals(org.springframework.http.HttpMethod.DELETE, deleteMethod!!.value.httpMethod)
        }
        
        @Test
        @DisplayName("PATCH 메서드가 올바르게 파싱된다")
        fun `should parse PATCH method correctly`() {
            // given
            val metadata = MethodMetadataParser.parse(AllHttpMethodsInterface::class)
            
            // when
            val patchMethod = metadata.entries.find { it.key.name == "patchMethod" }
            
            // then
            assertNotNull(patchMethod)
            assertEquals(org.springframework.http.HttpMethod.PATCH, patchMethod!!.value.httpMethod)
        }
    }
    
    @Nested
    @DisplayName("정적 헤더 파싱 테스트")
    inner class StaticHeaderParsingTest {
        
        @Test
        @DisplayName("정적 헤더가 올바르게 파싱된다")
        fun `should parse static headers correctly`() {
            // given
            val metadata = MethodMetadataParser.parse(StaticHeaderInterface::class)
            
            // when
            val method = metadata.entries.first()
            
            // then
            assertEquals(2, method.value.staticHeaders.size)
            assertEquals("application/json", method.value.staticHeaders["Content-Type"])
            assertEquals("gzip", method.value.staticHeaders["Accept-Encoding"])
        }
    }
    
    @Nested
    @DisplayName("RequestParam 속성 테스트")
    inner class RequestParamAttributesTest {
        
        @Test
        @DisplayName("RequestParam의 required와 defaultValue가 올바르게 파싱된다")
        fun `should parse RequestParam attributes correctly`() {
            // given
            val metadata = MethodMetadataParser.parse(RequestParamAttributesInterface::class)
            
            // when
            val method = metadata.entries.first()
            val requiredParam = method.value.parameters.find { it.name == "required" }
            val optionalParam = method.value.parameters.find { it.name == "optional" }
            val defaultParam = method.value.parameters.find { it.name == "withDefault" }
            
            // then
            assertNotNull(requiredParam)
            assertTrue(requiredParam!!.required)
            assertNull(requiredParam.defaultValue)
            
            assertNotNull(optionalParam)
            assertFalse(optionalParam!!.required)
            
            assertNotNull(defaultParam)
            assertEquals("defaultValue", defaultParam!!.defaultValue)
        }
    }
    
    // ======== 테스트용 인터페이스 정의 ========
    
    interface ExplicitNameInterface {
        @Get("/users/{userId}")
        suspend fun getUser(
            @PathVariable("userId") id: Long,
            @RequestParam("searchQuery") query: String
        ): String
    }
    
    interface ImplicitNameInterface {
        @Get("/users/{id}")
        suspend fun getUser(@PathVariable id: Long): String
    }
    
    interface HeaderInterface {
        @Get("/protected")
        suspend fun getProtected(@Header("Authorization") token: String): String
    }
    
    interface RequestBodyInterface {
        @Post("/users")
        suspend fun createUser(@RequestBody body: Map<String, Any>): String
    }
    
    interface HeaderMapInterface {
        @Get("/api")
        suspend fun callApi(@HeaderMap headers: Map<String, String>): String
    }
    
    interface AllHttpMethodsInterface {
        @Get("/get")
        suspend fun getMethod(): String
        
        @Post("/post")
        suspend fun postMethod(): String
        
        @Put("/put")
        suspend fun putMethod(): String
        
        @Delete("/delete")
        suspend fun deleteMethod(): String
        
        @Patch("/patch")
        suspend fun patchMethod(): String
    }
    
    interface StaticHeaderInterface {
        @Get("/api", headers = ["Content-Type: application/json", "Accept-Encoding: gzip"])
        suspend fun callWithHeaders(): String
    }
    
    interface RequestParamAttributesInterface {
        @Get("/search")
        suspend fun search(
            @RequestParam("required", required = true) required: String,
            @RequestParam("optional", required = false) optional: String?,
            @RequestParam("withDefault", defaultValue = "defaultValue") withDefault: String
        ): String
    }
}

