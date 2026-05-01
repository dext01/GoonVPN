import secrets
import aiosqlite
from datetime import datetime, timedelta
from config import DB_PATH


async def init_db():
    async with aiosqlite.connect(DB_PATH) as db:
        await db.executescript("""
            CREATE TABLE IF NOT EXISTS users (
                id             INTEGER PRIMARY KEY,
                username       TEXT,
                full_name      TEXT,
                balance        INTEGER NOT NULL DEFAULT 0,
                referral_code  TEXT    UNIQUE,
                referred_by    INTEGER,
                created_at     TEXT    NOT NULL DEFAULT (datetime('now'))
            );

            CREATE TABLE IF NOT EXISTS subscriptions (
                id          INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id     INTEGER NOT NULL,
                client_uuid TEXT    NOT NULL,
                inbound_id  INTEGER NOT NULL,
                vless_key   TEXT    NOT NULL,
                plan_key    TEXT    NOT NULL,
                expires_at  TEXT    NOT NULL,
                is_active   INTEGER NOT NULL DEFAULT 1,
                notified    INTEGER NOT NULL DEFAULT 0,
                created_at  TEXT    NOT NULL DEFAULT (datetime('now'))
            );

            CREATE TABLE IF NOT EXISTS transactions (
                id         INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id    INTEGER NOT NULL,
                amount     INTEGER NOT NULL,
                type       TEXT    NOT NULL,
                comment    TEXT,
                created_at TEXT    NOT NULL DEFAULT (datetime('now'))
            );
        """)
        await db.commit()
    await _migrate()


async def _migrate():
    """Добавляем новые колонки если их ещё нет (для существующих БД)."""
    migrations = [
        "ALTER TABLE users ADD COLUMN referral_code TEXT",
        "ALTER TABLE users ADD COLUMN referred_by INTEGER",
        "ALTER TABLE subscriptions ADD COLUMN notified INTEGER NOT NULL DEFAULT 0",
    ]
    async with aiosqlite.connect(DB_PATH) as db:
        for sql in migrations:
            try:
                await db.execute(sql)
            except Exception:
                pass
        await db.commit()


async def _row_to_dict(row, cursor):
    if row is None:
        return None
    cols = [d[0] for d in cursor.description]
    return dict(zip(cols, row))


async def get_or_create_user(user_id: int, username: str, full_name: str) -> dict:
    async with aiosqlite.connect(DB_PATH) as db:
        await db.execute(
            "INSERT OR IGNORE INTO users (id, username, full_name) VALUES (?, ?, ?)",
            (user_id, username or "", full_name or ""),
        )
        await db.execute(
            "UPDATE users SET username=?, full_name=? WHERE id=?",
            (username or "", full_name or "", user_id),
        )
        await db.commit()
        async with db.execute("SELECT * FROM users WHERE id=?", (user_id,)) as cur:
            row = await cur.fetchone()
            return await _row_to_dict(row, cur)


async def get_user(user_id: int) -> dict | None:
    async with aiosqlite.connect(DB_PATH) as db:
        async with db.execute("SELECT * FROM users WHERE id=?", (user_id,)) as cur:
            row = await cur.fetchone()
            return await _row_to_dict(row, cur)


async def get_balance(user_id: int) -> int:
    async with aiosqlite.connect(DB_PATH) as db:
        async with db.execute("SELECT balance FROM users WHERE id=?", (user_id,)) as cur:
            row = await cur.fetchone()
            return row[0] if row else 0


async def add_balance(user_id: int, amount: int, type_: str, comment: str = "") -> int:
    async with aiosqlite.connect(DB_PATH) as db:
        await db.execute("UPDATE users SET balance = balance + ? WHERE id=?", (amount, user_id))
        await db.execute(
            "INSERT INTO transactions (user_id, amount, type, comment) VALUES (?,?,?,?)",
            (user_id, amount, type_, comment),
        )
        await db.commit()
        async with db.execute("SELECT balance FROM users WHERE id=?", (user_id,)) as cur:
            row = await cur.fetchone()
            return row[0] if row else 0


async def deduct_balance(user_id: int, amount: int, comment: str = "") -> tuple[bool, int]:
    async with aiosqlite.connect(DB_PATH) as db:
        async with db.execute("SELECT balance FROM users WHERE id=?", (user_id,)) as cur:
            row = await cur.fetchone()
        if not row or row[0] < amount:
            return False, row[0] if row else 0
        await db.execute("UPDATE users SET balance = balance - ? WHERE id=?", (amount, user_id))
        await db.execute(
            "INSERT INTO transactions (user_id, amount, type, comment) VALUES (?,?,?,?)",
            (user_id, -amount, "purchase", comment),
        )
        await db.commit()
        async with db.execute("SELECT balance FROM users WHERE id=?", (user_id,)) as cur:
            row = await cur.fetchone()
        return True, row[0] if row else 0


async def save_subscription(
    user_id: int, client_uuid: str, inbound_id: int,
    vless_key: str, plan_key: str, expires_at: str
) -> int:
    async with aiosqlite.connect(DB_PATH) as db:
        cur = await db.execute(
            """INSERT INTO subscriptions
               (user_id, client_uuid, inbound_id, vless_key, plan_key, expires_at)
               VALUES (?,?,?,?,?,?)""",
            (user_id, client_uuid, inbound_id, vless_key, plan_key, expires_at),
        )
        await db.commit()
        return cur.lastrowid


async def update_subscription_expiry(sub_id: int, new_expires_at: str):
    async with aiosqlite.connect(DB_PATH) as db:
        await db.execute(
            "UPDATE subscriptions SET expires_at=?, notified=0 WHERE id=?",
            (new_expires_at, sub_id),
        )
        await db.commit()


