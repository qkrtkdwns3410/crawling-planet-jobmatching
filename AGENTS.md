# Global Analysis Rules

## Long-Term Memory
- Use `C:\Users\ipeac\IdeaProjects\obsidian` as the long-term memory source for this repository.
- Before answering architecture, analysis, design, debugging, or project-history questions, search the relevant notes under `C:\Users\ipeac\IdeaProjects\obsidian\claude-memory`.
- Prefer these notes first when building historical or operational context:
  - `C:\Users\ipeac\IdeaProjects\obsidian\claude-memory\crawling-planet-project.md`
  - `C:\Users\ipeac\IdeaProjects\obsidian\claude-memory\aws-infra.md`
  - `C:\Users\ipeac\IdeaProjects\obsidian\claude-memory\dev-environment.md`
- Treat repository source code and configuration in the current workspace as the source of truth for actual runtime behavior. Treat Obsidian notes as supporting context for history, architecture intent, infrastructure, and working conventions.
- If Obsidian notes conflict with current source code, call out the mismatch explicitly and prefer the current repository state.
- Do not read or expose `C:\Users\ipeac\IdeaProjects\obsidian\claude-memory\secrets.md` unless the task is explicitly about secrets or infrastructure credentials.

## Source Analysis Defaults
- When the user asks to analyze project source code, explain application structure, or describe call flow, inspect the relevant files before answering.
- Base explanations on actual code paths, classes, methods, and configuration found in the workspace.
- Prefer flow-oriented explanations over long code dumps so the user can understand the system without reading the source first.
- Include at least one Mermaid diagram when the request is about control flow, request flow, class relationships, module dependencies, or runtime sequence.
- Choose the Mermaid format that best matches the question: `flowchart`, `sequenceDiagram`, `classDiagram`, or `erDiagram`.
- When the flow is large, split the explanation into a short overview and one or more smaller Mermaid diagrams instead of one oversized diagram.
- Cite the concrete files and symbols that support the explanation.
- If part of the flow is inferred rather than directly confirmed from code or notes, label it clearly as an inference.

## Response Shape For Code Reading Requests
- Start with the entry point or triggering component.
- Summarize the main path through layers such as controller, service, repository, domain, and external systems when applicable.
- Highlight branch points, transactional boundaries, and important side effects when they materially affect behavior.
- Keep the explanation concise, but detailed enough that the user can understand the end-to-end flow without opening the code immediately.

---
<!-- Generated: 2026-04-01 | Updated: 2026-04-05 -->

# crawling-planet-jobmatching

## Purpose
잡플래닛(Jobplanet) 회사 리뷰를 크롤링하여 PostgreSQL에 저장하고, 잡코리아(JobKorea) 채용 공고 페이지에서 잡플래닛 리뷰를 오버레이로 보여주는 시스템. Spring Boot + Kotlin 기반의 모듈식 아키텍처.

## Key Files

| File | Description |
|------|-------------|
| `build.gradle.kts` | 루트 빌드 설정 (공통 플러그인, 멀티 모듈) |
| `settings.gradle.kts` | 서브모듈 목록 정의 |
| `docker-compose.yml` | 로컬 개발용 PostgreSQL 16 + pgAdmin 컨테이너 |
| `init-db.sql` | DB 초기화 스크립트 (pg_trgm 확장, 인덱스, 뷰) |
| `IMPROVEMENTS.md` | 기술적 개선사항 기록 (문제→원인→해결→결과 구조) |
| `README.md` | 프로젝트 개요 및 실행 방법 |
| `RELEASE.md` | 버전별 릴리스 내역 |

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `module-domain/` | JPA 엔티티, Repository, DTO (see `module-domain/AGENTS.md`) |
| `module-http-client/` | 선언적 WebClient 프레임워크 (see `module-http-client/AGENTS.md`) |
| `module-crawler/` | 잡플래닛 크롤링 핵심 로직 (see `module-crawler/AGENTS.md`) |
| `module-app/` | 크롤링 관리 REST API 서버 포트 8080 (see `module-app/AGENTS.md`) |
| `module-api/` | Chrome Extension용 검색 API 서버 포트 8081 (see `module-api/AGENTS.md`) |
| `module-chrome-ext/` | 잡코리아 페이지에 리뷰 오버레이하는 Chrome Extension (see `module-chrome-ext/AGENTS.md`) |
| `infra/` | Terraform 기반 AWS 인프라 코드 (see `infra/AGENTS.md`) |
| `private/` | 민감 정보 및 HTTP 요청 파일 (git 서브모듈, 커밋 제외) |

## Module Dependency Graph

```
module-app ──────────┐
                     ├──→ module-crawler ──→ module-domain
module-api ──────────┘         └──────────→ module-http-client
```

## For AI Agents

### Working In This Repository
- **문서 업데이트 필수**: 코드 변경 시 `IMPROVEMENTS.md`, `README.md`, `RELEASE.md`를 반드시 함께 수정
- **빌드**: `./gradlew build` (전체), `./gradlew :module-app:bootRun` (앱 실행)
- **DB 기동**: `docker-compose up -d`
- **테스트**: `./gradlew test` 또는 `./gradlew :<module>:test`
- **브랜치 전략**: `master`(프로덕션), `feature/*`(기능 개발) → PR → master

### Architecture Decisions
- **비동기**: Reactor Flux/Mono + `.subscribe()`로 HTTP 타임아웃 없는 백그라운드 실행
- **인증**: Selenium headless Chrome으로 잡플래닛 로그인 → 전체 쿠키 획득 → WebClient 헤더 주입
- **유사도 검색**: PostgreSQL `pg_trgm` 확장으로 회사명 퍼지 매칭
- **동시성**: concurrency=200, delay=10ms (rate limit 안전 범위 확인 완료)

### Common Crawling Pattern
```kotlin
service.crawlRange(startId, endId)
    .subscribe(
        { result -> logger.info { "완료: $result" } },
        { error -> logger.error(error) { "실패" } }
    )
return ResponseEntity.ok(mapOf("status" to "started"))
```

## Dependencies

### External
- Kotlin 2.0.21 / Java 21
- Spring Boot 3.4.1 (Web + WebFlux + Data JPA)
- PostgreSQL 16 + pg_trgm
- Selenium WebDriver + WebDriverManager
- Reactor + reactor-extra
- kotlin-logging (oshai)

<!-- MANUAL: -->
