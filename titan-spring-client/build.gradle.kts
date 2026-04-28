plugins {
    `java-library`
}

dependencies {
    api(project(":titan-stomp"))
    implementation(project(":core"))

    implementation("org.springframework.boot:spring-boot-autoconfigure:3.3.5")
    implementation("org.springframework:spring-context:7.0.7")
    implementation("org.springframework:spring-beans:7.0.7")
    implementation("org.springframework:spring-messaging:7.0.7")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")

    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor:3.3.5")
}
