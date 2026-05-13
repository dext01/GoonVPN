"""GoonVPN Telegram Bot — точка входа и все обработчики."""
import asyncio
import io
import logging
from datetime import datetime

import qrcode
from aiogram import Bot, Dispatcher, F, Router
from aiogram.filters import Command, CommandObject
from aiogram.fsm.storage.memory import MemoryStorage
from aiogram.types import (
    BotCommand,
    BufferedInputFile,
    CallbackQuery,
    KeyboardButton,
    LabeledPrice,
    Message,
    PreCheckoutQuery,
    ReplyKeyboardMarkup,
)

import database as db
import xui_client as xui
from config import (
    ADMIN_IDS, BOT_TOKEN, NOTIFY_DAYS_BEFORE,
    PLANS, REFERRAL_BONUS, REFERRAL_NEW_BONUS, TOPUP_OPTIONS,
)
from xui_client import INBOUND_ID
from keyboards import (
    admin_keyboard,
    back_to_menu,
    confirm_buy_keyboard,
    confirm_renew_keyboard,
    main_menu,
    my_subs_keyboard,
    plans_keyboard,
    renew_plan_keyboard,
    topup_keyboard,
)

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
log = logging.getLogger(__name__)

router = Router()


# ─── helpers ──────────────────────────────────────────────────────────────────

def menu_reply_kb() -> ReplyKeyboardMarkup:
    return ReplyKeyboardMarkup(
        keyboard=[[KeyboardButton(text="🏠 Меню")]],
        resize_keyboard=True,
        is_persistent=True,
    )


def is_admin(user_id: int) -> bool:
    return user_id in ADMIN_IDS


def fmt_date(iso: str) -> str:
    try:
        dt = datetime.fromisoformat(iso)
        return dt.strftime("%d.%m.%Y")
    except Exception:
        return iso


def make_qr_bytes(text: str) -> bytes:
    img = qrcode.make(text)
    buf = io.BytesIO()
    img.save(buf, format="PNG")
    return buf.getvalue()


async def ensure_user(message: Message) -> dict:
    u = message.from_user
    return await db.get_or_create_user(u.id, u.username, u.full_name)


async def send_key_message(bot: Bot, user_id: int, sub: dict):
    """Отправляет VLESS-ключ и QR-код отдельными сообщениями."""
    plan = PLANS.get(sub["plan_key"], {})
    await bot.send_message(
        user_id,
        f"🔑 <b>Ваш ключ ({plan.get('label', '')} · до {fmt_date(sub['expires_at'])}):</b>\n\n"
        f"<code>{sub['vless_key']}</code>\n\n"
        f"👆 Нажми чтобы скопировать, затем вставь в GoonVPN.",
        parse_mode="HTML",
    )
    try:
        qr_bytes = make_qr_bytes(sub["vless_key"])
        await bot.send_photo(
            chat_id=user_id,
            photo=BufferedInputFile(qr_bytes, filename="vpn_qr.png"),
            caption=f"📱 QR-код · {plan.get('label', '')} · до {fmt_date(sub['expires_at'])}",
        )
    except Exception as e:
        log.warning(f"QR send failed: {e}")


# ─── /start ───────────────────────────────────────────────────────────────────

