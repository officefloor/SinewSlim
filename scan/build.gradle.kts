// Walking a repository: the files git knows, include and exclude globs, and reading them in
// parallel on deep stacks. What is read from each file is the caller's.
description = "SinewSlim scanning: a repository's files, include and exclude, read in parallel"
dependencies {
    api(project(":sinewslim-javaparser"))
}
