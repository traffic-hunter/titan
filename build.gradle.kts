import com.github.jengelman.gradle.plugins.shadow.ShadowExtension
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import me.champeau.gradle.japicmp.JapicmpTask
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.jvm.tasks.Jar
import org.gradle.language.jvm.tasks.ProcessResources

plugins {
    id("java")
    id("com.vanniktech.maven.publish") version "0.36.0" apply false
    id("com.gradleup.shadow") version "9.4.1" apply false
    id("me.champeau.gradle.japicmp") version "0.4.6" apply false
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
    // Pull request builds expose refs like "136/merge"; a slash would turn artifact
    // names into nested paths and collide inside distribution archives.
    .map { refName -> refName.replace('/', '-') }
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

    val docs = files(
        layout.projectDirectory.file("README.md"),
        fileTree(layout.projectDirectory.dir("docs")) {
            include("**/*.md")
        },
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
        val cliArchivePattern = Regex("""(titan-cli-)\d+\.\d+\.\d+(-[a-z]+-[a-z0-9]+)\.(tar\.gz|zip)""")
        val documentedVersionPattern = Regex("""(Titan `)\d+\.\d+\.\d+(`)""")
        val sharedArtifactVersionPattern = Regex("""(Use the same `)\d+\.\d+\.\d+(` version)""")

        docs.files.sortedBy { it.path }.forEach { file ->
            val text = file.readText()
            val updated = text
                .replace(dependencyPattern) { match -> match.groupValues[1] + version }
                .replace(releasePathPattern) { match -> match.groupValues[1] + version }
                .replace(serverJarPattern) { match -> match.groupValues[1] + version + match.groupValues[2] }
                .replace(cliArchivePattern) { match -> match.groupValues[1] + version + match.groupValues[2] + "." + match.groupValues[3] }
                .replace(documentedVersionPattern) { match -> match.groupValues[1] + version + match.groupValues[2] }
                .replace(sharedArtifactVersionPattern) { match -> match.groupValues[1] + version + match.groupValues[2] }

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
    "titan-client" to "titan-client",
    "titan-spring-client" to "titan-spring-client",
    "dispatch" to "titan-dispatch",
    "monitor" to "titan-monitor",
)

val apiBaselineVersion = providers.gradleProperty("apiBaselineVersion").orElse("0.9.0")
val checkApiCompatibility = tasks.register("checkApiCompatibility") {
    group = "verification"
    description = "Checks published modules against the public API baseline."
}

configure(publishedArtifacts.keys.map { project(":$it") }) {
    apply(plugin = "com.vanniktech.maven.publish")

    tasks.named<ProcessResources>("processResources") {
        from(rootProject.file("LICENSE")) {
            into("META-INF/titan")
        }
        from(rootProject.file("NOTICE")) {
            into("META-INF/titan")
        }
        from(rootProject.file("docs/netty-provenance.md")) {
            into("META-INF/titan")
        }
    }

    pluginManager.withPlugin("com.gradleup.shadow") {
        extensions.configure<ShadowExtension>("shadow") {
            addShadowVariantIntoJavaComponent.set(false)
        }
    }

    val artifact = publishedArtifacts[name]!!
    val publicationVersion = rootProject.version.toString()
    val apiBaseline = configurations.create("apiBaseline") {
        isCanBeConsumed = false
        isCanBeResolved = true
        description = "Released $artifact API used as the compatibility baseline."
    }

    dependencies.add(
        apiBaseline.name,
        "org.traffichunter.titan:$artifact:${apiBaselineVersion.get()}"
    )

    val baselineArchive = apiBaseline.incoming.artifactView {
        componentFilter { component ->
            component is ModuleComponentIdentifier
                    && component.group == "org.traffichunter.titan"
                    && component.module == artifact
        }
    }.files

    val moduleApiCompatibility = tasks.register<JapicmpTask>("checkApiCompatibility") {
        group = "verification"
        description = "Checks $artifact against version ${apiBaselineVersion.get()}."
        dependsOn(tasks.named("jar"))

        oldArchives.from(baselineArchive)
        newArchives.from(tasks.named<Jar>("jar"))
        oldClasspath.from(apiBaseline)
        newClasspath.from(configurations.named("runtimeClasspath"), tasks.named<Jar>("jar"))

        onlyModified = true
        failOnModification = true
        failOnSourceIncompatibility = true
        ignoreMissingClasses = false
        accessModifier = "public"
        htmlOutputFile = layout.buildDirectory.file("reports/japicmp/api-compatibility.html").get().asFile
        txtOutputFile = layout.buildDirectory.file("reports/japicmp/api-compatibility.txt").get().asFile
    }

    checkApiCompatibility.configure {
        dependsOn(moduleApiCompatibility)
    }

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
                    name.set("Apache License, Version 2.0")
                    url.set("https://www.apache.org/licenses/LICENSE-2.0")
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
