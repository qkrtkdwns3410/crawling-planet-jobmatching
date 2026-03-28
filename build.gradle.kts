plugins {
    kotlin("jvm") version "2.0.21" apply false
    kotlin("plugin.spring") version "2.0.21" apply false
    id("org.springframework.boot") version "3.4.1" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}

allprojects {
    group = "com.crawling.planet"
    version = "0.0.1"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.kotlin.plugin.spring")
    apply(plugin = "io.spring.dependency-management")

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions {
            freeCompilerArgs.addAll("-Xjsr305=strict")
            // 런타임에 파라미터 이름 접근을 위해 필요 (module-http-client에서 사용)
            javaParameters.set(true)
        }
    }
    
    tasks.withType<JavaCompile> {
        // Java 코드에서도 파라미터 이름 보존
        options.compilerArgs.add("-parameters")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        systemProperty("DOCKER_HOST", "tcp://localhost:2375")
        jvmArgs("-Dorg.testcontainers.docker.host=tcp://localhost:2375")
    }

    the<JavaPluginExtension>().apply {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    dependencies {
        // 공통 의존성
        "implementation"("org.jetbrains.kotlin:kotlin-reflect")
        "implementation"("io.github.oshai:kotlin-logging-jvm:7.0.0")

        "testImplementation"("org.jetbrains.kotlin:kotlin-test-junit5")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")

        // Testcontainers (PostgreSQL E2E 테스트용)
        "testImplementation"("org.testcontainers:postgresql:1.20.4")
        "testImplementation"("org.testcontainers:junit-jupiter:1.20.4")

        // WireMock (외부 API 모킹)
        "testImplementation"("org.wiremock:wiremock-standalone:3.10.0")

        // Spring Boot Testcontainers 통합 (@ServiceConnection 지원)
        "testImplementation"("org.springframework.boot:spring-boot-testcontainers")
    }
}