@router.message(Command("start"))
async def cmd_start(message: Message, bot: Bot, command: CommandObject):
    user = await ensure_user(message)
    user_id = message.from_user.id

    # Обработка реферальной ссылки: /start ref_XXXXXXXX
    ref_arg = command.args or ""
    if ref_arg.startswith("ref_") and not user.get("referred_by"):
        ref_code = ref_arg[4:]
        referrer = await db.get_user_by_referral_code(ref_code)
        if referrer and referrer["id"] != user_id:
            await db.set_referred_by(user_id, referrer["id"])
            # Бонус новому пользователю
            await db.add_balance(user_id, REFERRAL_NEW_BONUS, "referral",
                                  f"Бонус за реферальную ссылку от {referrer['id']}")
            # Бонус рефереру
            await db.add_balance(referrer["id"], REFERRAL_BONUS, "referral",
                                  f"Реферал: новый пользователь {user_id}")
            try:
                await bot.send_message(
                    referrer["id"],
                    f"🎉 <b>Новый реферал!</b>\n\n"
                    f"По вашей ссылке зарегистрировался новый пользователь.\n"
                    f"Вам начислено <b>{REFERRAL_BONUS} монет</b>!",
                    parse_mode="HTML",
                )
            except Exception:
                pass
            await message.answer(
                f"🎁 Вам начислено <b>{REFERRAL_NEW_BONUS} монет</b> за использование реферальной ссылки!",
                parse_mode="HTML",
            )

    name = message.from_user.first_name or "Привет"
    # Сначала показываем постоянную кнопку «Меню» в нижней панели
    await message.answer(
        f"👋 <b>Привет, {name}!</b>\n\n"
        "🔐 <b>GoonVPN</b> — быстрый и надёжный VPN на базе VLESS/Reality.\n\n"
        "💰 <b>Как работает:</b>\n"
        "• Пополняй баланс монетами (за ⭐ или от админа)\n"
        "• Купи подписку — получишь VLESS-ключ и QR-код\n"
        "• Вставь ключ в приложение GoonVPN\n\n"
        "Кнопка <b>🏠 Меню</b> всегда доступна внизу экрана 👇",
        reply_markup=menu_reply_kb(),
        parse_mode="HTML",
    )
    # Отправляем основное инлайн-меню отдельным сообщением
    await message.answer("Выбери действие:", reply_markup=main_menu())


# ─── callback: main_menu ──────────────────────────────────────────────────────

@router.callback_query(F.data == "main_menu")
async def cb_main_menu(cb: CallbackQuery):
    await cb.message.edit_text(
        "🏠 <b>Главное меню</b>\n\nВыбери действие:",
        reply_markup=main_menu(),
        parse_mode="HTML",
    )


@router.message(F.text == "🏠 Меню")
async def msg_menu_button(message: Message):
    await ensure_user(message)
    await message.answer("🏠 <b>Главное меню</b>\n\nВыбери действие:", reply_markup=main_menu(), parse_mode="HTML")


# ─── balance ──────────────────────────────────────────────────────────────────

@router.message(Command("balance"))
@router.callback_query(F.data == "balance")
async def show_balance(event: Message | CallbackQuery):
    user_id = event.from_user.id
    balance = await db.get_balance(user_id)
    text = (
        f"💰 <b>Ваш баланс: {balance} монет</b>\n\n"
        f"Пополните баланс, чтобы купить подписку."
    )
    if isinstance(event, CallbackQuery):
        await event.message.edit_text(text, reply_markup=back_to_menu(), parse_mode="HTML")
    else:
        await event.answer(text, reply_markup=back_to_menu(), parse_mode="HTML")


# ─── пополнение через Stars ───────────────────────────────────────────────────

@router.message(Command("topup"))
@router.callback_query(F.data == "topup_menu")
async def topup_menu(event: Message | CallbackQuery):
    text = (
        "➕ <b>Пополнение баланса</b>\n\n"
        "Выбери сумму. Оплата через Telegram Stars ⭐"
    )
    if isinstance(event, CallbackQuery):
        await event.message.edit_text(text, reply_markup=topup_keyboard(), parse_mode="HTML")
    else:
        await event.answer(text, reply_markup=topup_keyboard(), parse_mode="HTML")


@router.callback_query(F.data.startswith("topup_") & ~F.data.startswith("topup_menu"))
async def cb_topup_option(cb: CallbackQuery, bot: Bot):
    _, coins_str, stars_str = cb.data.split("_")
    coins = int(coins_str)
    stars = int(stars_str)

    await bot.send_invoice(
        chat_id=cb.from_user.id,
        title=f"GoonVPN — {coins} монет",
        description=f"Пополнение баланса на {coins} монет для покупки VPN-подписки",
        payload=f"topup|{coins}",
        currency="XTR",
        prices=[LabeledPrice(label=f"{coins} монет", amount=stars)],
    )
    await cb.answer()


