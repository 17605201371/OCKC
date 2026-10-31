#!/usr/bin/env bash
# Copy the server's nightly database backups to this machine (off-server copy).
#   bash scripts/pull-backups.sh [destination]   default: ~/ockc-backups
# Only new files are copied; local copies are never deleted. Files contain student data: keep them private.
set -euo pipefail
HOST=${OCKC_HOST:-ockc}
DEST=${1:-$HOME/ockc-backups}
mkdir -p "$DEST" && chmod 700 "$DEST"
rsync -a --ignore-existing --chmod=F600 "$HOST:/opt/ockc/backups/" "$DEST/"
ls -1t "$DEST" | head -5
echo "$(ls -1 "$DEST" | wc -l | tr -d ' ') backups in $DEST"
