plugins {
    id("org.springframework.boot") apply false
    id("io.spring.dependency-management")
    kotlin("plugin.jpa") version "2.0.21"
}

dependencyManagement {
    imports {
        mavenBom(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES)
    }
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

dependencies {
    // Spring Data JPA
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    
    // PostgreSQL Driver
    runtimeOnly("org.postgresql:postgresql")
    
    // Validation
    implementation("org.springframework.boot:spring-boot-starter-validation")
    
    // Jackson for JSON
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}



