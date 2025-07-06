plugins {
    id("java")
}

group = "org.traffichunter.titan.core"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":core"))
}

tasks.test {
    useJUnitPlatform()
}