@router.pre_checkout_query()
async def pre_checkout(query: PreCheckoutQuery, bot: Bot):
    await bot.answer_pre_checkout_query(query.id, ok=True)


@router.message(F.successful_payment)
async def successful_payment(message: Message):
    payload = message.successful_payment.invoice_payload
    stars   = message.successful_payment.total_amount
    if payload.startswith("topup|"):
        coins = int(payload.split("|")[1])
        new_balance = await db.add_balance(
            message.from_user.id, coins, "stars",
            f"Пополнение {coins} монет за {stars} Stars",
        )
        await message.answer(
            f"✅ <b>Оплата получена!</b>\n\n"
            f"Начислено: <b>{coins} монет</b>\n"
            f"Новый баланс: <b>{new_balance} монет</b>",
            reply_markup=main_menu(),
            parse_mode="HTML",
        )


# ─── магазин / покупка подписки ───────────────────────────────────────────────

@router.message(Command("buy"))
@router.callback_query(F.data == "shop")
async def show_shop(event: Message | CallbackQuery):
    balance = await db.get_balance(event.from_user.id)
    text = (
        f"💳 <b>Покупка подписки</b>\n"
        f"Ваш баланс: <b>{balance} монет</b>\n\n"
        "Выбери тариф:"
    )
    if isinstance(event, CallbackQuery):
        await event.message.edit_text(text, reply_markup=plans_keyboard(), parse_mode="HTML")
    else:
        await event.answer(text, reply_markup=plans_keyboard(), parse_mode="HTML")


@router.callback_query(F.data.startswith("buy_"))
async def cb_buy_plan(cb: CallbackQuery):
    plan_key = cb.data[4:]
    plan = PLANS.get(plan_key)
    if not plan:
        await cb.answer("Тариф не найден", show_alert=True)
        return

    balance = await db.get_balance(cb.from_user.id)
    status = "✅ Достаточно монет" if balance >= plan["coins"] else f"❌ Не хватает {plan['coins'] - balance} монет"

    await cb.message.edit_text(
        f"💳 <b>{plan['label']}</b>\n\n"
        f"Стоимость: <b>{plan['coins']} монет</b>\n"
        f"Ваш баланс: <b>{balance} монет</b>\n"
        f"{status}\n\n"
        f"Подтвердить покупку?",
        reply_markup=confirm_buy_keyboard(plan_key, plan["coins"]),
        parse_mode="HTML",
    )


@router.callback_query(F.data.startswith("confirm_"))
async def cb_confirm_buy(cb: CallbackQuery, bot: Bot):
    plan_key = cb.data[8:]
    plan = PLANS.get(plan_key)
    if not plan:
        await cb.answer("Тариф не найден", show_alert=True)
        return

    user_id  = cb.from_user.id
    username = cb.from_user.username or str(user_id)

    ok, new_balance = await db.deduct_balance(
        user_id, plan["coins"], f"Подписка {plan['label']}"
    )
    if not ok:
        await cb.answer("❌ Недостаточно монет!", show_alert=True)
        return

    await cb.message.edit_text("⏳ Создаём ключ на сервере...", parse_mode="HTML")

    label  = f"goon_{username}"
    result = await asyncio.to_thread(xui.add_client, plan["days"], label)
    if not result:
        await db.add_balance(user_id, plan["coins"], "refund", "Возврат: ошибка создания ключа")
        await cb.message.edit_text(
            "❌ Ошибка при создании ключа. Монеты возвращены. Попробуй позже.",
            reply_markup=back_to_menu(),
        )
        return

    client_uuid = result["client_uuid"]
    expires_at  = result["expires_at"]

    vless_key = xui.build_vless_key(client_uuid, label)
    if not vless_key:
        await db.add_balance(user_id, plan["coins"], "refund", "Возврат: ошибка построения ключа")
        await cb.message.edit_text(
            "❌ Ошибка при формировании ключа. Монеты возвращены.",
            reply_markup=back_to_menu(),
        )
        return

    await db.save_subscription(
        user_id, client_uuid, INBOUND_ID, vless_key, plan_key, expires_at
    )

    await cb.message.edit_text(
        f"🎉 <b>Подписка активирована!</b>\n\n"
        f"📅 Тариф: <b>{plan['label']}</b>\n"
        f"⏳ Действует до: <b>{fmt_date(expires_at)}</b>\n"
        f"💰 Остаток: <b>{new_balance} монет</b>\n\n"
        f"Ключ и QR-код отправлены следующим сообщением 👇",
        reply_markup=back_to_menu(),
        parse_mode="HTML",
    )

    sub = {"vless_key": vless_key, "expires_at": expires_at, "plan_key": plan_key}
    await send_key_message(bot, user_id, sub)


