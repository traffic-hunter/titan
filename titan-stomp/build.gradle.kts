plugins {
    `java-library`
}

group = "org.traffichunter.titan.transport.stomp"
version = "1.0-SNAPSHOT"

dependencies {
    api(project(":bootstrap"))
    api(project(":core"))
}
