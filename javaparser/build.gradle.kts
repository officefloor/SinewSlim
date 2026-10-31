// The facts from Java source, by JavaParser's syntax tree. No symbol solver: syntax and lexical scope only.
description = "SinewSlim facts from Java source, by JavaParser"
dependencies {
    api(project(":sinewslim-facts"))
    testImplementation(project(":sinewslim-core"))
    // as the study's measurer (concentration-divergence-study/measurer/pom.xml); api, as an
    // extractor reads its syntax tree
    api("com.github.javaparser:javaparser-core:3.28.2")
    // the study's facts, to check these against, are JSON
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.20.2")
}
