plugins {
    `java-library`
}

dependencies {
    api(project(":titan-stomp"))
    implementation(project(":core"))

    implementation("org.springframework.boot:spring-boot-autoconfigure:3.3.5")
    implementation("org.springframework:spring-context:6.1.14")
    implementation("org.springframework:spring-beans:6.1.14")
    implementation("org.springframework:spring-messaging:6.1.14")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")

    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor:3.3.5")
}
