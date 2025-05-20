plugins {
    id("java")
}

group = "org.traffichunter.titan.recovery"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
}

tasks.test {
    useJUnitPlatform()
}