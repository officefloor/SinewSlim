// Every module is plain Java 17: the IntelliJ platform's floor, and the study measurer's.
subprojects {
    apply(plugin = "java-library")

    group = "net.officefloor.sinewslim"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
    }

    dependencies {
        "testImplementation"(platform("org.junit:junit-bom:5.13.4"))
        "testImplementation"("org.junit.jupiter:junit-jupiter")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(17)
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        // the search recurses once or twice per level of the statement tree, and an else-if chain
        // nests one level per branch: some methods of the study's corpora need a deep stack
        jvmArgs("-Xss64m")
        // the full parity run reads the export named here (DESIGN.md, Testing); without it only
        // the committed subset is read
        for (name in listOf("sinewslim.parity.full", "sinewslim.livewidth.facts", "sinewslim.livewidth.root")) {
            System.getProperty(name)?.let { systemProperty(name, it) }
        }
        maxHeapSize = "2g"
    }
}
