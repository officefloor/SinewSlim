// The facts from Java source, by JavaParser's syntax tree. No symbol solver: syntax and lexical scope only.
dependencies {
    api(project(":core"))
    // as the study's measurer (concentration-divergence-study/measurer/pom.xml)
    implementation("com.github.javaparser:javaparser-core:3.28.2")
    // the study's facts, to check these against, are JSON
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.20.2")
}
