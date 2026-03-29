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
        testImplementation(platform("org.junit:junit-bom:5.10.0"))
        testImplementation("org.junit.jupiter:junit-jupiter")
        testImplementation(rootProject.libs.mokito)

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
