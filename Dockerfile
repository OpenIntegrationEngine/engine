# syntax=docker/dockerfile:1.19.0
# SPDX-License-Identifier: MPL-2.0
# SPDX-FileCopyrightText: 2025 Mitch Gaffigan

# Stages:
# 1. Builder Stage: Compiles the application and resolves dependencies.  Produces
#    JAR files that can be deployed.
#      1a. Install dependencies
#      1b. Build the application
# 2. Runner Stage: Creates a lightweight image that runs the application using the JRE.

FROM ubuntu:noble-20251013 AS builder
WORKDIR /app
# sdkman requires bash
SHELL ["/bin/bash", "-c"]
ARG GRADLE_BUILD_ARGS="-PdisableSigning=true"

# Stage 1a: Install dependencies
# Install necessary tools, then drop root. The base image ships a
# pre-created "ubuntu" user (uid 1000, the same one the runner stage
# below renames to "engine") — reuse it so Gradle doesn't run as root,
# which ignores POSIX permission bits and breaks tests that depend on
# them (e.g. Log4jMigrationsTest's read-only-file assertion).
RUN apt-get update\
    && apt-get install -y zip curl\
    && rm -rf /var/lib/apt/lists/* \
    && chown ubuntu:ubuntu /app
USER ubuntu

COPY --chown=ubuntu:ubuntu .sdkmanrc .
RUN curl -s "https://get.sdkman.io?ci=true" | bash \
    && source "$HOME/.sdkman/bin/sdkman-init.sh" && sdk env install

# Stage 1b: Build the application
# Copy the entire source tree (excluding .dockerignore files), and build
# (file encoding is pinned to UTF-8 in gradle.properties)
COPY --chown=ubuntu:ubuntu . .
# The cache mounts below own their own target dirs (uid/gid), but not the
# ~/.gradle parent itself — Gradle also writes ~/.gradle/native (extracted
# native-platform lib) outside either mount, so create the parent up front.
RUN mkdir -p /home/ubuntu/.gradle
RUN --mount=type=cache,target=/home/ubuntu/.gradle/caches,sharing=locked,uid=1000,gid=1000 \
    --mount=type=cache,target=/home/ubuntu/.gradle/wrapper,sharing=locked,uid=1000,gid=1000 \
    source "$HOME/.sdkman/bin/sdkman-init.sh" \
    && ./gradlew --no-daemon build ${GRADLE_BUILD_ARGS}

##########################################
#
#     Ubuntu JDK Image
#
##########################################

FROM eclipse-temurin:21.0.9_10-jdk-noble AS jdk-run

RUN groupadd engine \
    && usermod -l engine ubuntu \
    && adduser engine engine \
    && mkdir -p /opt/engine/appdata \
    && chown -R engine:engine /opt/engine

WORKDIR /opt/engine
COPY --chown=engine:engine --from=builder \
    --exclude=cli-lib \
    --exclude=mirth-cli-launcher.jar \
    --exclude=mccommand \
    --exclude=manager-lib \
    --exclude=mirth-manager-launcher.jar \
    --exclude=mcmanager \
    /app/server/setup ./

VOLUME /opt/engine/appdata
VOLUME /opt/engine/custom-extensions
EXPOSE 8443

USER engine
ENTRYPOINT ["./configure-from-env"]
CMD ["./oieserver"]

##########################################
#
#     Alpine JRE Image
#
##########################################

FROM eclipse-temurin:21.0.9_10-jre-alpine AS jre-run

# Alpine does not include bash by default, so we install it
RUN apk add --no-cache bash
# useradd and groupadd are not available in Alpine
RUN addgroup -S engine \
    && adduser -S -g engine engine \
    && mkdir -p /opt/engine/appdata \
    && chown -R engine:engine /opt/engine

WORKDIR /opt/engine
COPY --chown=engine:engine --from=builder \
    --exclude=cli-lib \
    --exclude=mirth-cli-launcher.jar \
    --exclude=mccommand \
    --exclude=manager-lib \
    --exclude=mirth-manager-launcher.jar \
    --exclude=mcmanager \
    /app/server/setup ./

VOLUME /opt/engine/appdata
VOLUME /opt/engine/custom-extensions

EXPOSE 8443

USER engine
ENTRYPOINT ["./configure-from-env"]
CMD ["./oieserver"]
