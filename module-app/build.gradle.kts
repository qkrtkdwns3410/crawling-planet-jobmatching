plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    // 도메인 모듈 의존
    implementation(project(":module-domain"))
    // 크롤러 모듈 의존
    implementation(project(":module-crawler"))

    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter")
    
    // Spring Web (REST API)
    implementation("org.springframework.boot:spring-boot-starter-web")
    
    // Spring WebFlux (Reactive 지원)
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    
    // Spring Data JPA (트랜잭션 관리)
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    
    // PostgreSQL Driver
    runtimeOnly("org.postgresql:postgresql")
    
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

