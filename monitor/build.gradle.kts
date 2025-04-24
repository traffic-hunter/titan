plugins {
    id("java")
}

group = "org.traffichunter.titan.monitor"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {

    implementation(project(":bootstrap"))
}

tasks.test {
    useJUnitPlatform()
}