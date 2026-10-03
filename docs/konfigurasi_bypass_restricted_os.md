# Konfigurasi Notifikasi Persisten Non-Standar (Restricted OS Bypass)

Dokumen ini berisi konfigurasi dan arsitektur *non-standar industri* ("Survival Mode") untuk menjaga notifikasi tetap persisten dan mem-bypass pembantaian background process pada sistem operasi agresif (Custom ROM) seperti MIUI, One UI, ColorOS, dan sejenisnya.

---

## 1. Eksploitasi Celah `IMPORTANCE_MIN` (Hidden Foreground Channel)

Trik ini menyembunyikan *foreground service* dari pandangan langsung di *notification tray* tanpa memprovokasi pengguna untuk membersihkannya secara manual. Namun, status *Foreground Service* tetap terdaftar dan mendapatkan prioritas tinggi di sistem operasi.

```kotlin
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

fun createGhostNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channelId = "ghost_channel"
        val channelName = "System Core Engine"
        
        val channel = NotificationChannel(
            channelId,
            channelName,
            NotificationManager.IMPORTANCE_MIN // BUKAN LOW ATAU HIGH
        ).apply {
            setShowBadge(false) // Matikan dot merah pada ikon aplikasi
            lockscreenVisibility = Notification.VISIBILITY_SECRET // Sembunyikan total dari lockscreen
        }
        
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }
}
```

---

## 2. Trik Wakelock Berulang via `setAlarmClock()` (Anti-Doze Mode)

Mekanisme Doze Mode bawaan Android akan membekukan aktivitas CPU dan jaringan secara berkala. Untuk menembus pembatasan ini tanpa menggunakan `WorkManager` standar, kita mengeksploitasi `AlarmManager.setAlarmClock()`. Sistem operasi memperlakukan fungsi ini sebagai alarm fisik yang wajib membangunkan CPU terlepas dari status hemat daya.

```kotlin
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

fun scheduleStrictHeartbeatAlarm(context: Context) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = Intent(context, AlarmReceiver::class.java)
    
    val pendingIntent = PendingIntent.getBroadcast(
        context, 
        0, 
        intent, 
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    // Menyuntikkan alarm untuk memicu pengecekan ulang setiap 60 detik secara presisi
    val triggerTime = System.currentTimeMillis() + 60000
    val alarmInfo = AlarmManager.AlarmClockInfo(triggerTime, pendingIntent)

    alarmManager.setAlarmClock(alarmInfo, pendingIntent)
}
```

---

## 3. Pemaksaan Akses Pengaturan Otonom Vendor (Deep Linking)

Karena konfigurasi level kode tidak dapat melawan opsi *Battery Optimization* yang disetel keras oleh vendor Custom ROM, satu-satunya jalan keluar adalah membawa pengguna langsung ke menu pengaturan internal rahasia untuk mematikan pembatasan latar belakang.

```kotlin
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

fun bypassVendorBatteryRestriction(context: Context) {
    val manufacturer = Build.MANUFACTURER.lowercase()
    val intent = Intent()
    
    when {
        manufacturer.contains("xiaomi") -> {
            intent.component = ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            )
        }
        manufacturer.contains("samsung") -> {
            intent.component = ComponentName(
                "com.samsung.android.lool",
                "com.samsung.android.sm.ui.battery.BatteryActivity"
            )
        }
        manufacturer.contains("huawei") -> {
            intent.component = ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.optimize.process.ProtectActivity"
            )
        }
        else -> {
            // Fallback ke pengaturan optimasi baterai Android universal
            intent.action = Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
        }
    }
    
    try {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (e: Exception) {
        // Jika struktur paket deep link oem berubah, arahkan ke detail aplikasi standar
        val defaultIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(defaultIntent)
    }
}
```

---

## ⚠️ Konsekuensi & Risiko Teknis
1. **Pemberitahuan Sistem (Android Vitals):** Sistem operasi Android 13 ke atas akan melacak penggunaan CPU abnormal dan dapat secara otomatis memicu pop-up sistem kepada pengguna yang menyarankan untuk menonaktifkan aplikasi Anda secara paksa.
2. **Kuras Daya Baterai:** Penggunaan interupsi alarm jam fisik secara beruntun menghalangi perangkat masuk ke mode *Deep Sleep*, secara signifikan meningkatkan konsumsi daya.
3. **Kepatuhan Google Play Store:** Aplikasi dengan taktik *hidden channel* dan alarm intensif kemungkinan besar tidak akan lolos kurasi Google Play Store dan harus didistribusikan melalui skema APK internal atau *Enterprise Distribution*.
