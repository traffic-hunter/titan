import com.github.jengelman.gradle.plugins.shadow.ShadowExtension
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
    id("java")
    id("com.vanniktech.maven.publish") version "0.36.0" apply false
    id("com.gradleup.shadow") version "9.4.1" apply false
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

tasks.register("updateReleaseDocsVersion") {
    group = "documentation"
    description = "Updates README and docs examples to the release version."

    val releaseVersion = providers.gradleProperty("releaseVersion")
        .orElse(providers.gradleProperty("versionName"))
        .orElse(providers.environmentVariable("GITHUB_REF_NAME"))
        .map { it.removePrefix("refs/tags/") }

    val docs = listOf(
        layout.projectDirectory.file("README.md"),
        layout.projectDirectory.file("docs/examples/client.md"),
        layout.projectDirectory.file("docs/examples/server.md"),
        layout.projectDirectory.file("docs/examples/spring-client.md"),
    )

    inputs.property("releaseVersion", releaseVersion)
    inputs.files(docs)
    outputs.files(docs)

    doLast {
        val version = releaseVersion.orNull
            ?: error("releaseVersion, versionName, or GITHUB_REF_NAME is required")
        require(Regex("""\d+\.\d+\.\d+""").matches(version)) {
            "Invalid semantic version: $version"
        }

        val dependencyPattern = Regex("""(org\.traffichunter\.titan:[^:")]+:)\d+\.\d+\.\d+""")
        val releasePathPattern = Regex("""(releases/download/)\d+\.\d+\.\d+""")
        val serverJarPattern = Regex("""(titan-server-)\d+\.\d+\.\d+(\.jar)""")

        docs.forEach { doc ->
            val file = doc.asFile
            val text = file.readText()
            val updated = text
                .replace(dependencyPattern) { match -> match.groupValues[1] + version }
                .replace(releasePathPattern) { match -> match.groupValues[1] + version }
                .replace(serverJarPattern) { match -> match.groupValues[1] + version + match.groupValues[2] }

            if (updated != text) {
                file.writeText(updated)
                logger.lifecycle("Updated ${file.relativeTo(projectDir).path} to $version")
            }
        }
    }
}

subprojects {
    apply(plugin = "java")

    repositories {
        mavenCentral()
    }

    dependencies {
        testImplementation(platform("org.junit:junit-bom:6.1.0"))
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

    pluginManager.withPlugin("com.gradleup.shadow") {
        extensions.configure<ShadowExtension>("shadow") {
            addShadowVariantIntoJavaComponent.set(false)
        }
    }

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
