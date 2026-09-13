plugins {
    base
}

tasks.register("performanceTest") {
    group = "verification"
    description = "Builds the JMH benchmarks, the TitanClient performance test runner, and the stability fixture."
    dependsOn(
        ":benchmark:jmh:jmhClasses",
        ":benchmark:perf-test:shadowJar",
        ":benchmark:stability-fixture:shadowJar",
    )
}
