#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
export DEMO_SEED=true
# Explicitly loopback only. Cloud/production launch must use its own reviewed configuration.
export SERVER_ADDRESS="${SERVER_ADDRESS:-127.0.0.1}"
command -v python3 >/dev/null || { echo 'Install Python 3.11+ first.' >&2; exit 2; }
python3 scripts/local_preflight.py
bash scripts/verify-local.sh --install
cd backend
exec java -jar target/course-community-0.1.0.jar
