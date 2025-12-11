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
    // Spring Boot (boot plugin 없이 의존성만)
    implementation("org.springframework.boot:spring-boot-starter")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    // Selenium WebDriver for crawling
    implementation("org.seleniumhq.selenium:selenium-java:4.27.0")
    // WebDriverManager for automatic driver management
    implementation("io.github.bonigarcia:webdrivermanager:5.9.2")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

