import os
from dotenv import load_dotenv

load_dotenv()

BOT_TOKEN  = os.getenv("BOT_TOKEN")
ADMIN_IDS  = list(map(int, os.getenv("ADMIN_IDS", "762208194").split(",")))
PANEL_URL  = os.getenv("PANEL_URL", "").rstrip("/")
PANEL_USER = os.getenv("PANEL_USER", "")
PANEL_PASS = os.getenv("PANEL_PASS", "")
INBOUND_ID = int(os.getenv("INBOUND_ID", "1"))
DB_PATH    = os.getenv("DB_PATH", "vpn_bot.db")

YUKASSA_SHOP_ID = os.getenv("YUKASSA_SHOP_ID", "")
YUKASSA_SECRET  = os.getenv("YUKASSA_SECRET", "")

# Тарифы: ключ → {дни, монеты, stars, название}
PLANS = {
    "30d":  {"days": 30,  "coins": 150, "stars": 120, "label": "30 дней"},
    "90d":  {"days": 90,  "coins": 400, "stars": 280, "label": "90 дней"},
    "365d": {"days": 365, "coins": 1200, "stars": 880, "label": "365 дней"},
}

# Варианты пополнения через Telegram Stars
TOPUP_OPTIONS = [
    {"coins": 150,  "stars": 120},
    {"coins": 400,  "stars": 300},
    {"coins": 1200, "stars": 880},
]

# Варианты пополнения через ЮKassa (рубли)
YUKASSA_TOPUP_OPTIONS = [
    {"coins": 150,  "rub": "149.00"},
    {"coins": 400,  "rub": "349.00"},
    {"coins": 1200, "rub": "999.00"},
]

# Реферальная система
REFERRAL_BONUS     = 20   # монет рефереру за каждого нового пользователя
REFERRAL_NEW_BONUS = 10   # монет новому пользователю за использование реферальной ссылки

# Уведомления об истечении
NOTIFY_DAYS_BEFORE = 3    # за сколько дней предупреждать об истечении подписки
