#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/../../.." && pwd)"
EXPECTED_COLLATION="utf8mb4_0900_ai_ci"

INIT_SCRIPT="$PROJECT_DIR/forge-server/scripts/db/init-db.sh"
COMPOSE_FILE="$PROJECT_DIR/docker-forge-admin/docker-compose.yml"
ACTIVE_SQL_PATHS=(
  "$PROJECT_DIR/forge-server/db/全量初始化SQL.sql"
  "$PROJECT_DIR/docker-forge-admin/init-sql/01-init.sql"
  "$PROJECT_DIR/forge-server/db/seed"
  "$PROJECT_DIR/forge-server/forge-admin-server/src/main/resources/sql"
  "$PROJECT_DIR/forge-server/forge-report-server/sql"
  "$PROJECT_DIR/forge-server/forge-framework"
)
CONFIG_AND_GUIDE_PATHS=(
  "$INIT_SCRIPT"
  "$COMPOSE_FILE"
  "$PROJECT_DIR/AGENTS.md"
  "$PROJECT_DIR/code-copilot/rules/project-context.md"
)

failures=0

require_pattern() {
  local pattern="$1"
  local file="$2"
  local description="$3"
  if ! rg --quiet --ignore-case -- "$pattern" "$file"; then
    echo "ERROR: $description: $file" >&2
    failures=$((failures + 1))
  fi
}

require_pattern \
  "CREATE DATABASE IF NOT EXISTS.*CHARACTER SET utf8mb4 COLLATE $EXPECTED_COLLATION" \
  "$INIT_SCRIPT" \
  "init-db.sh must create databases with $EXPECTED_COLLATION"
require_pattern \
  "ALTER DATABASE.*CHARACTER SET utf8mb4 COLLATE $EXPECTED_COLLATION" \
  "$INIT_SCRIPT" \
  "init-db.sh must align an existing database default with $EXPECTED_COLLATION"
require_pattern \
  "--character-set-server=utf8mb4" \
  "$COMPOSE_FILE" \
  "Docker MySQL must use utf8mb4"
require_pattern \
  "--collation-server=$EXPECTED_COLLATION" \
  "$COMPOSE_FILE" \
  "Docker MySQL must use $EXPECTED_COLLATION"
require_pattern \
  "SET NAMES utf8mb4 COLLATE $EXPECTED_COLLATION" \
  "$PROJECT_DIR/forge-server/db/全量初始化SQL.sql" \
  "the server full initialization SQL must set the expected connection collation"
require_pattern \
  "SET NAMES utf8mb4 COLLATE $EXPECTED_COLLATION" \
  "$PROJECT_DIR/docker-forge-admin/init-sql/01-init.sql" \
  "the Docker full initialization SQL must set the expected connection collation"
require_pattern \
  "ALTER DATABASE CHARACTER SET utf8mb4 COLLATE $EXPECTED_COLLATION" \
  "$PROJECT_DIR/forge-server/db/全量初始化SQL.sql" \
  "the server full initialization SQL must align the selected database default"
require_pattern \
  "ALTER DATABASE CHARACTER SET utf8mb4 COLLATE $EXPECTED_COLLATION" \
  "$PROJECT_DIR/docker-forge-admin/init-sql/01-init.sql" \
  "the Docker full initialization SQL must align the selected database default"
require_pattern \
  "CREATE DATABASE forge DEFAULT CHARACTER SET utf8mb4 COLLATE $EXPECTED_COLLATION" \
  "$PROJECT_DIR/AGENTS.md" \
  "AGENTS.md setup guidance must use $EXPECTED_COLLATION"
require_pattern \
  "CREATE DATABASE forge DEFAULT CHARACTER SET utf8mb4 COLLATE $EXPECTED_COLLATION" \
  "$PROJECT_DIR/code-copilot/rules/project-context.md" \
  "project context setup guidance must use $EXPECTED_COLLATION"

if rg -n --ignore-case \
  'utf8mb4_(unicode_ci|general_ci)' \
  "${CONFIG_AND_GUIDE_PATHS[@]}"; then
  echo "ERROR: initialization configuration or setup guidance contains a legacy utf8mb4 collation" >&2
  failures=$((failures + 1))
fi

if rg -n --ignore-case \
  --glob '*.sql' \
  --glob '!**/target/**' \
  'COLLATE[=[:space:]]*utf8mb4_(unicode_ci|general_ci)' \
  "${ACTIVE_SQL_PATHS[@]}"; then
  echo "ERROR: active initialization SQL contains a legacy utf8mb4 collation" >&2
  failures=$((failures + 1))
fi

if ((failures > 0)); then
  echo "Collation consistency check failed with $failures problem(s)." >&2
  exit 1
fi

echo "Collation consistency check passed: utf8mb4 / $EXPECTED_COLLATION"
