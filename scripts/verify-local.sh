#!/usr/bin/env bash
# Builds/tests only. No server start, deployment, push, or application database access.
set -euo pipefail
cd "$(dirname "$0")/.."
[[ $# -eq 0 || ( $# -eq 1 && $1 == --install ) ]] || { echo 'Usage: bash scripts/verify-local.sh [--install]' >&2; exit 2; }
command -v python3 >/dev/null || { echo 'Install Python 3.11+ first.' >&2; exit 2; }
python3 scripts/local_preflight.py
python3 -m unittest discover -s scripts -p 'local_tools_test.py'
if [[ ${1:-} == --install ]]; then (cd frontend && npm ci); fi
(cd frontend && npm run test:markdown && npm run test:deadlines && npm run test:theme:unit && npm run test:reports:unit && npm run test:review:unit && npm run build)
# Remove both copies so an old hashed asset cannot remain in the application JAR.
rm -rf backend/src/main/resources/static backend/target/classes/static
mkdir -p backend/src/main/resources/static
cp -R frontend/dist/. backend/src/main/resources/static/
(cd backend && bash ./mvnw -B verify)
echo 'LOCAL VERIFICATION COMPLETE. Nothing was deployed or published.'
