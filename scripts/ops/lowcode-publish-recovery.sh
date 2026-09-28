#!/usr/bin/env bash
set -euo pipefail

# Operator-only recovery client. It intentionally uses audited HTTP endpoints
# instead of mutating recovery tables with ad-hoc SQL.
: "${FORGE_ADMIN_URL:?set FORGE_ADMIN_URL, for example http://127.0.0.1:8580}"
: "${FORGE_ADMIN_TOKEN:?set FORGE_ADMIN_TOKEN to an authorized bearer token}"

command -v curl >/dev/null
command -v jq >/dev/null

usage() {
  echo "usage: $0 replay <task-id> <reason> | rollback-config <config-id> <version-id>" >&2
  exit 64
}

request() {
  curl --fail-with-body --silent --show-error \
    -H "Authorization: Bearer ${FORGE_ADMIN_TOKEN}" \
    -H "Content-Type: application/json" \
    "$@"
}

case "${1:-}" in
  replay)
    [[ $# -eq 3 && "$2" =~ ^[1-9][0-9]*$ && -n "$3" ]] || usage
    request -X POST \
      --data "$(jq -cn --arg reason "$3" '{reason: $reason}')" \
      "${FORGE_ADMIN_URL%/}/ai/lowcode/app/publish-tasks/$2/replay"
    ;;
  rollback-config)
    [[ $# -eq 3 && "$2" =~ ^[1-9][0-9]*$ && "$3" =~ ^[1-9][0-9]*$ ]] || usage
    # This restores a published configuration version. It never attempts an
    # unsafe reverse DDL; schema compensation remains a reviewed DBA action.
    request -X POST \
      "${FORGE_ADMIN_URL%/}/ai/lowcode/app/$2/rollback/$3"
    ;;
  *)
    usage
    ;;
esac
