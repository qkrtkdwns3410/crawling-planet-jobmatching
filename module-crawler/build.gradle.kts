plugins {
    id("org.springframework.boot") apply false
    id("io.spring.dependency-management")
}

dependencyManagement {
    imports {
        mavenBom(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES)
    }
}

dependencies {
    // Domain 모듈 의존
    api(project(":module-domain"))
    
    // HTTP Client 모듈 (WebClient 선언적 인터페이스)
    api(project(":module-http-client"))
    
    // Spring Boot (boot plugin 없이 의존성만)
    implementation("org.springframework.boot:spring-boot-starter")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    // Spring Data JPA (트랜잭션 사용)
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    
    // Reactor Extra for retry
    implementation("io.projectreactor.addons:reactor-extra:3.5.2")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.projectreactor:reactor-test")
}
