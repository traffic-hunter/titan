group = "org.traffichunter.titan.core"
version = "1.0-SNAPSHOT"

dependencies {

    implementation(project(":bootstrap"))

    // embedded jetty
    implementation(project.libs.jetty.server)
    implementation(project.libs.jetty.servlet)
    implementation(project.libs.jetty.http)

    // jackson
    implementation(project.libs.jackson.databind)

    // logback
    implementation(project.libs.logback)
}
