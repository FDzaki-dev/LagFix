# Standar Industri Konfigurasi Notifikasi Persisten Android

Dokumen ini berisi konfigurasi lengkap, arsitektur, dan kode implementasi berstandar industri untuk membuat *Persistent Notification* menggunakan **Foreground Service** pada platform Android, diperbarui hingga penanganan **Android 13 (API 33)** dan **Android 14+ (API 34)**.

---

## 1. Deklarasi Manifest (`AndroidManifest.xml`)
Sejak Android 14, Anda wajib mendeklarasikan jenis *Foreground Service* secara spesifik (`foregroundServiceType`) sesuai dengan kegunaan aplikasi Anda.

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.company.persistentnotification">

    <!-- 1. Izin Dasar Notifikasi & Foreground Service -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" /> <!-- Wajib untuk Android 13+ -->

    <!-- 2. Izin Spesifik Tipe Service (Contoh: Sinkronisasi Data) -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />

    <application ...>
        
        <!-- 3. Deklarasi Service dengan Tipe Spesifik (Standar Android 14+) -->
        <service
            android:name=".CorePersistentService"
            android:enabled="true"
            android:exported="false"
            android:foregroundServiceType="dataSync" />
            
    </application>
</manifest>
```

---

## 2. Implementasi Service (`CorePersistentService.kt`)
Komponen utama yang menangani *Foreground Service* dan memanifestasikan notifikasi yang tidak dapat dihapus (*swipe-dismiss*).

```kotlin
package com.company.persistentnotification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class CorePersistentService : Service() {

    companion object {
        const val CHANNEL_ID = "persistent_core_channel"
        const val NOTIFICATION_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildPersistentNotification()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // Android 14+
            startForeground(
                NOTIFICATION_ID, 
                notification, 
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // Menjamin OS akan mencoba merestart service jika terbunuh karena keterbatasan RAM
        return START_STICKY 
    }

    private fun buildPersistentNotification(): Notification {
        val mainIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, mainIntent, 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Sistem Sinkronisasi Aktif")
            .setContentText("Mengamankan koneksi background data...")
            .setSmallIcon(R.drawable.ic_notification_secure) 
            .setContentIntent(pendingIntent)
            
            // --- KONFIGURASI PERSISTENT STANDAR INDUSTRI ---
            .setOngoing(true) // Mengunci notifikasi agar tidak bisa di-swipe dismiss
            .setAutoCancel(false) // Mencegah terhapus otomatis saat di-klik
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW) // Tidak mengganggu layar user secara pop-up
            .setOnlyAlertOnce(true) // Mencegah getar/suara berulang saat konten diupdate
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Layanan Latar Belakang Utama",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Saluran ini digunakan untuk menjaga kestabilan sinkronisasi aplikasi di latar belakang."
                setShowBadge(false) // Matikan badge dot merah pada ikon aplikasi
            }
            
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
```

---

## 3. Alur Kontrol UI (`MainActivity.kt`)
Memastikan penanganan izin runtime (`POST_NOTIFICATIONS`) dilakukan dengan benar sebelum menjalankan layanan.

```kotlin
package com.company.persistentnotification

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startCoreService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkAndStartService()
    }

    private fun checkAndStartService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13+
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == 
                PackageManager.PERMISSION_GRANTED) {
                startCoreService()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            startCoreService()
        }
    }

    private fun startCoreService() {
        val serviceIntent = Intent(this, CorePersistentService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun stopCoreService() {
        val serviceIntent = Intent(this, CorePersistentService::class.java)
        stopService(serviceIntent)
    }
}
```

---

## 4. Aturan Emas & Kebijakan Google Play Store
1. **Gunakan `setOnlyAlertOnce(true)`**: Menghindari keluhan pengguna akibat interupsi suara/getar terus menerus saat data diperbarui.
2. **Sesuaikan `IMPORTANCE_LOW`**: Jangan gunakan level `HIGH` kecuali untuk aplikasi navigasi real-time, panggilan VoIP, atau pemutar musik aktif.
3. **Sediakan Mekanisme Pemberhentian**: Selalu sediakan kontrol bagi pengguna untuk mematikan layanan langsung dari UI aplikasi atau melalui tombol aksi di dalam notifikasi.