async def get_user_subscriptions(user_id: int) -> list[dict]:
    async with aiosqlite.connect(DB_PATH) as db:
        async with db.execute(
            "SELECT * FROM subscriptions WHERE user_id=? ORDER BY created_at DESC",
            (user_id,),
        ) as cur:
            rows = await cur.fetchall()
            return [await _row_to_dict(r, cur) for r in rows]


async def get_active_subscriptions(user_id: int) -> list[dict]:
    async with aiosqlite.connect(DB_PATH) as db:
        now = datetime.utcnow().isoformat()
        async with db.execute(
            """SELECT * FROM subscriptions
               WHERE user_id=? AND is_active=1 AND expires_at > ?
               ORDER BY expires_at ASC""",
            (user_id, now),
        ) as cur:
            rows = await cur.fetchall()
            return [await _row_to_dict(r, cur) for r in rows]


async def get_subscription_by_id(sub_id: int) -> dict | None:
    async with aiosqlite.connect(DB_PATH) as db:
        async with db.execute("SELECT * FROM subscriptions WHERE id=?", (sub_id,)) as cur:
            row = await cur.fetchone()
            return await _row_to_dict(row, cur)


async def get_expiring_subscriptions(days: int) -> list[dict]:
    """Активные подписки, которые истекают через <=days дней и ещё не уведомлены."""
    async with aiosqlite.connect(DB_PATH) as db:
        now = datetime.utcnow()
        deadline = (now + timedelta(days=days)).isoformat()
        now_iso = now.isoformat()
        async with db.execute(
            """SELECT * FROM subscriptions
               WHERE is_active=1 AND notified=0
                 AND expires_at > ? AND expires_at <= ?""",
            (now_iso, deadline),
        ) as cur:
            rows = await cur.fetchall()
            return [await _row_to_dict(r, cur) for r in rows]


async def mark_subscription_notified(sub_id: int):
    async with aiosqlite.connect(DB_PATH) as db:
        await db.execute("UPDATE subscriptions SET notified=1 WHERE id=?", (sub_id,))
        await db.commit()


async def deactivate_expired_subscriptions() -> list[dict]:
    """Деактивирует просроченные подписки, возвращает список деактивированных."""
    async with aiosqlite.connect(DB_PATH) as db:
        now = datetime.utcnow().isoformat()
        async with db.execute(
            "SELECT * FROM subscriptions WHERE is_active=1 AND expires_at <= ?", (now,)
        ) as cur:
            rows = await cur.fetchall()
            expired = [await _row_to_dict(r, cur) for r in rows]
        if expired:
            ids = [s["id"] for s in expired]
            await db.execute(
                f"UPDATE subscriptions SET is_active=0 WHERE id IN ({','.join('?'*len(ids))})",
                ids,
            )
            await db.commit()
        return expired


# ─── Реферальная система ──────────────────────────────────────────────────────

async def get_or_create_referral_code(user_id: int) -> str:
    async with aiosqlite.connect(DB_PATH) as db:
        async with db.execute("SELECT referral_code FROM users WHERE id=?", (user_id,)) as cur:
            row = await cur.fetchone()
        if row and row[0]:
            return row[0]
        # Генерируем уникальный код
        while True:
            code = secrets.token_hex(4).upper()  # 8 символов
            async with db.execute("SELECT id FROM users WHERE referral_code=?", (code,)) as cur:
                exists = await cur.fetchone()
            if not exists:
                break
        await db.execute("UPDATE users SET referral_code=? WHERE id=?", (code, user_id))
        await db.commit()
        return code


async def get_user_by_referral_code(code: str) -> dict | None:
    async with aiosqlite.connect(DB_PATH) as db:
        async with db.execute("SELECT * FROM users WHERE referral_code=?", (code,)) as cur:
            row = await cur.fetchone()
            return await _row_to_dict(row, cur)


async def set_referred_by(user_id: int, referrer_id: int):
    async with aiosqlite.connect(DB_PATH) as db:
        await db.execute(
            "UPDATE users SET referred_by=? WHERE id=? AND referred_by IS NULL",
            (referrer_id, user_id),
        )
        await db.commit()


async def get_referral_count(user_id: int) -> int:
    async with aiosqlite.connect(DB_PATH) as db:
        async with db.execute(
            "SELECT COUNT(*) FROM users WHERE referred_by=?", (user_id,)
        ) as cur:
            row = await cur.fetchone()
            return row[0] if row else 0


# ─── Статистика ───────────────────────────────────────────────────────────────

async def get_all_users() -> list[dict]:
    async with aiosqlite.connect(DB_PATH) as db:
        async with db.execute(
            "SELECT id, username, full_name, balance, created_at FROM users ORDER BY created_at DESC"
        ) as cur:
            rows = await cur.fetchall()
            return [await _row_to_dict(r, cur) for r in rows]


async def get_stats() -> dict:
    async with aiosqlite.connect(DB_PATH) as db:
        async with db.execute("SELECT COUNT(*) FROM users") as cur:
            total_users = (await cur.fetchone())[0]
        async with db.execute(
            "SELECT COUNT(*) FROM subscriptions WHERE is_active=1 AND expires_at > datetime('now')"
        ) as cur:
            active_subs = (await cur.fetchone())[0]
        async with db.execute("SELECT SUM(amount) FROM transactions WHERE type='stars'") as cur:
            stars_total = (await cur.fetchone())[0] or 0
        return {
            "total_users": total_users,
            "active_subs": active_subs,
            "stars_total": stars_total,
        }
