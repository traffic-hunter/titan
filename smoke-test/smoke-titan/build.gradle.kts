dependencies {
    testImplementation(project(":core"))
    testImplementation(project(":titan-stomp"))
    testImplementation(project(":titan-client"))
    testImplementation(project(":fanout"))
}

tasks.named("jar") {
    enabled = false
}
