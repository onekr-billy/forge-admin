#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FORGE_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
HOST="127.0.0.1"
PORT="3306"
DATABASE="forge_admin"
USER="root"
PASSWORD=""
WITH_DEMO="false"
WITH_OPTIONAL="false"
WITH_MODULE="false"
SKIP_ADMIN_INIT="false"

usage() {
  cat <<'USAGE'
Usage: init-db.sh [options]

Options:
  --host HOST              MySQL host, default 127.0.0.1
  --port PORT              MySQL port, default 3306
  --database DATABASE      Database name, default forge_admin
  --user USER              MySQL user, default root
  --password PASSWORD      MySQL password
  --with-demo              Import demo seed data
  --with-optional          Import optional seed data
  --with-module            Import module SQL from db/module
  --skip-admin-init        Skip forge-admin-server/sql/初始化脚本.sql
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --host)
      HOST="$2"
      shift 2
      ;;
    --port)
      PORT="$2"
      shift 2
      ;;
    --database)
      DATABASE="$2"
      shift 2
      ;;
    --user)
      USER="$2"
      shift 2
      ;;
    --password)
      PASSWORD="$2"
      shift 2
      ;;
    --with-demo)
      WITH_DEMO="true"
      shift
      ;;
    --with-optional)
      WITH_OPTIONAL="true"
      shift
      ;;
    --with-module)
      WITH_MODULE="true"
      shift
      ;;
    --skip-admin-init)
      SKIP_ADMIN_INIT="true"
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage
      exit 1
      ;;
  esac
done

if ! command -v mysql >/dev/null 2>&1; then
  echo "mysql client is required. Install MySQL client and retry." >&2
  exit 1
fi

MYSQL=(mysql --host="$HOST" --port="$PORT" --user="$USER" --default-character-set=utf8mb4)
if [[ -n "$PASSWORD" ]]; then
  MYSQL+=(--password="$PASSWORD")
fi

"${MYSQL[@]}" --execute="
CREATE DATABASE IF NOT EXISTS \`$DATABASE\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
ALTER DATABASE \`$DATABASE\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
"
MYSQL_DB=("${MYSQL[@]}" "$DATABASE")

run_sql_file() {
  local file="$1"
  if [[ -s "$file" ]]; then
    echo "Running $file"
    "${MYSQL_DB[@]}" < "$file"
  fi
}

run_sql_dir() {
  local dir="$1"
  if [[ -d "$dir" ]]; then
    find "$dir" -maxdepth 1 -type f -name '*.sql' | sort | while IFS= read -r file; do
      run_sql_file "$file"
    done
  fi
}

if [[ "$SKIP_ADMIN_INIT" != "true" ]]; then
  run_sql_file "$FORGE_DIR/db/全量初始化SQL.sql"
fi
run_sql_dir "$FORGE_DIR/db/seed/required"

if [[ "$WITH_MODULE" == "true" ]]; then
  run_sql_dir "$FORGE_DIR/db/module"
fi

if [[ "$WITH_DEMO" == "true" ]]; then
  run_sql_dir "$FORGE_DIR/db/seed/demo"
fi

if [[ "$WITH_OPTIONAL" == "true" ]]; then
  run_sql_dir "$FORGE_DIR/db/seed/optional"
fi

echo "Database initialization completed for $DATABASE."
