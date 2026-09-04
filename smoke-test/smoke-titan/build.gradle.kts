dependencies {
    testImplementation(project(":titan-client"))
}

val titanServerJar = project(":bootstrap").layout.buildDirectory
    .file("libs/titan-server-${project.version}.jar")

tasks.named("jar") {
    enabled = false
}

tasks.test {
    dependsOn(":bootstrap:shadowJar")

    systemProperty("titan.smoke.jar", titanServerJar.get().asFile.absolutePath)
    forkEvery = 1
    systemProperty("junit.jupiter.execution.timeout.threaddump.enabled", "true")
}
