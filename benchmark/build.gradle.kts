plugins {
    base
}

tasks.register("performanceTest") {
    group = "verification"
    description = "Builds the JMH benchmarks and the TitanClient performance test runner."
    dependsOn(":benchmark:jmh:jmhClasses", ":benchmark:perf-test:shadowJar")
}
