plugins {
    java
    checkstyle
    id("org.springframework.boot") version "3.3.5"
    id("io.spring.dependency-management") version "1.1.6"
}

checkstyle {
    toolVersion = "10.12.5"
    // Use the config/checkstyle/checkstyle.xml config file
    configFile = file("${project.projectDir}/config/checkstyle/checkstyle.xml")
    isIgnoreFailures = false // Fail build if there are checkstyle violations
}

group = "de.ude"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot Core Web & JPA
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Database Migration (Flyway) & PostgreSQL Driver
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    testImplementation("org.junit.jupiter:junit-jupiter:5.14.0")
    runtimeOnly("org.postgresql:postgresql")

    // JavaParser (for AST static analysis)
    implementation("com.github.javaparser:javaparser-core:3.26.2")

    // JGit (for Git-URL import, US3.3)
    implementation("org.eclipse.jgit:org.eclipse.jgit:7.7.0.202606012155-r")

    // Lombok (Annotation processing for boilerplate reduction)
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
