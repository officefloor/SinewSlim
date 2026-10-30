import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

// The IntelliJ plugin: the inspection, the gutter mark, the overlay of the proposal, and
// extraction through the IDE's own Extract Method with only the name asked for.
plugins {
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation(project(":javaparser"))
    implementation(project(":config"))

    intellijPlatform {
        create(
            IntelliJPlatformType.fromCode(providers.gradleProperty("platformType").get()),
            providers.gradleProperty("platformVersion").get()
        )
        bundledPlugin("com.intellij.java")
        pluginVerifier()
        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.Plugin.Java)
    }

    // the platform's test cases are JUnit 4; the root build runs tests on the JUnit Platform
    testImplementation("junit:junit:4.13.2")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine")
    testImplementation("org.opentest4j:opentest4j:1.3.0")
}

intellijPlatform {
    projectName = "sinew-slim"
    pluginConfiguration {
        version = project.version.toString()
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = provider { null }
        }
    }
    // from the release workflow's secrets (RELEASING.md); a secret that is not set arrives as an
    // empty variable, which counts as unset, so the plugin is then published unsigned
    signing {
        certificateChain = secret("CERTIFICATE_CHAIN")
        privateKey = secret("PRIVATE_KEY")
        password = secret("PRIVATE_KEY_PASSWORD")
    }
    publishing {
        token = secret("PUBLISH_TOKEN")
    }
    // -PverifyIde=IC:2024.2.6 or IntellijIdea:2026.2.3 verifies against that one IDE (CI runs one per job: the IDEs do not
    // fit on one runner's disk); without it, every IDE the platform recommends for the range
    pluginVerification {
        ides {
            val one = providers.gradleProperty("verifyIde").orNull
            if (one != null) {
                val (type, version) = one.split(":", limit = 2)
                // a type by its name (IntellijIdea, the unified IDE from 2025.3) or its code (IC)
                create(IntelliJPlatformType.values().firstOrNull { it.name == type } ?: IntelliJPlatformType.fromCode(type), version)
            } else {
                recommended()
            }
        }
    }
}

fun secret(name: String) = providers.environmentVariable(name).filter { it.isNotBlank() }

tasks {
    // publishPlugin does not run verifyPlugin itself: every recommended IDE does not fit on one
    // runner's disk, so the release workflow publishes only after the verify-plugin matrix passes

    // IDEA 2024.2.4 predates Java 25. On startup its bundled Gradle plugin (com.intellij.gradle)
    // reads JetBrains' current Gradle/JVM compatibility matrix, which now lists Java 25, and
    // 2024.2.4's JavaVersion.parse("25") throws, provoking a JreHiDpiUtil startup assertion.
    // SinewSlim needs no Gradle integration, so the runIde sandbox starts with that plugin
    // disabled (as ~/OfficeFloor-IntelliJ-Plugin does). Remove once platformVersion knows Java 25.
    named<org.jetbrains.intellij.platform.gradle.tasks.RunIdeTask>("runIde") {
        doFirst {
            val config = sandboxConfigDirectory.get().asFile
            config.mkdirs()
            config.resolve("disabled_plugins.txt").writeText("com.intellij.gradle\n")
        }
    }
}
