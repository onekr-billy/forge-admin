#!/usr/bin/env bash
set -euo pipefail

readonly FORGE_REPOSITORY_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
forge_node_bin="$("${FORGE_REPOSITORY_ROOT}/scripts/ci/bootstrap-node20.sh")"
export PATH="${forge_node_bin}:${PATH}"
export CI=true
export HUSKY=0

corepack enable --install-directory "${forge_node_bin}"
corepack prepare pnpm@10.28.1 --activate
"${FORGE_REPOSITORY_ROOT}/scripts/ci/verify-toolchain.sh" --frontend

pnpm --dir "${FORGE_REPOSITORY_ROOT}/forge-admin-ui" --ignore-workspace install --frozen-lockfile
pnpm --dir "${FORGE_REPOSITORY_ROOT}/forge-admin-ui" --ignore-workspace build

pnpm --dir "${FORGE_REPOSITORY_ROOT}/forge-report-ui" --ignore-workspace install --frozen-lockfile
pnpm --dir "${FORGE_REPOSITORY_ROOT}/forge-report-ui" --ignore-workspace build