# ─── мои подписки ─────────────────────────────────────────────────────────────

@router.message(Command("mysubs"))
@router.callback_query(F.data == "my_subs")
async def show_my_subs(event: Message | CallbackQuery):
    user_id = event.from_user.id
    subs = await db.get_active_subscriptions(user_id)

    if not subs:
        text = (
            "📋 <b>Активные подписки</b>\n\n"
            "У тебя пока нет активных подписок.\n"
            "Купи подписку в разделе «Купить подписку»."
        )
        kb = back_to_menu()
    else:
        lines = [f"📋 <b>Активные подписки ({len(subs)})</b>\n"]
        for i, s in enumerate(subs, 1):
            plan = PLANS.get(s["plan_key"], {})
            lines.append(
                f"<b>{i}. {plan.get('label', s['plan_key'])}</b>\n"
                f"   До: {fmt_date(s['expires_at'])}"
            )
        text = "\n".join(lines)
        kb = my_subs_keyboard(subs)

    if isinstance(event, CallbackQuery):
        await event.message.edit_text(text, reply_markup=kb, parse_mode="HTML")
    else:
        await event.answer(text, reply_markup=kb, parse_mode="HTML")


@router.callback_query(F.data.startswith("getkey_"))
async def cb_getkey_sub(cb: CallbackQuery, bot: Bot):
    sub_id = int(cb.data[7:])
    sub = await db.get_subscription_by_id(sub_id)
    if not sub or sub["user_id"] != cb.from_user.id:
        await cb.answer("Подписка не найдена", show_alert=True)
        return
    await cb.answer("Отправляю ключ...")
    await send_key_message(bot, cb.from_user.id, sub)


# ─── продление подписки ───────────────────────────────────────────────────────

@router.callback_query(F.data.startswith("renew_"))
async def cb_renew_sub(cb: CallbackQuery):
    sub_id = int(cb.data[6:])
    sub = await db.get_subscription_by_id(sub_id)
    if not sub or sub["user_id"] != cb.from_user.id:
        await cb.answer("Подписка не найдена", show_alert=True)
        return

    plan = PLANS.get(sub["plan_key"], {})
    balance = await db.get_balance(cb.from_user.id)
    await cb.message.edit_text(
        f"🔄 <b>Продление подписки</b>\n\n"
        f"Текущая: <b>{plan.get('label', sub['plan_key'])}</b> · до {fmt_date(sub['expires_at'])}\n"
        f"Ваш баланс: <b>{balance} монет</b>\n\n"
        f"Выбери срок продления:",
        reply_markup=renew_plan_keyboard(sub_id),
        parse_mode="HTML",
    )


