#!/usr/bin/env python3
"""Two-way sync between Aiven (primary) and the local MySQL/Laragon database.

The sync is intentionally conservative:
- Aiven wins for configuration/master-data conflicts.
- Append-only tables are unioned; conflicting rows are logged, not overwritten.
- Parking sessions and slots use domain-specific merge rules.
- Local auto-increment values are moved to a high range so new rows created
  during a fallback do not reuse Aiven IDs.
- Hard deletes are not propagated (there are no tombstones in the schema).
"""

from __future__ import annotations

import argparse
import datetime as dt
import decimal
import json
import logging
import msvcrt
import os
import re
import sys
import time
from pathlib import Path
from typing import Any

import pymysql
from pymysql.cursors import DictCursor

SCRIPT_DIR = Path(__file__).resolve().parent
ROOT = SCRIPT_DIR.parents[1]
ENV_FILE = ROOT / "deploy" / ".env.deploy"
LOG_DIR = SCRIPT_DIR / "logs"
LOG_DIR.mkdir(parents=True, exist_ok=True)
LOG_FILE = LOG_DIR / "sync.log"
CONFLICT_LOG = LOG_DIR / "conflicts.log"
LOCK_FILE = SCRIPT_DIR / ".sync.lock"
LOOP_LOCK_FILE = SCRIPT_DIR / ".sync-loop.lock"

# IDs created by the local fallback start here. Aiven keeps using its normal
# low range; after syncing a high local ID, the script moves Aiven's counter
# back to max(low IDs) + 1.
LOCAL_ID_OFFSET = 1_000_000_000_000

EXCLUDED_TABLES = {
    "parking_card_code_migration_backup",
    "resident_image_private_migration_backup",
    # Authentication sessions/tokens must not be copied between installations.
    "desktop_sessions",
    "desktop_refresh_tokens",
}

TABLE_ORDER = [
    "households",
    "family_members",
    "vehicles",
    "parking_cards",
    "vehicle_authorized_members",
    "pricing_rules",
    "parking_policy",
    "security_roles",
    "security_role_permissions",
    "system_accounts",
    "parking_sessions",
    "parking_slots",
    "payment_transactions",
    "subscription_payments",
    "parking_operations",
    "manual_override_events",
    "gate_shift_assignments",
    "gate_evidences",
    "gate_evidence_assets",
    "security_audit_events",
]

APPEND_ONLY = {
    "payment_transactions",
    "subscription_payments",
    "manual_override_events",
    "gate_shift_assignments",
    "gate_evidences",
    "gate_evidence_assets",
    "security_audit_events",
}

TIMESTAMP_POLICY = {
    "parking_operations",
}

AIVEN_WINS = {
    "households",
    "family_members",
    "vehicles",
    "parking_cards",
    "vehicle_authorized_members",
    "pricing_rules",
    "parking_policy",
    "security_roles",
    "security_role_permissions",
}

logger = logging.getLogger("autoparking-sync")


def configure_logging(verbose: bool = False) -> None:
    if logger.handlers:
        return
    logger.setLevel(logging.DEBUG if verbose else logging.INFO)
    fmt = logging.Formatter("%(asctime)s %(levelname)s %(message)s", "%Y-%m-%d %H:%M:%S")

    console = logging.StreamHandler(sys.stdout)
    console.setFormatter(fmt)
    logger.addHandler(console)

    file_handler = logging.FileHandler(LOG_FILE, encoding="utf-8")
    file_handler.setFormatter(fmt)
    logger.addHandler(file_handler)


def load_env(path: Path) -> dict[str, str]:
    result: dict[str, str] = {}
    if not path.exists():
        return result
    for raw in path.read_text(encoding="utf-8-sig").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        value = value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in ("'", '"'):
            value = value[1:-1]
        result[key.strip()] = value
    return result


def setting(env: dict[str, str], *keys: str, default: str | None = None) -> str | None:
    for key in keys:
        value = os.environ.get(key)
        if value is None or value == "":
            value = env.get(key)
        if value is not None and value != "":
            return value
    return default


def parse_jdbc(url: str | None) -> tuple[str | None, int | None, str | None]:
    if not url:
        return None, None, None
    match = re.match(r"jdbc:mysql://([^/:?]+)(?::(\d+))?/([^?]+)", url)
    if not match:
        return None, None, None
    return match.group(1), int(match.group(2) or 3306), match.group(3)


