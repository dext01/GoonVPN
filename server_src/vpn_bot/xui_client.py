"""
Клиент для 3X-UI — работает напрямую с SQLite БД.
Не требует авторизации через web API.
"""
import json
import sqlite3
import subprocess
import uuid
from datetime import datetime, timedelta

DB_PATH     = "/etc/x-ui/x-ui.db"
INBOUND_ID  = 3          # vless:443 — из БД
SERVER_HOST = "62.60.251.120"


def _get_inbound() -> dict:
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    cur = conn.execute("SELECT * FROM inbounds WHERE id = ?", (INBOUND_ID,))
    row = cur.fetchone()
    conn.close()
    return dict(row) if row else {}


def _get_reality_params() -> dict:
    ib = _get_inbound()
    stream = json.loads(ib.get("stream_settings", "{}"))
    reality = stream.get("realitySettings", {})
    settings = reality.get("settings", {})
    return {
        "port":        ib.get("port", 443),
        "public_key":  settings.get("publicKey", ""),
        "sni":         (reality.get("serverNames") or [SERVER_HOST])[0],
        "short_id":    (reality.get("shortIds") or [""])[0],
        "fingerprint": settings.get("fingerprint", "chrome"),
        "network":     stream.get("network", "tcp"),
    }


def add_client(days: int, label: str) -> dict:
    """
    Добавляет нового клиента в inbound.
    Возвращает {"client_uuid": str, "expires_at": str ISO} или {}.
    """
    client_uuid = str(uuid.uuid4())
    expires_dt  = datetime.utcnow() + timedelta(days=days)
    expires_ms  = int(expires_dt.timestamp() * 1000)
    expires_iso = expires_dt.isoformat()
    # email = первые 12 символов UUID — уникален, нет конфликтов между юзерами
    email = client_uuid[:12]

    conn = sqlite3.connect(DB_PATH)
    try:
        row = conn.execute(
            "SELECT settings FROM inbounds WHERE id = ?", (INBOUND_ID,)
        ).fetchone()
        if not row:
            return {}

        settings_json = json.loads(row[0])
        clients = settings_json.get("clients", [])

        new_client = {
            "comment":    label,
            "created_at": int(datetime.utcnow().timestamp() * 1000),
            "email":      email,
            "enable":     True,
            "expiryTime": expires_ms,
            "flow":       "",
            "id":         client_uuid,
            "limitIp":    3,
            "rateLimit": {
                "downMbps": 15,
                "upMbps":   15,
            },
            "reset":      0,
            "subId":      uuid.uuid4().hex[:16],
            "tgId":       0,
            "totalGB":    0,
            "updated_at": int(datetime.utcnow().timestamp() * 1000),
        }
        clients.append(new_client)
        settings_json["clients"] = clients

        conn.execute(
            "UPDATE inbounds SET settings = ? WHERE id = ?",
            (json.dumps(settings_json), INBOUND_ID),
        )
        conn.execute(
            """INSERT OR IGNORE INTO client_traffics
               (inbound_id, enable, email, up, down, total, expiry_time, reset)
               VALUES (?, 1, ?, 0, 0, 0, ?, 0)""",
            (INBOUND_ID, email, expires_ms),
        )
        conn.commit()
    except Exception:
        conn.close()
        return {}
    conn.close()

    _reload_xray()
    return {"client_uuid": client_uuid, "expires_at": expires_iso}