@router.callback_query(F.data.startswith("renewplan_"))
async def cb_renewplan(cb: CallbackQuery):
    # renewplan_{sub_id}_{plan_key}
    parts = cb.data.split("_", 2)
    sub_id   = int(parts[1])
    plan_key = parts[2]
    plan = PLANS.get(plan_key)
    if not plan:
        await cb.answer("Тариф не найден", show_alert=True)
        return

    sub = await db.get_subscription_by_id(sub_id)
    if not sub or sub["user_id"] != cb.from_user.id:
        await cb.answer("Подписка не найдена", show_alert=True)
        return

    balance = await db.get_balance(cb.from_user.id)
    status = "✅ Достаточно монет" if balance >= plan["coins"] else f"❌ Не хватает {plan['coins'] - balance} монет"

    await cb.message.edit_text(
        f"🔄 <b>Продление на {plan['label']}</b>\n\n"
        f"Стоимость: <b>{plan['coins']} монет</b>\n"
        f"Ваш баланс: <b>{balance} монет</b>\n"
        f"{status}\n\n"
        f"Подтвердить?",
        reply_markup=confirm_renew_keyboard(sub_id, plan_key, plan["coins"]),
        parse_mode="HTML",
    )


@router.callback_query(F.data.startswith("confirmrenew_"))
async def cb_confirm_renew(cb: CallbackQuery):
    # confirmrenew_{sub_id}_{plan_key}
    parts    = cb.data.split("_", 2)
    sub_id   = int(parts[1])
    plan_key = parts[2]
    plan = PLANS.get(plan_key)
    if not plan:
        await cb.answer("Тариф не найден", show_alert=True)
        return

    sub = await db.get_subscription_by_id(sub_id)
    if not sub or sub["user_id"] != cb.from_user.id:
        await cb.answer("Подписка не найдена", show_alert=True)
        return

    user_id = cb.from_user.id
    ok, new_balance = await db.deduct_balance(
        user_id, plan["coins"], f"Продление {plan['label']}"
    )
    if not ok:
        await cb.answer("❌ Недостаточно монет!", show_alert=True)
        return

    await cb.message.edit_text("⏳ Продлеваем подписку...", parse_mode="HTML")

    result = await asyncio.to_thread(xui.extend_client, sub["client_uuid"], plan["days"])
    if not result:
        await db.add_balance(user_id, plan["coins"], "refund", "Возврат: ошибка продления")
        await cb.message.edit_text(
            "❌ Ошибка продления. Монеты возвращены.",
            reply_markup=back_to_menu(),
        )
        return

    new_expires = result["expires_at"]
    await db.update_subscription_expiry(sub_id, new_expires)

    await cb.message.edit_text(
        f"✅ <b>Подписка продлена!</b>\n\n"
        f"📅 Тариф: <b>{plan['label']}</b>\n"
        f"⏳ Новый срок до: <b>{fmt_date(new_expires)}</b>\n"
        f"💰 Остаток: <b>{new_balance} монет</b>",
        reply_markup=back_to_menu(),
        parse_mode="HTML",
    )


# ─── /getkey — повторно получить ключ ─────────────────────────────────────────

@router.message(Command("getkey"))
async def cmd_getkey(message: Message, bot: Bot):
    subs = await db.get_active_subscriptions(message.from_user.id)
    if not subs:
        await message.answer("У тебя нет активных подписок.", reply_markup=back_to_menu())
        return
    for sub in subs:
        await send_key_message(bot, message.from_user.id, sub)


# ─── Реферальная программа ────────────────────────────────────────────────────

@router.message(Command("ref"))
@router.callback_query(F.data == "referral")
async def show_referral(event: Message | CallbackQuery, bot: Bot):
    user_id = event.from_user.id
    code = await db.get_or_create_referral_code(user_id)
    count = await db.get_referral_count(user_id)
    bot_info = await bot.get_me()
    link = f"https://t.me/{bot_info.username}?start=ref_{code}"

    text = (
        f"👥 <b>Реферальная программа</b>\n\n"
        f"Приглашай друзей — получай монеты!\n\n"
        f"• За каждого друга: <b>+{REFERRAL_BONUS} монет</b>\n"
        f"• Другу за регистрацию: <b>+{REFERRAL_NEW_BONUS} монет</b>\n\n"
        f"Твоя ссылка:\n<code>{link}</code>\n\n"
        f"Приглашено: <b>{count} чел.</b>"
    )

    if isinstance(event, CallbackQuery):
        await event.message.edit_text(text, reply_markup=back_to_menu(), parse_mode="HTML")
    else:
        await event.answer(text, reply_markup=back_to_menu(), parse_mode="HTML")


