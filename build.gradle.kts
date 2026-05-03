import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

// Fix 1: explicit versions so Gradle (and the IDE script engine) can resolve the plugin
//         classpath without a pluginManagement block in settings.gradle.kts.
plugins {
    id("org.jetbrains.kotlin.jvm") version "2.3.21"
    id("org.jetbrains.intellij.platform") version "2.3.0"
    id("org.jetbrains.changelog") version "2.5.0"
}

// IntelliJ Platform 2024.3+ requires Java 21; default Kotlin JVM target is 17.
kotlin { jvmToolchain(21) }

// Fix 2: repositories block is required by IntelliJ Platform Gradle Plugin v2.
//         intellijPlatform.defaultRepositories() adds the JetBrains CDN, Maven Central,
//         and the Marketplace plugin repository in one call.
repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")

    intellijPlatform {
        // Fix 3a: intellijIdeaCommunity() is the correct function name (v2 plugin dropped
        //          the old intellijIdea() shorthand).
        // Fix 3b: use a real release version string (YYYY.MAJOR.MINOR format).
        intellijIdeaCommunity("2024.3.5")
        testFramework(TestFrameworkType.Platform)

        // Required for GitRepositoryManager and git4idea.* APIs used in InlineDiffService
        bundledPlugin("Git4Idea")
    }
}

// Configure IntelliJ Platform Gradle Plugin - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-extension.html
intellijPlatform {
    pluginVerification {
        ides {
            ide(IntelliJPlatformType.IntellijIdeaCommunity, "2024.3.5")
        }
    }

    pluginConfiguration {
        // Extract the <!-- Plugin description --> section from README.md and provide for the plugin's manifest
        description = providers.fileContents(layout.projectDirectory.file("README.md")).asText.map {
            val start = "<!-- Plugin description -->"
            val end = "<!-- Plugin description end -->"

            with(it.lines()) {
                if (!containsAll(listOf(start, end))) {
                    throw GradleException("Plugin description section not found in README.md:\n$start ... $end")
                }
                subList(indexOf(start) + 1, indexOf(end)).joinToString("\n").let(::markdownToHTML)
            }
        }

        val changelog = project.changelog // local variable for configuration cache compatibility
        // Get the latest available change notes from the changelog file
        changeNotes = version.map { pluginVersion ->
            with(changelog) {
                renderItem(
                    (getOrNull(pluginVersion) ?: getUnreleased())
                        .withHeader(false)
                        .withEmptySections(false),
                    Changelog.OutputType.HTML,
                )
            }
        }
    }
}

// Configure Gradle Changelog Plugin - read more: https://github.com/JetBrains/gradle-changelog-plugin
changelog {
    groups.empty()
    repositoryUrl = providers.gradleProperty("pluginRepositoryUrl")
    versionPrefix = ""
}

tasks {
    publishPlugin {
        dependsOn(patchChangelog)
    }
}
