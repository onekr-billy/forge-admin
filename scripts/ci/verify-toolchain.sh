#!/usr/bin/env bash
set -euo pipefail

forge_scope="${1:---all}"

verify_backend() {
  local java_specification_version
  local maven_java_version
  java_specification_version="$(java -XshowSettings:properties -version 2>&1 \
    | awk -F'= ' '/java.specification.version =/ { print $2; exit }')"
  if [[ "${java_specification_version}" != "17" ]]; then
    echo "Forge backend requires JDK 17; found ${java_specification_version:-unknown}." >&2
    exit 1
  fi
  maven_java_version="$(mvn -version | awk -F'[:,]' '/Java version:/ { gsub(/^ +| +$/, "", $2); print $2; exit }')"
  if [[ "${maven_java_version}" != 17.* ]]; then
    echo "Maven must run on JDK 17; found ${maven_java_version:-unknown}." >&2
    exit 1
  fi
}

verify_frontend() {
  local node_version
  local pnpm_version
  node_version="$(node --version)"
  pnpm_version="$(pnpm --version)"
  if [[ "${node_version}" != "v20.19.5" ]]; then
    echo "Forge frontend CI requires Node.js v20.19.5; found ${node_version}." >&2
    exit 1
  fi
  if [[ "${pnpm_version}" != "10.28.1" ]]; then
    echo "Forge frontend CI requires pnpm 10.28.1; found ${pnpm_version}." >&2
    exit 1
  fi
}

case "${forge_scope}" in
  --backend) verify_backend ;;
  --frontend) verify_frontend ;;
  --all)
    verify_backend
    verify_frontend
    ;;
  *)
    echo "Usage: $0 [--backend|--frontend|--all]" >&2
    exit 2
    ;;
esac
