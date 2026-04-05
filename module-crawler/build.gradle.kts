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

    // OkHttp (Cloudflare TLS 핑거프린팅 우회)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Selenium (로그인 자동화)
    implementation("org.seleniumhq.selenium:selenium-java:4.27.0")
    implementation("io.github.bonigarcia:webdrivermanager:5.9.2")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("org.wiremock:wiremock-standalone:3.4.2")
    testImplementation("com.fasterxml.jackson.module:jackson-module-kotlin")
}
