#!/bin/sh
set -eu

log() { echo "[db] $*"; }

# Kiem tra that su bang mot truy van, khong chi mo cong TCP.
# Aiven bat buoc TLS, Laragon thi khong.
probe() {
  host="$1"; port="$2"; db="$3"; user="$4"; pass="$5"; tls="$6"
  if [ "$tls" = "yes" ]; then
    MYSQL_PWD="$pass" mysql --connect-timeout=8 --ssl \
      -h "$host" -P "$port" -u "$user" -D "$db" -e "SELECT 1" >/dev/null 2>&1
  else
    MYSQL_PWD="$pass" mysql --connect-timeout=8 --skip-ssl \
      -h "$host" -P "$port" -u "$user" -D "$db" -e "SELECT 1" >/dev/null 2>&1
  fi
}

probe_with_retry() {
  host="$1"; port="$2"; db="$3"; user="$4"; pass="$5"; tls="$6"; attempts="${7:-2}"
  i=1
  while [ "$i" -le "$attempts" ]; do
    if probe "$host" "$port" "$db" "$user" "$pass" "$tls"; then
      return 0
    fi
    log "lan thu $i that bai ($host:$port/$db)"
    i=$((i + 1))
    [ "$i" -le "$attempts" ] && sleep 2
  done
  return 1
}

use_aiven() {
  export PARKING_DB_URL="${PARKING_AIVEN_URL:?thieu PARKING_AIVEN_URL}"
  export PARKING_DB_USERNAME="${PARKING_AIVEN_USERNAME:?thieu PARKING_AIVEN_USERNAME}"
  export PARKING_DB_PASSWORD="${PARKING_AIVEN_PASSWORD:-}"
  log "dang dung Aiven (${PARKING_AIVEN_HOST:-?}/${PARKING_AIVEN_DB:-?})"
}

use_laragon() {
  export PARKING_DB_URL="${PARKING_LARAGON_URL:?thieu PARKING_LARAGON_URL}"
  export PARKING_DB_USERNAME="${PARKING_LARAGON_USERNAME:-root}"
  export PARKING_DB_PASSWORD="${PARKING_LARAGON_PASSWORD:-}"
  log "dang dung Laragon (${PARKING_LARAGON_HOST:-host.docker.internal}/${PARKING_LARAGON_DB:-parking_anpr})"
}

MODE="${PARKING_DB_MODE:-auto}"
log "che do: $MODE"

case "$MODE" in
  aiven)
    probe_with_retry "${PARKING_AIVEN_HOST:-}" "${PARKING_AIVEN_PORT:-3306}" \
      "${PARKING_AIVEN_DB:-parking_anpr}" "${PARKING_AIVEN_USERNAME:-}" \
      "${PARKING_AIVEN_PASSWORD:-}" yes 2 \
      || { log "Aiven khong ket noi duoc, dung lai"; exit 1; }
    use_aiven
    ;;
  laragon)
    probe_with_retry "${PARKING_LARAGON_HOST:-host.docker.internal}" "${PARKING_LARAGON_PORT:-3306}" \
      "${PARKING_LARAGON_DB:-parking_anpr}" "${PARKING_LARAGON_USERNAME:-root}" \
      "${PARKING_LARAGON_PASSWORD:-}" no 1 \
      || { log "Laragon khong ket noi duoc, dung lai"; exit 1; }
    use_laragon
    ;;
  auto)
    if probe_with_retry "${PARKING_AIVEN_HOST:-}" "${PARKING_AIVEN_PORT:-3306}" \
        "${PARKING_AIVEN_DB:-parking_anpr}" "${PARKING_AIVEN_USERNAME:-}" \
        "${PARKING_AIVEN_PASSWORD:-}" yes 2; then
      use_aiven
    else
      log "Aiven that bai, tu dong chuyen sang Laragon"
      probe_with_retry "${PARKING_LARAGON_HOST:-host.docker.internal}" "${PARKING_LARAGON_PORT:-3306}" \
        "${PARKING_LARAGON_DB:-parking_anpr}" "${PARKING_LARAGON_USERNAME:-root}" \
        "${PARKING_LARAGON_PASSWORD:-}" no 1 \
        || { log "Ca Aiven va Laragon deu that bai"; exit 1; }
      use_laragon
    fi
    ;;
  *)
    log "PARKING_DB_MODE khong hop le: $MODE (chi nhan auto|aiven|laragon)"
    exit 1
    ;;
esac

exec java -jar /app/app.jar
