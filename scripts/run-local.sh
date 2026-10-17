#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -z "${DEMO_PASSWORD:-}" || ${#DEMO_PASSWORD} -lt 12 ]]; then
  echo 'Set DEMO_PASSWORD to a locally chosen password of at least 12 characters, then rerun.' >&2
  echo 'This command seeds demonstration accounts and is for isolated local evaluation only.' >&2
  exit 1
fi
command -v java >/dev/null || { echo 'Install JDK 21 first.' >&2; exit 1; }
command -v npm >/dev/null || { echo 'Install Node.js 22 first.' >&2; exit 1; }
(cd frontend && npm ci && npm run build)
mkdir -p backend/src/main/resources/static
cp -R frontend/dist/. backend/src/main/resources/static/
cd backend
bash ./mvnw -B verify
export DEMO_SEED=true
exec java -jar target/course-community-0.1.0.jar
