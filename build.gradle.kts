plugins {
    id("java")
}

group = "org.traffichunter.titan"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {

}

subprojects {
    apply(plugin = "java")

    dependencies {
        testImplementation(platform("org.junit:junit-bom:5.10.0"))
        testImplementation("org.junit.jupiter:junit-jupiter")
        testImplementation("org.mockito:mockito-core:5.21.0")

        compileOnly("org.projectlombok:lombok:1.18.38")
        annotationProcessor("org.projectlombok:lombok:1.18.38")

        implementation("org.slf4j:slf4j-api:2.0.17")
        implementation("com.google.guava:guava:33.4.8-jre")
    }
}

tasks.test {
    useJUnitPlatform()
}