group = "org.traffichunter.titan.bootstrap"
version = "1.0-SNAPSHOT"

dependencies {
    implementation(project.libs.snakeyaml)

    runtimeOnly(project(":core"))
    runtimeOnly(project(":titan-stomp"))
}
