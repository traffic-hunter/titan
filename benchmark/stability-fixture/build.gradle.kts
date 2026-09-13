plugins {
    application
    id("com.gradleup.shadow")
}

dependencies {
    implementation(project(":core"))
    implementation(project(":titan-stomp"))
    implementation(project(":dispatch"))
}

application {
    mainClass.set("org.traffichunter.titan.stability.StabilityFixtureApplication")
}

tasks.shadowJar {
    archiveBaseName.set("titan-stability-fixture")
    archiveClassifier.set("")
    mergeServiceFiles()
}