def build_config(env: dict[str, str]) -> dict[str, dict[str, Any]]:
    aiven_url = setting(env, "PARKING_AIVEN_URL")
    url_host, url_port, url_db = parse_jdbc(aiven_url)
    aiven_host = setting(env, "PARKING_AIVEN_HOST", default=url_host)
    aiven_port = int(setting(env, "PARKING_AIVEN_PORT", default=str(url_port or 3306)) or 3306)
    aiven_db = setting(env, "PARKING_AIVEN_DB", default=url_db or "parking_anpr")
    aiven_user = setting(env, "PARKING_AIVEN_USERNAME")
    aiven_password = setting(env, "PARKING_AIVEN_PASSWORD")

    # The host-side sync connects to local MySQL over loopback, not through the
    # Docker-only host.docker.internal alias.
    local_host = setting(env, "PARKING_LARAGON_SYNC_HOST", default="127.0.0.1")
    local_port = int(setting(env, "PARKING_LARAGON_PORT", default="3306") or 3306)
    local_db = setting(env, "PARKING_LARAGON_DB", "PARKING_LARAGON_DATABASE", default="parking_anpr")
    local_user = setting(env, "PARKING_LARAGON_USERNAME", default="root")
    local_password = setting(env, "PARKING_LARAGON_PASSWORD", default="")

    missing = []
    if not aiven_host:
        missing.append("PARKING_AIVEN_HOST")
    if not aiven_user:
        missing.append("PARKING_AIVEN_USERNAME")
    if aiven_password is None:
        missing.append("PARKING_AIVEN_PASSWORD")
    if not local_user:
        missing.append("PARKING_LARAGON_USERNAME")
    if local_password is None:
        missing.append("PARKING_LARAGON_PASSWORD")
    if missing:
        raise RuntimeError("Thieu cau hinh: " + ", ".join(missing))

    return {
        "aiven": {
            "host": aiven_host,
            "port": aiven_port,
            "user": aiven_user,
            "password": aiven_password or "",
            "database": aiven_db or "parking_anpr",
            "ssl": {"verify_mode": False},
        },
        "local": {
            "host": local_host,
            "port": local_port,
            "user": local_user,
            "password": local_password or "",
            "database": local_db or "parking_anpr",
            "ssl": None,
        },
    }


def connect(cfg: dict[str, Any]):
    kwargs: dict[str, Any] = {
        "host": cfg["host"],
        "port": cfg["port"],
        "user": cfg["user"],
        "password": cfg["password"],
        "database": cfg["database"],
        "charset": "utf8mb4",
        "cursorclass": DictCursor,
        "autocommit": False,
        "connect_timeout": 10,
        "read_timeout": 120,
        "write_timeout": 120,
        "program_name": "autoparking-sync",
    }
    if cfg.get("ssl"):
        kwargs["ssl"] = cfg["ssl"]
    return pymysql.connect(**kwargs)


class SingleInstance:
    def __init__(self, path: Path = LOCK_FILE) -> None:
        self.path = path
        self.handle = None

    def __enter__(self):
        self.handle = open(self.path, "a+", encoding="ascii")
        self.handle.seek(0)
        if self.handle.read(1) == "":
            self.handle.write("0")
            self.handle.flush()
        self.handle.seek(0)
        try:
            msvcrt.locking(self.handle.fileno(), msvcrt.LK_NBLCK, 1)
        except OSError as exc:
            self.handle.close()
            self.handle = None
            raise RuntimeError("Mot tien trinh dong bo khac dang chay.") from exc
        return self

    def __exit__(self, exc_type, exc, tb):
        if self.handle:
            try:
                self.handle.seek(0)
                msvcrt.locking(self.handle.fileno(), msvcrt.LK_UNLCK, 1)
            finally:
                self.handle.close()
                self.handle = None


def list_tables(conn) -> set[str]:
    with conn.cursor() as cur:
        cur.execute(
            "SELECT table_name AS name FROM information_schema.tables "
            "WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'"
        )
        return {row["name"] for row in cur.fetchall()}


