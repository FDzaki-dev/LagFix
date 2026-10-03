package com.lagfix.fstrim

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * v105 (perintah user, konfigurasi_bypass_restricted_os.md bagian 3): buka halaman pengaturan
 * Autostart/baterai milik vendor (Xiaomi, Samsung, Huawei) supaya user bisa mengizinkan LagFix
 * berjalan di latar belakang. Nama activity vendor = dari dokumen user, BELUM diverifikasi di
 * perangkat mana pun; bisa berubah antar versi ROM, makanya semua pemanggilan dibungkus
 * runCatching dengan fallback ke Info Aplikasi standar. Merek lain (termasuk Infinix/Tecno)
 * memakai daftar pengecualian optimasi baterai bawaan Android. 0 izin baru, 0 perubahan perilaku
 * otomatis: hanya jalan saat user mengetuk tombolnya.
 */
internal fun vendorBatteryIntent(): Intent {
    val manufacturer = Build.MANUFACTURER.lowercase()
    val intent = Intent()
    when {
        manufacturer.contains("xiaomi") -> intent.component = ComponentName(
            "com.miui.securitycenter",
            "com.miui.permcenter.autostart.AutoStartManagementActivity"
        )
        manufacturer.contains("samsung") -> intent.component = ComponentName(
            "com.samsung.android.lool",
            "com.samsung.android.sm.ui.battery.BatteryActivity"
        )
        manufacturer.contains("huawei") -> intent.component = ComponentName(
            "com.huawei.systemmanager",
            "com.huawei.systemmanager.optimize.process.ProtectActivity"
        )
        else -> intent.action = Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
    }
    return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

/** Buka pengaturan vendor; gagal (activity tak ada / struktur paket berubah) -> Info Aplikasi standar. */
internal fun openVendorBatterySettings(ctx: Context) {
    val opened = runCatching { ctx.startActivity(vendorBatteryIntent()) }.isSuccess
    if (!opened) {
        runCatching {
            ctx.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
