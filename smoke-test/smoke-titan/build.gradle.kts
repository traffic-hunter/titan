dependencies {
    testImplementation(project(":core"))
    testImplementation(project(":titan-stomp"))
    testImplementation(project(":titan-client"))
    testImplementation(project(":dispatch"))
}

tasks.named("jar") {
    enabled = false
}
