#!/usr/bin/env bash
# One-time server ops setup (idempotent). Run as root on the server:
#   ssh ockc 'bash -s' < scripts/server/setup-ops.sh
# - nightly backup directory + settings in ockc.env (only appended if missing; secrets untouched)
# - weekly logrotate for /var/log/ockc/app.log
# - drop firewall rules for ports with nothing listening (FTP 20/21, 888, 25008, 39000-40000)
set -euo pipefail
APP=/opt/ockc
ENV=$APP/ockc.env

install -d -o ockc -g ockc -m 750 "$APP/backups"
add_env() { grep -q "^$1=" "$ENV" || { echo "$1=$2" >> "$ENV"; echo "env: added $1"; }; }
add_env BACKUP_DIR "$APP/backups"
add_env BACKUP_KEEP 14
add_env AI_DAILY_LIMIT 10

cat > /etc/logrotate.d/ockc <<'EOF'
/var/log/ockc/app.log {
    weekly
    rotate 8
    compress
    delaycompress
    missingok
    notifempty
    copytruncate
}
EOF
logrotate --debug /etc/logrotate.d/ockc >/dev/null 2>&1 && echo "logrotate: ok"

if command -v ufw >/dev/null; then
  for rule in 20/tcp 21/tcp 888/tcp 25008/tcp 39000:40000/tcp; do
    port=${rule%/tcp}
    if ss -ltn | awk 'NR>1{print $4}' | grep -qE "[:.]${port%%:*}$"; then
      echo "ufw: keep $rule (something is listening)"
    else
      ufw --force delete allow "$rule" >/dev/null 2>&1 && echo "ufw: closed $rule" || true
    fi
  done
fi
