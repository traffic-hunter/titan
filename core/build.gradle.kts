plugins {
    id("java")
}

group = "org.traffichunter.titan.core"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

val jettyVersion = "12.0.19"
val jacksonVersion = "2.19.0"
val apacheCommonsPool = "2.12.1"
val slf4jVersion = "2.0.17"
val logbackVersion = "1.5.18"

dependencies {

    implementation(project(":bootstrap"))
    implementation(project(":monitor"))
    implementation(project(":recovery"))

    // embedded jetty
    implementation("org.eclipse.jetty:jetty-server:${jettyVersion}")
    implementation("org.eclipse.jetty.ee10:jetty-ee10-servlet:${jettyVersion}")
    implementation("org.eclipse.jetty:jetty-http:${jettyVersion}")

    // objectmapper
    implementation("com.fasterxml.jackson.core:jackson-databind:${jacksonVersion}")

    // apache commons pool2
    implementation("org.apache.commons:commons-pool2:${apacheCommonsPool}")

    // netty buffer
    implementation("io.netty:netty-buffer:4.2.2.Final")

    // SLF4J
    implementation("org.slf4j:slf4j-api:${slf4jVersion}")
    implementation("ch.qos.logback:logback-classic:${logbackVersion}")

    // assertJ
    testImplementation("org.assertj:assertj-core:3.27.3")

    // awaitility
    testImplementation("org.awaitility:awaitility:4.3.0")
}

tasks.test {
    useJUnitPlatform()
}