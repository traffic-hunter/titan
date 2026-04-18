plugins {
    id("java")
    id("me.champeau.jmh") version "0.7.3"
}

group = "org.traffichunter.titan"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":core"))
    implementation(project(":fanout"))

    jmh("org.openjdk.jmh:jmh-core:1.37")
    jmh("org.openjdk.jmh:jmh-generator-annprocess:1.37")
}

jmh {
    warmupIterations.set(2)
    iterations.set(5)
    fork.set(1)
    timeOnIteration.set("1s")
    benchmarkMode.set(listOf("avgt"))
    resultFormat.set("JSON")
}