def table_info(conn, table: str) -> dict[str, Any]:
    with conn.cursor() as cur:
        cur.execute(
            "SELECT column_name AS name, extra AS extra, column_type AS ctype, "
            "is_nullable AS nullable, column_default AS default_value "
            "FROM information_schema.columns "
            "WHERE table_schema = DATABASE() AND table_name = %s "
            "ORDER BY ordinal_position",
            (table,),
        )
        columns = cur.fetchall()
        cur.execute(
            "SELECT column_name AS name FROM information_schema.key_column_usage "
            "WHERE table_schema = DATABASE() AND table_name = %s "
            "AND constraint_name = 'PRIMARY' ORDER BY ordinal_position",
            (table,),
        )
        pk = [row["name"] for row in cur.fetchall()]

    auto_col = None
    generated = set()
    names = []
    for col in columns:
        name = col["name"]
        extra = (col["extra"] or "").lower()
        names.append(name)
        if "auto_increment" in extra:
            auto_col = name
        if "generated" in extra:
            generated.add(name)
    return {
        "columns": names,
        "pk": pk,
        "auto_col": auto_col,
        "generated": generated,
    }


def fetch_rows(conn, table: str, columns: list[str]) -> list[dict[str, Any]]:
    sql = "SELECT " + ", ".join(f"`{col}`" for col in columns) + f" FROM `{table}`"
    with conn.cursor() as cur:
        cur.execute(sql)
        return list(cur.fetchall())


def canonical_value(value: Any) -> Any:
    # BIT(1) is returned as b"\x00"/b"\x01" by PyMySQL, while some Aiven
    # columns are exposed as TINYINT(1) and returned as 0/1. Treat both as
    # the same logical boolean and keep binary values longer than one byte.
    if isinstance(value, (bytes, bytearray)):
        if len(value) == 1:
            return value[0]
        return bytes(value)
    return value


def normalize(value: Any) -> Any:
    value = canonical_value(value)
    if isinstance(value, decimal.Decimal):
        return str(value)
    if isinstance(value, dt.datetime):
        return value.isoformat(sep=" ")
    if isinstance(value, dt.date):
        return value.isoformat()
    if isinstance(value, dt.time):
        return value.isoformat()
    return value


def fingerprint(row: dict[str, Any], columns: list[str]) -> tuple[Any, ...]:
    return tuple(normalize(row.get(col)) for col in columns)


def key_of(row: dict[str, Any], pk: list[str]) -> tuple[Any, ...]:
    return tuple(row.get(col) for col in pk)


def time_key(value: Any) -> dt.datetime:
    if isinstance(value, dt.datetime):
        return value
    if isinstance(value, dt.date):
        return dt.datetime.combine(value, dt.time())
    return dt.datetime.min


def row_time(row: dict[str, Any], names: list[str]) -> dt.datetime:
    for name in names:
        if name in row and row[name] is not None:
            return time_key(row[name])
    return dt.datetime.min


def log_conflict(table: str, key: tuple[Any, ...], reason: str) -> None:
    safe_key = json.dumps([normalize(v) for v in key], ensure_ascii=False, default=str)
    with CONFLICT_LOG.open("a", encoding="utf-8") as handle:
        handle.write(f"{dt.datetime.now().isoformat(sep=' ')}\t{table}\t{safe_key}\t{reason}\n")


def write_row(conn, table: str, columns: list[str], pk: list[str], row: dict[str, Any]) -> None:
    cols = list(columns)
    placeholders = ", ".join(["%s"] * len(cols))
    col_sql = ", ".join(f"`{col}`" for col in cols)
    non_pk = [col for col in cols if col not in pk]
    if non_pk:
        updates = ", ".join(f"`{col}` = VALUES(`{col}`)" for col in non_pk)
        sql = (
            f"INSERT INTO `{table}` ({col_sql}) VALUES ({placeholders}) "
            f"ON DUPLICATE KEY UPDATE {updates}"
        )
    else:
        sql = f"INSERT INTO `{table}` ({col_sql}) VALUES ({placeholders})"
    values = [canonical_value(row.get(col)) for col in cols]
    with conn.cursor() as cur:
        cur.execute(sql, values)


def session_entry_time(conn, session_id: Any) -> dt.datetime:
    if session_id is None:
        return dt.datetime.min
    with conn.cursor() as cur:
        cur.execute("SELECT entry_time FROM `parking_sessions` WHERE id = %s", (session_id,))
        row = cur.fetchone()
    return time_key(row["entry_time"]) if row else dt.datetime.min


def choose_session(a: dict[str, Any], l: dict[str, Any]) -> str:
    a_exit = row_time(a, ["exit_time"])
    l_exit = row_time(l, ["exit_time"])
    if l_exit != dt.datetime.min and (a_exit == dt.datetime.min or l_exit > a_exit):
        return "local"
    return "aiven"


