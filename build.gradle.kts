plugins {
    id("java")
}

group = "org.traffichunter.titan"
version = "1.0-SNAPSHOT"

subprojects {
    apply(plugin = "java")

    repositories {
        mavenCentral()
    }

    dependencies {
        testImplementation(platform("org.junit:junit-bom:6.0.3"))
        testImplementation("org.junit.jupiter:junit-jupiter")
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")
        testImplementation(rootProject.libs.mokito)
        testImplementation("org.mockito:mockito-junit-jupiter:5.21.0")

        compileOnly(rootProject.libs.lombok)
        annotationProcessor(rootProject.libs.lombok)

        implementation(rootProject.libs.slf4j)
        implementation(rootProject.libs.google.guava)

        // netty buffer
        implementation(rootProject.libs.netty.buffer)

        // IntelliJ nullability contracts
        implementation(rootProject.libs.jetbrains.annotations)

        // assertJ
        testImplementation(rootProject.libs.assertj)
        testImplementation(rootProject.libs.awaitility)
    }

    tasks.test {
        useJUnitPlatform()
    }
}
