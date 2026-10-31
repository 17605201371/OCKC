#!/usr/bin/env bash
# Runs ON THE SERVER (piped in by scripts/deploy.sh). Swaps /opt/ockc/app.jar.new into place.
# Always: stop service -> back up DB and current jar -> optional SQL -> swap -> start -> health check.
# If the new version does not come up healthy, restores the previous jar and DB and starts it again.
set -euo pipefail
APP=/opt/ockc
SERVICE=ockc
KEEP_JARS=${KEEP_JARS:-5}
SQL_FILE=${1:-}
cd "$APP"
[[ -f app.jar.new ]] || { echo "deploy: app.jar.new not found" >&2; exit 1; }
ts=$(date +%Y%m%d-%H%M%S)
db=data/community.mv.db
port=$(sed -n 's/^PORT=//p' ockc.env | tr -d '"' | tail -1); port=${port:-8080}

healthy() {
  for _ in $(seq 1 30); do
    sleep 3
    systemctl is-active --quiet "$SERVICE" || continue
    [[ $(curl -s -o /dev/null -w '%{http_code}' -m 5 "http://127.0.0.1:$port/") == 200 ]] && return 0
  done
  return 1
}
h2() {
  unzip -p app.jar.new 'BOOT-INF/lib/h2-*.jar' > /tmp/ockc-h2.jar && chmod 644 /tmp/ockc-h2.jar
  runuser -u ockc -- bash -c 'set -a; . /opt/ockc/ockc.env; set +a; exec java -cp /tmp/ockc-h2.jar "$@" -url "$DATABASE_URL" -user "$DATABASE_USER" -password "$DATABASE_PASSWORD"' _ "$@"
}

started=0
trap '[[ $started == 1 ]] || systemctl start "$SERVICE"; rm -f /tmp/ockc-h2.jar' EXIT

systemctl stop "$SERVICE"
cp -p "$db" "$db.bak-$ts"
cp -p app.jar "app.jar.bak-$ts"
echo "deploy: backups $db.bak-$ts, app.jar.bak-$ts"

if [[ -n $SQL_FILE ]]; then
  chmod 644 "$SQL_FILE"
  h2 org.h2.tools.RunScript -script "$SQL_FILE" -showResults
fi

chown ockc:ockc app.jar.new
mv app.jar.new app.jar
systemctl start "$SERVICE"; started=1

if healthy; then
  echo "deploy: healthy on 127.0.0.1:$port, $(sha256sum app.jar | cut -c1-16)"
  # 只保留最近几份旧 jar；数据库备份不在这里清理。
  ls -1t app.jar.bak-* 2>/dev/null | tail -n +$((KEEP_JARS + 1)) | xargs -r rm -f
  exit 0
fi

echo "deploy: new version did not become healthy, rolling back" >&2
systemctl stop "$SERVICE" || true
cp -p "app.jar.bak-$ts" app.jar
cp -p "$db.bak-$ts" "$db"
systemctl start "$SERVICE"
if healthy; then echo "deploy: rolled back to previous version" >&2; else echo "deploy: ROLLBACK ALSO UNHEALTHY, check journalctl -u $SERVICE" >&2; fi
exit 1
