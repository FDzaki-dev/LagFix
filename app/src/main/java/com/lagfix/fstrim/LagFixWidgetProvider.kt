package com.lagfix.fstrim

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Widget home screen (fitur baru, prioritas user atas widget+QS tile). Status ditampilkan sbg
 * ringkasan ramah (v23: "Bersih ✓ · dd/MM HH:mm" dst — BUKAN lagi baris `Prefs.log` mentah "dd/MM
 * HH:mm OK/FAIL <ms>ms exit=0 <output shell>", yg dilaporkan user "kelihatan teknis banget").
 * Detail teknis (durasi ms, exit code, output shell mentah) TETAP ada, hanya di Riwayat dalam app
 * (MainActivity, tidak diubah) — widget cuma utk sekilas lihat, bukan debugging. + tombol
 * "Jalankan Sekarang". Tombol memicu `Scheduler.runOnce()` (TrimWorker via WorkManager) — jalur
 * yang sama persis dengan run manual di MainViewModel & jadwal otomatis, 0 logic Shizuku baru
 * ditulis. Widget tidak bisa menunggu hasil fstrim secara sinkron (broadcast onReceive tidak boleh
 * blocking lama), jadi setelah tombol ditekan status ditampilkan "Sedang memproses…" dulu; hasil
 * riil didorong balik segera stlh selesai lewat `Scheduler.notifyChanged()` (lihat TrimWorker.kt).
 */
class LagFixWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { updateWidget(context, manager, it, running = false) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_RUN_NOW) {
            Scheduler.runOnce(context)
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, LagFixWidgetProvider::class.java))
            ids.forEach { updateWidget(context, manager, it, running = true) }
        }
    }

    private fun updateWidget(context: Context, manager: AppWidgetManager, id: Int, running: Boolean) {
        val status = if (running) context.getString(R.string.widget_status_running) else friendlyStatus(context)
        val views = RemoteViews(context.packageName, R.layout.widget_lagfix).apply {
            setTextViewText(R.id.widget_title, context.getString(R.string.app_name))
            setTextViewText(R.id.widget_status, status)
            setTextViewText(R.id.widget_button, context.getString(R.string.widget_button_run))
            setOnClickPendingIntent(R.id.widget_button, runNowPendingIntent(context))
            setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent(context))
        }
        manager.updateAppWidget(id, views)
    }

    // v23: sumber data TETAP Prefs.lastRunMs/lastOk/log yg sudah ada (0 perubahan skema
    // penyimpanan) — cuma cara TAMPILKANNYA di widget yg dibikin ramah. Deteksi "dilewati" pakai
    // teknik yg SAMA persis dgn yg sudah dipakai MainActivity.kt baris ~276 (cek prefix log),
    // konsisten dgn konvensi existing, bukan bikin cara baru.
    // v45 (permintaan user: info "kurang jelas" — taruh interval di widget + baris status tak
    // bilang pemicu Otomatis/Manual): baris ke-2 (`android:maxLines="2"` di layout SUDAH
    // mengantisipasi ini sejak awal, 0 perubahan XML) menampilkan interval aktif via
    // `formatInterval()` (dibuat `internal` di MainActivity.kt, v45, 0 logic diduplikasi). Baris
    // ke-1 menyisipkan `(Otomatis)`/`(Manual)` dari `ParsedLog.trigger` (juga dibuat `internal`)
    // HANYA kalau baris log itu punya token trigger (baris lama pra-v38 -> tanpa embel-embel,
    // sama seperti sebelumnya).
    private fun friendlyStatus(context: Context): String {
        val prefs = Prefs(context)
        val intervalLine = context.getString(R.string.widget_interval_line, formatInterval(prefs.intervalMinutes))
        if (prefs.lastRunMs == 0L) {
            return "${context.getString(R.string.widget_status_never)}\n$intervalLine"
        }
        val stamp = SimpleDateFormat("dd/MM HH:mm", Locale.US).format(Date(prefs.lastRunMs))
        val parsed = prefs.log.firstOrNull()?.let(::parseLogLine)
        val trigger = parsed?.trigger?.takeIf { it.isNotEmpty() }
        val line1 = when {
            parsed?.skipped == true -> context.getString(R.string.widget_status_not_ready, stamp)
            prefs.lastOk -> if (trigger != null) {
                context.getString(R.string.widget_status_ok_trigger, stamp, trigger)
            } else {
                context.getString(R.string.widget_status_ok, stamp)
            }
            else -> if (trigger != null) {
                context.getString(R.string.widget_status_fail_trigger, stamp, trigger)
            } else {
                context.getString(R.string.widget_status_fail, stamp)
            }
        }
        return "$line1\n$intervalLine"
    }

    private fun runNowPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, LagFixWidgetProvider::class.java).setAction(ACTION_RUN_NOW)
        return PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun openAppPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val ACTION_RUN_NOW = "com.lagfix.fstrim.action.WIDGET_RUN_NOW"
    }
}
