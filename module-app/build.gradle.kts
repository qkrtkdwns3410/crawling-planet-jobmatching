plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    // 크롤러 모듈 의존
    implementation(project(":module-crawler"))

    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter")
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

