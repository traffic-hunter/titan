plugins {
    id("java")
}

group = "org.traffichunter.titan.transport.stomp"
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