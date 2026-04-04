<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-01 | Updated: 2026-04-05 -->

# module-http-client

## Purpose
Spring WebClient를 인터페이스 기반으로 선언적으로 사용할 수 있는 커스텀 프레임워크. Feign과 유사하지만 WebFlux(Reactive) 기반. `@WebClientInterface` 어노테이션 선언만으로 프록시를 자동 생성한다.

## Key Files

| File | Description |
|------|-------------|
| `annotation/WebClientInterface.kt` | 인터페이스를 HTTP 클라이언트로 선언하는 어노테이션 |
| `annotation/EnableWebClients.kt` | Spring 설정에서 WebClient 스캔을 활성화하는 어노테이션 |
| `annotation/HttpMethod.kt` | `@Get`, `@Post` 등 HTTP 메서드 어노테이션 |
| `annotation/Parameters.kt` | 파라미터 바인딩 어노테이션 |
| `config/WebClientAutoConfiguration.kt` | Spring Boot 자동 설정 |
| `config/WebClientInterfaceRegistrar.kt` | `@EnableWebClients` 스캔 후 BeanDefinition 등록 |
| `config/WebClientInterfaceFactoryBean.kt` | 런타임 프록시 생성 FactoryBean |
| `core/WebClientProxyFactory.kt` | 동적 프록시(JDK Proxy) 생성 |
| `core/WebClientInvocationHandler.kt` | 메서드 호출을 WebClient 요청으로 변환 |
| `core/MethodMetadataParser.kt` | 어노테이션에서 URL/파라미터 메타데이터 파싱 |

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `src/main/kotlin/.../annotation/` | 커스텀 어노테이션 정의 |
| `src/main/kotlin/.../config/` | Spring Boot 자동 설정 및 Bean 등록 |
| `src/main/kotlin/.../core/` | 프록시 생성 및 호출 처리 핵심 로직 |
| `src/main/kotlin/.../exception/` | 프레임워크 예외 클래스 |
| `src/test/kotlin/` | MethodMetadataParser 단위 테스트 |

## For AI Agents

### Usage Pattern
```kotlin
// 1. 인터페이스 선언
@WebClientInterface
interface JobplanetReviewClient {
    @Get("/api/v4/companies/reviews/list")
    fun getReviews(@Parameters params: Map<String, Any>): Mono<JobplanetApiResponse>
}

// 2. 설정에서 활성화
@EnableWebClients(basePackages = ["com.crawling.planet.crawler.client"])
@Configuration class CrawlerConfig

// 3. 주입해서 사용
@Service class MyService(private val client: JobplanetReviewClient)
```

### Working In This Directory
- 이 모듈 자체는 비즈니스 로직 없음 — 프레임워크 코드만 존재
- 실제 WebClient Bean(`jobplanetWebClient`)은 `module-crawler`의 `WebClientConfig`에서 생성
- Kotlin Reflect로 런타임 파라미터 이름을 읽기 때문에 컴파일 시 `-parameters` 옵션 필요
- `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`로 자동 설정 등록됨

### Testing Requirements
- `MethodMetadataParserTest`: 어노테이션 파싱 단위 테스트
- 통합 테스트: WireMock으로 실제 HTTP 프록시 동작 검증

## Dependencies

### Internal
- 없음 (독립 프레임워크 모듈)

### External
- Spring WebFlux (WebClient)
- Kotlin Reflect
- Kotlin Coroutines (선택적)

<!-- MANUAL: -->
