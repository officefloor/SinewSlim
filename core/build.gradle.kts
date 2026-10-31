// The pure core: the facts model, the seam, the search and the result. No dependencies at run time.
description = "The SinewSlim core: the facts model, the seam, the search and the result"
dependencies {
    // the parity data (split.py's answers on the study's facts) is JSON
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.20.2")
}
