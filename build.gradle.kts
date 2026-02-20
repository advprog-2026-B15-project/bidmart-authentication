plugins {
    java
    id("org.springframework.boot") version "4.0.3"
    id("io.spring.dependency-management") version "1.1.7"
    checkstyle
    jacoco
    id("org.sonarqube") version "4.4.1.3373"
}

group = "id.ac.ui.cs.advprog.bidmart"
version = "0.0.1-SNAPSHOT"
description = "bidmart-authentication"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
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
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.thymeleaf.extras:thymeleaf-extras-springsecurity6")
    compileOnly("org.projectlombok:lombok")
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    runtimeOnly("org.postgresql:postgresql")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    annotationProcessor("org.projectlombok:lombok")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testRuntimeOnly("com.h2database:h2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

checkstyle {
    toolVersion = "10.21.0"
    configFile = file("config/checkstyle/checkstyle.xml")
}

jacoco {
    toolVersion = "0.8.12"
}

tasks.withType<Test> {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
    }
}

sonarqube {
    properties {
        property("sonar.projectKey", System.getenv("SONAR_PROJECT_KEY") ?: "YOUR_PROJECT_KEY")
        property("sonar.organization", System.getenv("SONAR_ORGANIZATION") ?: "YOUR_ORGANIZATION")
        property("sonar.host.url", "https://sonarcloud.io")
        property("sonar.coverage.jacoco.xmlReportPaths", "build/reports/jacoco/test/jacocoTestReport.xml")
    }
}
