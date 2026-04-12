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
ARG ANT_BUILD_ARGS="-DdisableSigning=true"

# Stage 1a: Install dependencies
# Install necessary tools
COPY .sdkmanrc .
RUN apt-get update\
    && apt-get install -y zip curl\
    && curl -s "https://get.sdkman.io?ci=true" | bash \
    && source "$HOME/.sdkman/bin/sdkman-init.sh" && sdk env install \
    && rm -rf /var/lib/apt/lists/*

# Stage 1b: Build the application
# Copy the entire source tree (excluding .dockerignore files), and build
COPY . .
WORKDIR /app/server
RUN source "$HOME/.sdkman/bin/sdkman-init.sh" \
    && ANT_OPTS="-Dfile.encoding=UTF8" ant -f mirth-build.xml ${ANT_BUILD_ARGS}

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
