// The pure core: the cost, the search and the result, over the facts.
description = "The SinewSlim core: the conventional cost, the search and the result, over the facts"
dependencies {
    api(project(":sinewslim-facts"))
    // the parity data (split.py's answers on the study's facts) is JSON
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.20.2")
}
