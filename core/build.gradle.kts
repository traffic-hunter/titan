plugins {
    id("java")
}

group = "org.traffichunter.titan.core"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

val jettyVersion = "12.0.19"

dependencies {

    implementation(project(":bootstrap"))
    implementation(project(":monitor"))
    implementation(project(":recovery"))
    implementation(project(":service-discovery"))

    // embedded jetty
    implementation("org.eclipse.jetty:jetty-server:${jettyVersion}")
    implementation("org.eclipse.jetty.ee10:jetty-ee10-servlet:${jettyVersion}")
    implementation("org.eclipse.jetty:jetty-http:${jettyVersion}")

    // objectmapper
    implementation("com.fasterxml.jackson.core:jackson-databind:2.19.0")
}

tasks.test {
    useJUnitPlatform()
}