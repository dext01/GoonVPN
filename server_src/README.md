# Сервер GoonVPN — документация

IP: `62.60.251.120`  
Пользователь: `root`

---

## Структура сервера

```
/root/
├── vpn_bot/                  — Telegram-бот
│   ├── bot.py                — основной файл, все обработчики
│   ├── config.py             — тарифы, переменные окружения
│   ├── database.py           — работа с SQLite (пользователи, подписки, баланс)
│   ├── keyboards.py          — inline-клавиатуры бота
│   ├── xui_client.py         — API-клиент к панели x-ui (создание/удаление клиентов)
│   ├── .env                  — секреты (токен бота, пароль панели и т.д.)
│   ├── requirements.txt      — зависимости Python
│   └── vpn_bot.db            — база данных SQLite
└── cert/                     — SSL-сертификаты (acme.sh)
```

---

## Telegram-бот

### Технологии
- Python 3.12
- aiogram 3.x — фреймворк для Telegram Bot API
- aiosqlite — асинхронная работа с SQLite
- qrcode — генерация QR-кодов для VLESS-ключей

### Запуск как systemd-сервис

Файл: `/etc/systemd/system/goonvpn-bot.service`

```bash
# Запустить
systemctl start goonvpn-bot

# Остановить
systemctl stop goonvpn-bot

# Перезапустить (после изменений в коде)
systemctl restart goonvpn-bot

# Статус
systemctl status goonvpn-bot

# Логи
journalctl -u goonvpn-bot -f
```

### Установка с нуля

```bash
cd /root
mkdir vpn_bot && cd vpn_bot
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt

# Создать .env файл (см. раздел ниже)
# Запустить сервис
systemctl enable goonvpn-bot
systemctl start goonvpn-bot
```

### Переменные окружения (.env)

| Переменная | Описание |
|------------|----------|
| `BOT_TOKEN` | Токен бота от @BotFather |
| `ADMIN_IDS` | Telegram ID администраторов (через запятую) |
| `PANEL_URL` | URL панели x-ui (например `http://localhost:54321`) |
| `PANEL_USER` | Логин панели x-ui |
| `PANEL_PASS` | Пароль панели x-ui |
| `INBOUND_ID` | ID inbound в x-ui (обычно `1`) |
| `DB_PATH` | Путь к файлу базы данных (по умолчанию `vpn_bot.db`) |

---

## База данных (SQLite)

Файл: `/root/vpn_bot/vpn_bot.db`

### Таблицы

**users** — пользователи бота
- `id` — Telegram ID
- `username`, `full_name`
- `balance` — баланс в монетах
- `referral_code` — уникальный реферальный код
- `referred_by` — ID того, кто пригласил

**subscriptions** — VPN-подписки
- `user_id` — Telegram ID пользователя
- `client_uuid` — UUID клиента в x-ui
- `inbound_id` — ID inbound в x-ui
- `vless_key` — готовый VLESS-ключ для вставки в приложение
- `plan_key` — тариф (`30d`, `90d`, `365d`)
- `expires_at` — дата истечения
- `is_active` — активна ли подписка
- `notified` — отправлено ли уведомление об истечении

**balance_log** — история операций с балансом
- `user_id`, `amount`, `type`, `comment`, `created_at`

### Просмотр базы вручную (на сервере)

```bash
cd /root/vpn_bot
sqlite3 vpn_bot.db

# Примеры запросов:
.tables
SELECT * FROM users;
SELECT * FROM subscriptions WHERE is_active = 1;
SELECT * FROM balance_log ORDER BY created_at DESC LIMIT 20;
```

---

## VPN-панель (x-ui / Marzban)

Панель управляет VLESS-клиентами через API.  
Бот при покупке подписки автоматически:
1. Создаёт клиента в x-ui через `xui_client.py`
2. Получает UUID и дату истечения
3. Строит VLESS-ключ и сохраняет в базу
4. Отправляет пользователю ключ + QR-код

При истечении подписки — бот автоматически отключает клиента в x-ui (каждый час проверяет).

### Тарифы

| Тариф | Дней | Монеты | Stars |
|-------|------|--------|-------|
| 30 дней | 30 | 150 | 120 ⭐ |
| 90 дней | 90 | 400 | 280 ⭐ |
| 365 дней | 365 | 1200 | 880 ⭐ |

### Пополнение баланса (Telegram Stars)

| Монеты | Stars |
|--------|-------|
| 150 | 120 ⭐ |
| 400 | 300 ⭐ |
| 1200 | 880 ⭐ |

---

## SSL-сертификаты

Используется acme.sh, автообновление через cron:
```
32 9 * * * /root/.acme.sh/acme.sh --cron --home /root/.acme.sh
```

---

## Полезные команды на сервере

```bash
# Логи бота в реальном времени
journalctl -u goonvpn-bot -f

# Перезапустить бота после изменений
systemctl restart goonvpn-bot

# Проверить работающие процессы
ps aux | grep python

# Подключиться к базе данных
sqlite3 /root/vpn_bot/vpn_bot.db

# Обновить код бота
cd /root/vpn_bot
# ... внести изменения ...
systemctl restart goonvpn-bot
```
