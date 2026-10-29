# sinew-slim: a small image for any CI with Docker (GitLab, Jenkins, and the pre-commit
# framework's docker_image hooks). The runtime needs are a JRE and git: the review reads
# staged, worktree and range changes through git.
#
# Build:   docker build -t sinew-slim .
# Review:  docker run --rm -v "$PWD:/repo" sinew-slim review --mode range --base origin/main
# Scan:    docker run --rm -v "$PWD:/repo" sinew-slim scan --format html --out scan.html

FROM eclipse-temurin:17-jdk AS build
WORKDIR /src
COPY . /src
RUN ./gradlew --no-daemon --console=plain -q :cli:fatJar

FROM eclipse-temurin:17-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends git \
    && rm -rf /var/lib/apt/lists/*
# CI mounts the repository owned by another uid; without this git refuses it ("dubious ownership").
RUN git config --system --add safe.directory '*'
COPY --from=build /src/cli/build/libs/sinew-slim.jar /opt/sinew-slim/sinew-slim.jar
COPY bin/sinew-slim /usr/local/bin/sinew-slim
# Run unprivileged; the repository is mounted at /repo.
RUN useradd --create-home --uid 1001 slim
USER slim
WORKDIR /repo
ENTRYPOINT ["sinew-slim"]
CMD ["--help"]

LABEL org.opencontainers.image.title="sinew-slim" \
      org.opencontainers.image.description="Slims Java methods over the length cap at the seams where the least data crosses." \
      org.opencontainers.image.licenses="Apache-2.0" \
      org.opencontainers.image.source="https://github.com/officefloor/SinewSlim"
