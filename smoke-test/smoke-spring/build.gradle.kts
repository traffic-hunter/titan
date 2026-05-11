plugins {
    java
    id("org.springframework.boot") version "4.0.6"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "org.traffichunter.titan"
version = "0.0.1-SNAPSHOT"
description = "smoke-spring"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":titan-spring-client"))

    implementation("org.springframework.boot:spring-boot-starter")
    testImplementation("org.springframework.boot:spring-boot-starter-test")

    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.named("bootJar") {
    enabled = false
}

configurations.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "io.netty") {
            useVersion("4.2.2.Final")
            because("Titan core stack uses Netty 4.2.2 and mixed Netty versions cause runtime linkage errors in smoke tests")
        }
    }
}
