plugins {
    `java-library`
}

dependencies {
    api(project(":titan-stomp"))
    implementation(project(":core"))

    implementation(project.libs.spring.boot.autoconfigure)
    implementation(project.libs.spring.context)
    implementation(project.libs.spring.beans)
    implementation(project.libs.spring.messaging)
    implementation(project.libs.jackson.databind)

    testImplementation(project.libs.spring.boot.test)

    annotationProcessor(project.libs.spring.boot.configure.processor)
}
