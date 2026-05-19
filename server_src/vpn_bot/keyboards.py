from aiogram.types import InlineKeyboardMarkup, InlineKeyboardButton
from config import PLANS, TOPUP_OPTIONS, YUKASSA_TOPUP_OPTIONS


def main_menu() -> InlineKeyboardMarkup:
    return InlineKeyboardMarkup(inline_keyboard=[
        [InlineKeyboardButton(text="💳 Купить подписку", callback_data="shop")],
        [InlineKeyboardButton(text="💰 Баланс",          callback_data="balance"),
         InlineKeyboardButton(text="➕ Пополнить",        callback_data="topup_menu")],
        [InlineKeyboardButton(text="📋 Мои подписки",     callback_data="my_subs")],
        [InlineKeyboardButton(text="👥 Реферальная программа", callback_data="referral")],
        [InlineKeyboardButton(text="💬 Обратная связь",   url="https://t.me/goonvpnadmin")],
    ])


def plans_keyboard() -> InlineKeyboardMarkup:
    rows = []
    for key, p in PLANS.items():
        rows.append([InlineKeyboardButton(
            text=f"{p['label']} — {p['coins']} монет",
            callback_data=f"buy_{key}",
        )])
    rows.append([InlineKeyboardButton(text="◀️ Назад", callback_data="main_menu")])
    return InlineKeyboardMarkup(inline_keyboard=rows)


def topup_method_keyboard() -> InlineKeyboardMarkup:
    return InlineKeyboardMarkup(inline_keyboard=[
        [InlineKeyboardButton(text="⭐ Telegram Stars",   callback_data="topup_stars")],
        [InlineKeyboardButton(text="💳 Оплата картой (ЮKassa)", callback_data="topup_yukassa")],
        [InlineKeyboardButton(text="◀️ Назад",            callback_data="main_menu")],
    ])


def topup_keyboard() -> InlineKeyboardMarkup:
    rows = []
    for opt in TOPUP_OPTIONS:
        rows.append([InlineKeyboardButton(
            text=f"{opt['coins']} монет за {opt['stars']} ⭐",
            callback_data=f"topup_{opt['coins']}_{opt['stars']}",
        )])
    rows.append([InlineKeyboardButton(text="◀️ Назад", callback_data="topup_menu")])
    return InlineKeyboardMarkup(inline_keyboard=rows)


def topup_yukassa_keyboard() -> InlineKeyboardMarkup:
    rows = []
    for opt in YUKASSA_TOPUP_OPTIONS:
        rows.append([InlineKeyboardButton(
            text=f"{opt['coins']} монет за {opt['rub']} ₽",
            callback_data=f"topupyk_{opt['coins']}",
        )])
    rows.append([InlineKeyboardButton(text="◀️ Назад", callback_data="topup_menu")])
    return InlineKeyboardMarkup(inline_keyboard=rows)


def confirm_buy_keyboard(plan_key: str, coins: int) -> InlineKeyboardMarkup:
    return InlineKeyboardMarkup(inline_keyboard=[
        [InlineKeyboardButton(text=f"✅ Купить за {coins} монет", callback_data=f"confirm_{plan_key}")],
        [InlineKeyboardButton(text="❌ Отмена", callback_data="shop")],
    ])


def my_subs_keyboard(subs: list) -> InlineKeyboardMarkup:
    rows = []
    for s in subs:
        plan = PLANS.get(s["plan_key"], {})
        label = plan.get("label", s["plan_key"])
        rows.append([
            InlineKeyboardButton(text=f"🔑 Ключ ({label})", callback_data=f"getkey_{s['id']}"),
            InlineKeyboardButton(text="🔄 Продлить",         callback_data=f"renew_{s['id']}"),
        ])
    rows.append([InlineKeyboardButton(text="🏠 Главное меню", callback_data="main_menu")])
    return InlineKeyboardMarkup(inline_keyboard=rows)


def renew_plan_keyboard(sub_id: int) -> InlineKeyboardMarkup:
    rows = []
    for key, p in PLANS.items():
        rows.append([InlineKeyboardButton(
            text=f"{p['label']} — {p['coins']} монет",
            callback_data=f"renewplan_{sub_id}_{key}",
        )])
    rows.append([InlineKeyboardButton(text="◀️ Назад", callback_data="my_subs")])
    return InlineKeyboardMarkup(inline_keyboard=rows)


def confirm_renew_keyboard(sub_id: int, plan_key: str, coins: int) -> InlineKeyboardMarkup:
    return InlineKeyboardMarkup(inline_keyboard=[
        [InlineKeyboardButton(
            text=f"✅ Продлить за {coins} монет",
            callback_data=f"confirmrenew_{sub_id}_{plan_key}",
        )],
        [InlineKeyboardButton(text="❌ Отмена", callback_data=f"renew_{sub_id}")],
    ])


def back_to_menu() -> InlineKeyboardMarkup:
    return InlineKeyboardMarkup(inline_keyboard=[
        [InlineKeyboardButton(text="🏠 Главное меню", callback_data="main_menu")],
    ])


def admin_keyboard() -> InlineKeyboardMarkup:
    return InlineKeyboardMarkup(inline_keyboard=[
        [InlineKeyboardButton(text="👥 Пользователи",   callback_data="admin_users")],
        [InlineKeyboardButton(text="📊 Статистика",      callback_data="admin_stats")],
        [InlineKeyboardButton(text="🔌 Список inbound",  callback_data="admin_inbounds")],
    ])
