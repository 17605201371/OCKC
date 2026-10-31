#!/usr/bin/env bash
# Build, test and deploy to the server in one step.
#   bash scripts/deploy.sh                 full verify (frontend + 279 backend tests), then deploy
#   bash scripts/deploy.sh --skip-verify   deploy the existing backend/target jar
#   bash scripts/deploy.sh --sql fix.sql   also run an SQL script against the stopped H2 database
# Server: SSH host alias from OCKC_HOST (default "ockc"), app in /opt/ockc, systemd unit "ockc".
# The server side (scripts/deploy-remote.sh) backs up the DB and jar first and rolls back if the
# new version is not healthy within ~90 seconds.
set -euo pipefail
cd "$(dirname "$0")/.."
HOST=${OCKC_HOST:-ockc}
JAR=backend/target/course-community-0.1.0.jar
verify=1; sql=""
while [[ $# -gt 0 ]]; do
  case $1 in
    --skip-verify) verify=0 ;;
    --sql) sql=${2:?--sql needs a file}; [[ -f $sql ]] || { echo "no such file: $sql" >&2; exit 2; }; shift ;;
    *) echo "usage: bash scripts/deploy.sh [--skip-verify] [--sql file.sql]" >&2; exit 2 ;;
  esac
  shift
done

if [[ -n $(git status --porcelain) ]]; then
  echo "warning: deploying uncommitted changes:" >&2
  git status --short >&2
fi
if [[ $verify == 1 ]]; then
  log=$(mktemp -t ockc-verify)
  echo "verify: building and testing (log: $log)"
  bash scripts/verify-local.sh > "$log" 2>&1 || { tail -40 "$log" >&2; echo "verify failed, nothing deployed" >&2; exit 1; }
  grep -E "Tests run: [0-9]+, Failures: 0, Errors: 0, Skipped: [0-9]+$" "$log" | tail -1
fi
[[ -f $JAR ]] || { echo "missing $JAR; run without --skip-verify" >&2; exit 1; }
local_sha=$(shasum -a 256 "$JAR" | cut -c1-16)

echo "upload: $JAR ($local_sha) -> $HOST:/opt/ockc/app.jar.new"
scp -q "$JAR" "$HOST:/opt/ockc/app.jar.new"
remote_sql=""
if [[ -n $sql ]]; then remote_sql=/tmp/ockc-deploy.sql; scp -q "$sql" "$HOST:$remote_sql"; fi
ssh -o BatchMode=yes "$HOST" "bash -s -- $remote_sql" < scripts/deploy-remote.sh
[[ -z $remote_sql ]] || ssh -o BatchMode=yes "$HOST" "rm -f $remote_sql"

remote_sha=$(ssh -o BatchMode=yes "$HOST" 'sha256sum /opt/ockc/app.jar | cut -c1-16')
[[ $remote_sha == "$local_sha" ]] || { echo "deployed jar $remote_sha does not match local $local_sha" >&2; exit 1; }
echo "deployed $local_sha at $(git rev-parse --short HEAD)$( [[ -n $(git status --porcelain) ]] && echo ' + uncommitted changes')"
