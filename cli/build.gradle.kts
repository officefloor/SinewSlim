// The command line: scan (and, in phase 3, review, the hook and the PR comment).
dependencies {
    implementation(project(":javaparser"))
    implementation(project(":config"))
    implementation("info.picocli:picocli:4.7.7")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.20.2")
}

// One jar with every dependency in it: java -jar cli/build/libs/sinew-slim.jar scan
val fatJar by tasks.registering(Jar::class) {
    archiveFileName.set("sinew-slim.jar")
    manifest {
        attributes(
            "Main-Class" to "net.officefloor.sinewslim.cli.SinewSlim",
            "Implementation-Title" to "sinew-slim",
            "Implementation-Version" to project.version,
        )
    }
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({ configurations.runtimeClasspath.get().filter { it.name.endsWith(".jar") }.map { zipTree(it) } })
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/versions/*/module-info.class", "module-info.class")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.named("assemble") { dependsOn(fatJar) }

// the scan tests read the parser and the reports end to end
dependencies {
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.20.2")
}
