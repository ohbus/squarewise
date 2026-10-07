# syntax=docker/dockerfile:1
ARG NODE_IMAGE=node:24.18.0-bookworm-slim
FROM ${NODE_IMAGE}

ARG MERMAID_CLI_VERSION=11.12.0
RUN apt-get update \
    && apt-get install --no-install-recommends --yes chromium \
    && rm -rf /var/lib/apt/lists/* \
    && npm install --global "@mermaid-js/mermaid-cli@${MERMAID_CLI_VERSION}" \
    && npm cache clean --force
ENV PUPPETEER_SKIP_DOWNLOAD=true
WORKDIR /workspace
ENTRYPOINT ["mmdc"]
