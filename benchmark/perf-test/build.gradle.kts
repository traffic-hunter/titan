plugins {
    application
    id("com.gradleup.shadow")
}

dependencies {
    implementation(project(":titan-client"))

    testImplementation(project(":benchmark:stability-fixture"))
}

application {
    mainClass.set("org.traffichunter.titan.perftest.PerfTestApplication")
}

tasks.shadowJar {
    archiveBaseName.set("titan-perf-runner")
    archiveClassifier.set("")
    mergeServiceFiles()
}
