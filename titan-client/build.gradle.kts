plugins {
    `java-library`
}

group = "org.traffichunter.titan.client"
version = "1.0-SNAPSHOT"

dependencies {
    api(project(":core"))
    api(project(":titan-stomp"))

    testImplementation(project(":dispatch"))
}
