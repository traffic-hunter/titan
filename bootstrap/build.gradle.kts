plugins {
    id("java")
}

group = "org.traffichunter.titan.bootstrap"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
}

tasks.test {
    useJUnitPlatform()
}