def extend_client(client_uuid: str, additional_days: int) -> dict:
    """
    Продлевает подписку клиента на additional_days дней.
    Если подписка уже истекла — считает от текущего момента.
    Возвращает {"expires_at": str ISO} или {}.
    """
    conn = sqlite3.connect(DB_PATH)
    try:
        row = conn.execute(
            "SELECT settings FROM inbounds WHERE id = ?", (INBOUND_ID,)
        ).fetchone()
        if not row:
            return {}

        settings_json = json.loads(row[0])
        clients = settings_json.get("clients", [])

        now_ms  = int(datetime.utcnow().timestamp() * 1000)
        add_ms  = additional_days * 24 * 3600 * 1000
        new_expiry_ms  = 0
        new_expiry_iso = ""

        found = False
        for cl in clients:
            if cl.get("id") == client_uuid:
                current = cl.get("expiryTime", now_ms)
                base = max(current, now_ms)   # если истёк — продлеваем от сегодня
                new_expiry_ms = base + add_ms
                cl["expiryTime"] = new_expiry_ms
                cl["updated_at"] = now_ms
                cl["enable"] = True
                new_expiry_iso = datetime.utcfromtimestamp(new_expiry_ms / 1000).isoformat()
                found = True
                break

        if not found:
            conn.close()
            return {}

        settings_json["clients"] = clients
        conn.execute(
            "UPDATE inbounds SET settings = ? WHERE id = ?",
            (json.dumps(settings_json), INBOUND_ID),
        )
        # Обновляем трафик-запись (ищем по email = client_uuid[:12])
        conn.execute(
            """UPDATE client_traffics
               SET expiry_time=?, enable=1
               WHERE inbound_id=? AND email=?""",
            (new_expiry_ms, INBOUND_ID, client_uuid[:12]),
        )
        conn.commit()
    except Exception:
        conn.close()
        return {}
    conn.close()

    _reload_xray()
    return {"expires_at": new_expiry_iso}


def disable_client(client_uuid: str) -> bool:
    """Отключает клиента (enable=False) без удаления — для просроченных подписок."""
    conn = sqlite3.connect(DB_PATH)
    try:
        row = conn.execute(
            "SELECT settings FROM inbounds WHERE id = ?", (INBOUND_ID,)
        ).fetchone()
        if not row:
            conn.close()
            return False

        settings_json = json.loads(row[0])
        found = False
        for cl in settings_json.get("clients", []):
            if cl.get("id") == client_uuid:
                cl["enable"] = False
                found = True
                break

        if not found:
            conn.close()
            return False

        conn.execute(
            "UPDATE inbounds SET settings = ? WHERE id = ?",
            (json.dumps(settings_json), INBOUND_ID),
        )
        conn.execute(
            "UPDATE client_traffics SET enable=0 WHERE inbound_id=? AND email=?",
            (INBOUND_ID, client_uuid[:12]),
        )
        conn.commit()
    except Exception:
        conn.close()
        return False
    conn.close()
    _reload_xray()
    return True


def build_vless_key(client_uuid: str, label: str) -> str:
    p = _get_reality_params()
    if not p.get("public_key"):
        return ""
    return (
        f"vless://{client_uuid}@{SERVER_HOST}:{p['port']}"
        f"?type={p['network']}&security=reality"
        f"&pbk={p['public_key']}"
        f"&fp={p['fingerprint']}"
        f"&sni={p['sni']}"
        f"&sid={p['short_id']}"
        f"&spx=%2F"
        f"#{label}"
    )


def delete_client(client_uuid: str) -> bool:
    conn = sqlite3.connect(DB_PATH)
    try:
        row = conn.execute(
            "SELECT settings FROM inbounds WHERE id = ?", (INBOUND_ID,)
        ).fetchone()
        if not row:
            conn.close()
            return False

        settings_json = json.loads(row[0])
        before = len(settings_json.get("clients", []))
        settings_json["clients"] = [
            cl for cl in settings_json.get("clients", [])
            if cl.get("id") != client_uuid
        ]
        after = len(settings_json["clients"])
        if before == after:
            conn.close()
            return False

        conn.execute(
            "UPDATE inbounds SET settings = ? WHERE id = ?",
            (json.dumps(settings_json), INBOUND_ID),
        )
        conn.commit()
    except Exception:
        conn.close()
        return False
    conn.close()
    _reload_xray()
    return True


def get_inbound_info() -> dict:
    p = _get_reality_params()
    ib = _get_inbound()
    settings = json.loads(ib.get("settings", "{}"))
    return {
        "id":         INBOUND_ID,
        "port":       p["port"],
        "protocol":   ib.get("protocol", "vless"),
        "sni":        p["sni"],
        "public_key": p["public_key"],
        "clients":    len(settings.get("clients", [])),
    }


def _reload_xray():
    try:
        subprocess.run(
            ["systemctl", "restart", "x-ui"],
            timeout=15, check=False, capture_output=True
        )
    except Exception:
        pass
