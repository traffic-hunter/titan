plugins {
    id("java")
}

group = "org.traffichunter.titan.bootstrap"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {

    implementation("org.yaml:snakeyaml:2.3")
}

tasks.test {
    useJUnitPlatform()
}