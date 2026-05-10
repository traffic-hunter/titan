plugins {
    id("com.gradleup.shadow") version "9.4.1"
}

group = "org.traffichunter.titan.bootstrap"
version = "1.0-SNAPSHOT"

dependencies {
    implementation(project.libs.snakeyaml)

    runtimeOnly(project(":core"))
    runtimeOnly(project(":titan-stomp"))
    runtimeOnly(project(":fanout"))
}

val manifestPath = "src/main/resources/META-INF/MANIFEST.MF"

tasks.jar {
    manifest.from(manifestPath)
}

tasks.shadowJar {
    archiveBaseName.set("titan-server")
    archiveClassifier.set("")

    manifest.from(manifestPath)
}
