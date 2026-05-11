import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
    id("java")
    id("com.vanniktech.maven.publish") version "0.36.0" apply false
}

group = "org.traffichunter.titan"

fun resolveGitTagVersion(): String? {
    return providers.exec {
        commandLine("git", "describe", "--tags", "--exact-match")
        isIgnoreExitValue = true
    }.standardOutput.asText.get()
        .trim()
        .takeIf { it.isNotBlank() }
}

fun resolveVersion(): String = providers.gradleProperty("versionName")
    .orElse(providers.gradleProperty("VERSION_NAME"))
    .orElse(providers.environmentVariable("GITHUB_REF_NAME"))
    .map { refName -> refName.removePrefix("refs/tags/") }
    .getOrElse(resolveGitTagVersion() ?: "1.0-SNAPSHOT")

allprojects {
    version = resolveVersion()
}

subprojects {
    apply(plugin = "java")

    repositories {
        mavenCentral()
    }

    dependencies {
        testImplementation(platform("org.junit:junit-bom:5.13.4"))
        testImplementation("org.junit.jupiter:junit-jupiter")
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")
        testImplementation(rootProject.libs.mokito)
        testImplementation("org.mockito:mockito-junit-jupiter:5.21.0")

        compileOnly(rootProject.libs.lombok)
        annotationProcessor(rootProject.libs.lombok)

        implementation(rootProject.libs.slf4j)
        implementation(rootProject.libs.google.guava)

        // netty buffer
        implementation(rootProject.libs.netty.buffer)

        // IntelliJ nullability contracts
        implementation(rootProject.libs.jetbrains.annotations)

        // assertJ
        testImplementation(rootProject.libs.assertj)
        testImplementation(rootProject.libs.awaitility)
    }

    tasks.test {
        useJUnitPlatform()
    }
}

val publishedArtifacts = mapOf(
    "bootstrap" to "titan-bootstrap",
    "core" to "titan-core",
    "titan-stomp" to "titan-stomp",
    "titan-spring-client" to "titan-spring-client",
    "fanout" to "titan-fanout",
)

configure(publishedArtifacts.keys.map { project(":$it") }) {
    apply(plugin = "com.vanniktech.maven.publish")

    val artifact = publishedArtifacts[name]!!
    val publicationVersion = rootProject.version.toString()

    val titanGitAddress = "https://github.com/traffic-hunter/titan"
    val scmConnection = "scm:git:https://github.com/traffic-hunter/titan.git"
    val scmDeveloperConnection = "scm:git:https://github.com/traffic-hunter/titan.git"

    extensions.configure<MavenPublishBaseExtension>("mavenPublishing") {
        publishToMavenCentral()
        signAllPublications()

        coordinates(
            "org.traffichunter.titan",
            artifact,
            publicationVersion,
        )

        pom {
            name.set(project.name)
            description.set("Titan ${project.name}")
            url.set(titanGitAddress)

            licenses {
                license {
                    name.set("MIT License")
                    url.set("https://opensource.org/licenses/MIT")
                }
            }

            developers {
                developer {
                    id.set("swager253")
                    name.set("yun")
                    email.set("swager253@naver.com")
                }
            }

            scm {
                url.set(titanGitAddress)
                connection.set(scmConnection)
                developerConnection.set(scmDeveloperConnection)
            }
        }
    }
}