# ─── ADMIN ────────────────────────────────────────────────────────────────────

@router.message(Command("admin"))
async def cmd_admin(message: Message):
    if not is_admin(message.from_user.id):
        return
    await message.answer("🛠 <b>Панель администратора</b>", reply_markup=admin_keyboard(), parse_mode="HTML")


@router.callback_query(F.data == "admin_stats")
async def cb_admin_stats(cb: CallbackQuery):
    if not is_admin(cb.from_user.id):
        return
    stats = await db.get_stats()
    await cb.message.edit_text(
        f"📊 <b>Статистика</b>\n\n"
        f"👥 Пользователей: <b>{stats['total_users']}</b>\n"
        f"🔐 Активных подписок: <b>{stats['active_subs']}</b>\n"
        f"⭐ Stars заработано (монет): <b>{stats['stars_total']}</b>",
        reply_markup=admin_keyboard(),
        parse_mode="HTML",
    )


@router.callback_query(F.data == "admin_users")
async def cb_admin_users(cb: CallbackQuery):
    if not is_admin(cb.from_user.id):
        return
    users = await db.get_all_users()
    if not users:
        await cb.answer("Нет пользователей", show_alert=True)
        return
    lines = [f"👥 <b>Пользователи ({len(users)})</b>\n"]
    for u in users[:30]:
        tag = f"@{u['username']}" if u["username"] else u["full_name"] or "—"
        lines.append(f"<code>{u['id']}</code> {tag} — <b>{u['balance']} монет</b>")
    await cb.message.edit_text(
        "\n".join(lines),
        reply_markup=admin_keyboard(),
        parse_mode="HTML",
    )


@router.callback_query(F.data == "admin_inbounds")
async def cb_admin_inbounds(cb: CallbackQuery):
    if not is_admin(cb.from_user.id):
        return
    await cb.answer("Загружаю...")
    info = await asyncio.to_thread(xui.get_inbound_info)
    await cb.message.edit_text(
        f"🔌 <b>Активный Inbound</b>\n\n"
        f"ID: <b>{info['id']}</b>\n"
        f"Протокол: <b>{info['protocol']}</b>\n"
        f"Порт: <b>{info['port']}</b>\n"
        f"SNI: <b>{info['sni']}</b>\n"
        f"Клиентов: <b>{info['clients']}</b>\n\n"
        f"Public Key:\n<code>{info['public_key']}</code>",
        reply_markup=admin_keyboard(),
        parse_mode="HTML",
    )


@router.message(Command("add_balance"))
async def cmd_add_balance(message: Message, command: CommandObject, bot: Bot):
    """
    /add_balance <user_id> <amount> [комментарий]
    """
    if not is_admin(message.from_user.id):
        return
    args = (command.args or "").split(maxsplit=2)
    if len(args) < 2:
        await message.answer("Использование: /add_balance <user_id> <монеты> [комментарий]")
        return
    try:
        target_id = int(args[0])
        amount    = int(args[1])
        comment   = args[2] if len(args) > 2 else "от администратора"
    except ValueError:
        await message.answer("❌ Неверный формат.")
        return

    user = await db.get_user(target_id)
    if not user:
        await message.answer(f"❌ Пользователь {target_id} не найден.")
        return

    new_balance = await db.add_balance(target_id, amount, "admin", comment)
    tag = f"@{user['username']}" if user["username"] else user["full_name"]
    await message.answer(
        f"✅ Начислено <b>{amount} монет</b> пользователю {tag}\n"
        f"Новый баланс: <b>{new_balance} монет</b>",
        parse_mode="HTML",
    )
    try:
        await bot.send_message(
            target_id,
            f"💰 <b>Пополнение баланса!</b>\n\n"
            f"Вам начислено <b>{amount} монет</b>\n"
            f"Новый баланс: <b>{new_balance} монет</b>\n"
            f"Комментарий: {comment}",
            parse_mode="HTML",
        )
    except Exception:
        pass