def session_is_open(conn, session_id: Any) -> bool:
    if session_id is None:
        return False
    with conn.cursor() as cur:
        cur.execute(
            "SELECT status, exit_time FROM `parking_sessions` WHERE id = %s",
            (session_id,),
        )
        row = cur.fetchone()
    return bool(row) and row.get("status") == "OPEN" and row.get("exit_time") is None


def choose_slot(a: dict[str, Any], l: dict[str, Any], aiven_conn, local_conn) -> str:
    a_sid = a.get("current_session_id")
    l_sid = l.get("current_session_id")
    a_open = session_is_open(aiven_conn, a_sid)
    l_open = session_is_open(local_conn, l_sid)

    if a_sid != l_sid:
        if a_open and not l_open:
            return "aiven"
        if l_open and not a_open:
            return "local"
        # Cả hai bên đều trỏ tới session đã đóng/không tồn tại:
        # chọn bên Aiven để không đẩy tham chiếu cũ từ local ngược trở lại.
        return "aiven"

    if not a_open and not l_open:
        return "aiven"

    # Same open session reference: prefer the side that has an explicit override.
    for field in ("status_override", "assigned_vehicle_id", "borrowed_until"):
        a_value = a.get(field)
        l_value = l.get(field)
        if l_value is not None and a_value is None:
            return "local"
        if a_value is not None and l_value is None:
            return "aiven"
    return "aiven"


def choose_winner(
    table: str,
    a: dict[str, Any],
    l: dict[str, Any],
    aiven_conn,
    local_conn,
) -> tuple[str | None, str]:
    if table in APPEND_ONLY:
        return None, "append-only-conflict"
    if table in TIMESTAMP_POLICY:
        a_time = row_time(a, ["updated_at", "modified_at", "last_updated", "created_at"])
        l_time = row_time(l, ["updated_at", "modified_at", "last_updated", "created_at"])
        if l_time > a_time:
            return "local", "newer-updated-at"
        return "aiven", "aiven-newer-or-equal"
    if table == "system_accounts":
        a_version = int(a.get("security_version") or 0)
        l_version = int(l.get("security_version") or 0)
        if l_version > a_version:
            return "local", "higher-security-version"
        return "aiven", "aiven-security-version"
    if table == "parking_sessions":
        return choose_session(a, l), "session-merge"
    if table == "parking_slots":
        return choose_slot(a, l, aiven_conn, local_conn), "slot-merge"
    if table in AIVEN_WINS:
        return "aiven", "aiven-primary"
    return "aiven", "aiven-default"


def ensure_local_id_offset(local, infos: dict[str, dict[str, Any]], dry_run: bool) -> None:
    for table, info in infos.items():
        auto_col = info.get("auto_col")
        pk = info.get("pk") or []
        if not auto_col or pk != [auto_col]:
            continue
        with local.cursor() as cur:
            cur.execute(f"SELECT MAX(`{auto_col}`) AS max_id FROM `{table}`")
            max_id = cur.fetchone()["max_id"] or 0
            cur.execute(
                "SELECT AUTO_INCREMENT AS next_id FROM information_schema.tables "
                "WHERE table_schema = DATABASE() AND table_name = %s",
                (table,),
            )
            next_row = cur.fetchone()
            next_id = int(next_row["next_id"] or 0) if next_row else 0
        if max_id < LOCAL_ID_OFFSET and next_id < LOCAL_ID_OFFSET:
            logger.info(
                "Local ID offset: %s next=%s -> %s",
                table,
                next_id or max_id + 1,
                LOCAL_ID_OFFSET,
            )
            if not dry_run:
                with local.cursor() as cur:
                    cur.execute(f"ALTER TABLE `{table}` AUTO_INCREMENT = {LOCAL_ID_OFFSET}")
                local.commit()


