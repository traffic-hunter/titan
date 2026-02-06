group = "org.traffichunter.titan.core"
version = "1.0-SNAPSHOT"

dependencies {

    implementation(project(":bootstrap"))
    implementation(project(":monitor"))
    implementation(project(":recovery"))

    // embedded jetty
    implementation(project.libs.jetty.server)
    implementation(project.libs.jetty.servlet)
    implementation(project.libs.jetty.http)

    // jackson
    implementation(project.libs.jackson.databind)

    // Apache Commons pool2
    implementation(project.libs.apache.commons.pool)

    // logback
    implementation(project.libs.logback)

    // awaitility
    testImplementation(project.libs.awaitility)
}