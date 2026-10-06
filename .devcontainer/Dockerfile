# syntax=docker/dockerfile:1
ARG DEVCONTAINER_BASE_IMAGE=mcr.microsoft.com/devcontainers/base:ubuntu-24.04
FROM ${DEVCONTAINER_BASE_IMAGE}

ARG DEBIAN_FRONTEND=noninteractive
ARG DEVCONTAINER_JDK_VERSION=25
ARG DEVCONTAINER_NODE_VERSION=22
ARG PYTHON_VERSION=3.12
ARG UV_VERSION=0.6.5
ARG POSTGRES_CLIENT_VERSION=17

# 1. Install prerequisites, system utilities, and PostgreSQL client
RUN apt-get update && apt-get install -y --no-install-recommends \
    apt-transport-https \
    ca-certificates \
    curl \
    gnupg \
    jq \
    lsb-release \
    postgresql-client \
    software-properties-common \
    wget \
    && rm -rf /var/lib/apt/lists/*

# 2. Install Java 25 (matching CI reusable workflow baseline)
# Add Eclipse Adoptium and Microsoft package repositories
RUN install -m 0755 -d /etc/apt/keyrings \
    && wget -qO - https://packages.adoptium.net/artifactory/api/gpg/key/public | gpg --dearmor -o /etc/apt/keyrings/adoptium.gpg \
    && chmod a+r /etc/apt/keyrings/adoptium.gpg \
    && echo "deb [signed-by=/etc/apt/keyrings/adoptium.gpg] https://packages.adoptium.net/artifactory/deb $(lsb_release -cs) main" | tee /etc/apt/sources.list.d/adoptium.list \
    && wget -q https://packages.microsoft.com/config/ubuntu/24.04/packages-microsoft-prod.deb -O /tmp/packages-microsoft-prod.deb \
    && dpkg -i /tmp/packages-microsoft-prod.deb \
    && rm /tmp/packages-microsoft-prod.deb \
    && apt-get update \
    && (apt-get install -y --no-install-recommends msopenjdk-${DEVCONTAINER_JDK_VERSION} || apt-get install -y --no-install-recommends temurin-${DEVCONTAINER_JDK_VERSION}-jdk || apt-get install -y --no-install-recommends openjdk-${DEVCONTAINER_JDK_VERSION}-jdk) \
    && rm -rf /var/lib/apt/lists/*

# Set up architecture-agnostic JAVA_HOME
RUN JAVA_DIR=$(dirname $(dirname $(readlink -f $(which javac 2>/dev/null || which java)))) \
    && mkdir -p /usr/lib/jvm \
    && ln -sfn "${JAVA_DIR}" /usr/lib/jvm/default-java
ENV JAVA_HOME=/usr/lib/jvm/default-java
ENV PATH="${JAVA_HOME}/bin:${PATH}"

# 3. Install Node.js LTS (for executing @usebruno/cli via npx)
RUN curl -fsSL https://deb.nodesource.com/setup_${DEVCONTAINER_NODE_VERSION}.x | bash - \
    && apt-get install -y --no-install-recommends nodejs \
    && rm -rf /var/lib/apt/lists/*

# 4. Install Astral uv package manager pinned to UV_VERSION
RUN curl -LsSf "https://astral.sh/uv/${UV_VERSION}/install.sh" | env UV_INSTALL_DIR="/usr/local/bin" sh || \
    curl -LsSf https://astral.sh/uv/install.sh | env UV_INSTALL_DIR="/usr/local/bin" sh \
    && chmod +x /usr/local/bin/uv*

# 5. Preconfigure unprivileged vscode user environment and optimized Gradle cache
USER vscode
ENV PATH="/home/vscode/.local/bin:${PATH}"

RUN mkdir -p /home/vscode/.gradle \
    && echo "org.gradle.daemon=true" > /home/vscode/.gradle/gradle.properties \
    && echo "org.gradle.jvmargs=-Xmx1536m -XX:+UseZGC" >> /home/vscode/.gradle/gradle.properties \
    && echo "org.gradle.parallel=true" >> /home/vscode/.gradle/gradle.properties \
    && echo "org.gradle.vfs.watch=true" >> /home/vscode/.gradle/gradle.properties \
    && echo "org.gradle.caching=true" >> /home/vscode/.gradle/gradle.properties

WORKDIR /workspace