def reset_aiven_auto_increment(aiven, infos: dict[str, dict[str, Any]]) -> None:
    for table, info in infos.items():
        auto_col = info.get("auto_col")
        pk = info.get("pk") or []
        if not auto_col or pk != [auto_col]:
            continue
        with aiven.cursor() as cur:
            cur.execute(
                f"SELECT SUM(CASE WHEN `{auto_col}` >= %s THEN 1 ELSE 0 END) AS high_count, "
                f"MAX(CASE WHEN `{auto_col}` < %s THEN `{auto_col}` END) AS max_low "
                f"FROM `{table}`",
                (LOCAL_ID_OFFSET, LOCAL_ID_OFFSET),
            )
            row = cur.fetchone()
            cur.execute(
                "SELECT AUTO_INCREMENT AS next_id FROM information_schema.tables "
                "WHERE table_schema = DATABASE() AND table_name = %s",
                (table,),
            )
            next_row = cur.fetchone()
        if not row or int(row["high_count"] or 0) == 0:
            continue
        max_low = row["max_low"]
        if max_low is None:
            continue
        next_low = int(max_low) + 1
        current_next = int(next_row["next_id"] or 0) if next_row else 0
        if current_next == next_low:
            continue
        logger.info("Reset Aiven AUTO_INCREMENT: %s -> %s", table, next_low)
        try:
            with aiven.cursor() as cur:
                cur.execute(f"ALTER TABLE `{table}` AUTO_INCREMENT = {next_low}")
            aiven.commit()
        except Exception as exc:
            logger.warning("Khong reset duoc AUTO_INCREMENT cho %s: %s", table, exc)


def process_table(
    aiven,
    local,
    table: str,
    a_info: dict[str, Any],
    l_info: dict[str, Any],
    dry_run: bool,
) -> dict[str, int]:
    common = [col for col in l_info["columns"] if col in set(a_info["columns"])]
    pk = [col for col in a_info["pk"] if col in common]
    if not pk:
        logger.warning("Bo qua %s: khong co khoa chinh chung.", table)
        return {"a_to_l": 0, "l_to_a": 0, "conflicts": 0, "skipped": 0}

    a_rows = fetch_rows(aiven, table, common)
    l_rows = fetch_rows(local, table, common)
    a_by_key = {key_of(row, pk): row for row in a_rows}
    l_by_key = {key_of(row, pk): row for row in l_rows}

    stats = {"a_to_l": 0, "l_to_a": 0, "conflicts": 0, "skipped": 0}

    if not dry_run:
        aiven.begin()
        local.begin()
    try:
        for key in sorted(set(a_by_key) | set(l_by_key), key=lambda value: tuple(str(v) for v in value)):
            a_row = a_by_key.get(key)
            l_row = l_by_key.get(key)

            if a_row is not None and l_row is None:
                logger.debug("%s %s: Aiven -> Local (insert)", table, key)
                if not dry_run:
                    write_row(local, table, common, pk, a_row)
                stats["a_to_l"] += 1
                continue

            if l_row is not None and a_row is None:
                logger.debug("%s %s: Local -> Aiven (insert)", table, key)
                if not dry_run:
                    write_row(aiven, table, common, pk, l_row)
                stats["l_to_a"] += 1
                continue

            assert a_row is not None and l_row is not None
            if fingerprint(a_row, common) == fingerprint(l_row, common):
                continue

            winner, reason = choose_winner(table, a_row, l_row, aiven, local)
            if winner is None:
                stats["conflicts"] += 1
                log_conflict(table, key, reason)
                logger.warning("CONFLICT %s %s: %s (giu nguyen ca hai)", table, key, reason)
                continue

            if winner == "local":
                logger.debug("%s %s: Local -> Aiven (%s)", table, key, reason)
                if not dry_run:
                    write_row(aiven, table, common, pk, l_row)
                stats["l_to_a"] += 1
            else:
                logger.debug("%s %s: Aiven -> Local (%s)", table, key, reason)
                if not dry_run:
                    write_row(local, table, common, pk, a_row)
                stats["a_to_l"] += 1

        if not dry_run:
            aiven.commit()
            local.commit()
    except Exception:
        if not dry_run:
            aiven.rollback()
            local.rollback()
        raise

    return stats