@router.message(Command("broadcast"))
async def cmd_broadcast(message: Message, command: CommandObject, bot: Bot):
    if not is_admin(message.from_user.id):
        return
    text = command.args or ""
    if not text:
        await message.answer("Использование: /broadcast <текст>")
        return

    users = await db.get_all_users()
    sent = failed = 0
    for u in users:
        try:
            await bot.send_message(u["id"], text, parse_mode="HTML")
            sent += 1
        except Exception:
            failed += 1
    await message.answer(f"✅ Рассылка завершена: {sent} отправлено, {failed} ошибок.")


# ─── Фоновые задачи ───────────────────────────────────────────────────────────

async def cleanup_loop(bot: Bot):
    """Каждый час деактивирует просроченные подписки и отключает клиентов в x-ui."""
    while True:
        try:
            expired = await db.deactivate_expired_subscriptions()
            for sub in expired:
                await asyncio.to_thread(xui.disable_client, sub["client_uuid"])
                log.info(f"Деактивирована подписка {sub['id']} (user {sub['user_id']})")
                try:
                    plan = PLANS.get(sub["plan_key"], {})
                    await bot.send_message(
                        sub["user_id"],
                        f"⚠️ <b>Подписка истекла</b>\n\n"
                        f"Ваша подписка «{plan.get('label', sub['plan_key'])}» закончилась.\n"
                        f"Купите новую в боте 👇",
                        reply_markup=main_menu(),
                        parse_mode="HTML",
                    )
                except Exception:
                    pass
        except Exception as e:
            log.error(f"cleanup_loop error: {e}")
        await asyncio.sleep(3600)


async def notify_loop(bot: Bot):
    """Каждый час шлёт предупреждение за N дней до истечения подписки."""
    while True:
        try:
            expiring = await db.get_expiring_subscriptions(NOTIFY_DAYS_BEFORE)
            for sub in expiring:
                plan = PLANS.get(sub["plan_key"], {})
                try:
                    await bot.send_message(
                        sub["user_id"],
                        f"⏰ <b>Подписка скоро истекает!</b>\n\n"
                        f"Тариф «{plan.get('label', sub['plan_key'])}» истекает <b>{fmt_date(sub['expires_at'])}</b>.\n"
                        f"Продли сейчас, чтобы не потерять доступ 👇",
                        reply_markup=main_menu(),
                        parse_mode="HTML",
                    )
                    await db.mark_subscription_notified(sub["id"])
                except Exception:
                    pass
        except Exception as e:
            log.error(f"notify_loop error: {e}")
        await asyncio.sleep(3600)


# ─── запуск ───────────────────────────────────────────────────────────────────

async def main():
    await db.init_db()
    bot = Bot(token=BOT_TOKEN)
    dp  = Dispatcher(storage=MemoryStorage())
    dp.include_router(router)

    async def on_startup(bot: Bot, **kwargs):
        await bot.set_my_commands([
            BotCommand(command="start",   description="Начать / главное меню"),
            BotCommand(command="balance", description="Мой баланс"),
            BotCommand(command="buy",     description="Купить подписку"),
            BotCommand(command="mysubs",  description="Мои подписки"),
            BotCommand(command="getkey",  description="Получить ключ повторно"),
            BotCommand(command="ref",     description="Реферальная программа"),
            BotCommand(command="topup",   description="Пополнить баланс"),
        ])
        asyncio.create_task(cleanup_loop(bot))
        asyncio.create_task(notify_loop(bot))

    dp.startup.register(on_startup)

    log.info("GoonVPN bot starting...")
    await dp.start_polling(bot, allowed_updates=dp.resolve_used_update_types())


if __name__ == "__main__":
    asyncio.run(main())

# bot version: 1.2
