// .sinew-slim.yml: load, defaults, validation, the effective-config dump.
description = "SinewSlim configuration: .sinew-slim.yml loading, defaults and validation"
dependencies {
    api(project(":sinewslim-core"))
    implementation("org.yaml:snakeyaml:2.5")
}
