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
    implementation(project(":service-discovery"))
}

tasks.test {
    useJUnitPlatform()
}