def run_once(config: dict[str, dict[str, Any]], dry_run: bool, set_id_offset: bool) -> dict[str, int]:
    logger.info(
        "Bat dau dong bo: Aiven=%s/%s, Local=%s/%s%s",
        config["aiven"]["host"],
        config["aiven"]["database"],
        config["local"]["host"],
        config["local"]["database"],
        " (DRY-RUN)" if dry_run else "",
    )
    aiven = connect(config["aiven"])
    local = connect(config["local"])
    summary = {"a_to_l": 0, "l_to_a": 0, "conflicts": 0, "skipped": 0}
    try:
        with aiven.cursor() as cur:
            cur.execute("SET FOREIGN_KEY_CHECKS = 0")
        with local.cursor() as cur:
            cur.execute("SET FOREIGN_KEY_CHECKS = 0")
        aiven.commit()
        local.commit()

        a_tables = list_tables(aiven)
        l_tables = list_tables(local)
        tables = [table for table in TABLE_ORDER if table in a_tables and table in l_tables]
        extras = sorted((a_tables & l_tables) - set(TABLE_ORDER) - EXCLUDED_TABLES)
        tables.extend(extras)

        infos: dict[str, dict[str, Any]] = {}
        for table in tables:
            if table in EXCLUDED_TABLES:
                continue
            a_info = table_info(aiven, table)
            l_info = table_info(local, table)
            if not a_info["pk"] or not l_info["pk"]:
                logger.warning("Bo qua %s: thieu khoa chinh.", table)
                continue
            infos[table] = l_info
            stats = process_table(aiven, local, table, a_info, l_info, dry_run)
            for key in summary:
                summary[key] += stats[key]
            logger.info(
                "%s: Aiven->Local=%d Local->Aiven=%d conflicts=%d",
                table,
                stats["a_to_l"],
                stats["l_to_a"],
                stats["conflicts"],
            )

        if not dry_run:
            for label, conn in (("Aiven", aiven), ("Local", local)):
                with conn.cursor() as cur:
                    cur.execute(
                        "UPDATE `parking_slots` s "
                        "LEFT JOIN `parking_sessions` p ON p.id = s.current_session_id "
                        "SET s.current_session_id = NULL "
                        "WHERE s.current_session_id IS NOT NULL "
                        "AND (p.id IS NULL OR p.status <> 'OPEN' OR p.exit_time IS NOT NULL)"
                    )
                    cleared = cur.rowcount
                conn.commit()
                if cleared:
                    logger.info("Don slot rac %s: %d", label, cleared)

        if not dry_run and set_id_offset:
            ensure_local_id_offset(local, infos, dry_run=False)
            reset_aiven_auto_increment(aiven, infos)

        logger.info(
            "Ket thuc: Aiven->Local=%d Local->Aiven=%d conflicts=%d",
            summary["a_to_l"],
            summary["l_to_a"],
            summary["conflicts"],
        )
        return summary
    finally:
        try:
            with aiven.cursor() as cur:
                cur.execute("SET FOREIGN_KEY_CHECKS = 1")
            aiven.commit()
        except Exception:
            pass
        try:
            with local.cursor() as cur:
                cur.execute("SET FOREIGN_KEY_CHECKS = 1")
            local.commit()
        except Exception:
            pass
        aiven.close()
        local.close()


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Dong bo hai chieu Aiven <-> MySQL local")
    parser.add_argument("--once", action="store_true", help="Chay mot lan (mac dinh)")
    parser.add_argument("--loop", action="store_true", help="Chay lap lai moi --interval giay")
    parser.add_argument("--interval", type=int, default=300, help="Chu ky loop, mac dinh 300 giay")
    parser.add_argument("--dry-run", action="store_true", help="Chi doc va ghi log, khong ghi DB")
    parser.add_argument("--no-id-offset", action="store_true", help="Khong dat ID offset cho local")
    parser.add_argument("--verbose", action="store_true", help="Log chi tiet")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv or sys.argv[1:])
    configure_logging(args.verbose)
    env = load_env(ENV_FILE)
    try:
        config = build_config(env)
    except Exception as exc:
        logger.error("Cau hinh khong hop le: %s", exc)
        return 2

    try:
        loop_guard = None
        if args.loop:
            try:
                loop_guard = SingleInstance(LOOP_LOCK_FILE)
                loop_guard.__enter__()
            except RuntimeError as exc:
                logger.warning("%s", exc)
                return 0
        try:
            while True:
                try:
                    with SingleInstance(LOCK_FILE):
                        run_once(config, args.dry_run, not args.no_id_offset)
                except RuntimeError as exc:
                    logger.warning("%s", exc)
                    if not args.loop:
                        return 3
                except Exception as exc:
                    logger.exception("Dong bo that bai: %s", exc)
                    if not args.loop:
                        return 1
                if not args.loop or args.dry_run:
                    return 0
                time.sleep(max(30, args.interval))
        finally:
            if loop_guard is not None:
                loop_guard.__exit__(None, None, None)
    except RuntimeError as exc:
        logger.warning("%s", exc)
        return 0


if __name__ == "__main__":
    raise SystemExit(main())
