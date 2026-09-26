package com.lagfix.fstrim

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/**
 * Widget home screen (fitur baru, prioritas user atas widget+QS tile). Tampilkan baris status
 * terakhir apa adanya dari `Prefs.log` (sudah diformat oleh `Prefs.record()` — "dd/MM HH:mm
 * OK/FAIL <ms>ms <pesan>", 0 duplikasi format tanggal baru di sini) + tombol "Jalankan Sekarang".
 * Tombol memicu `Scheduler.runOnce()` (TrimWorker via WorkManager) — jalur yang sama persis
 * dengan run manual di MainViewModel & jadwal otomatis, 0 logic Shizuku baru ditulis. Widget tidak
 * bisa menunggu hasil fstrim secara sinkron (broadcast onReceive tidak boleh blocking lama), jadi
 * setelah tombol ditekan status ditampilkan "Menjadwalkan…" dulu; hasil riil (OK/FAIL) muncul saat
 * widget refresh berikutnya (updatePeriodMillis 30 menit) atau saat app dibuka lagi.
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
        val status = if (running) {
            context.getString(R.string.widget_status_running)
        } else {
            Prefs(context).log.firstOrNull() ?: context.getString(R.string.widget_status_never)
        }
        val views = RemoteViews(context.packageName, R.layout.widget_lagfix).apply {
            setTextViewText(R.id.widget_title, context.getString(R.string.app_name))
            setTextViewText(R.id.widget_status, status)
            setTextViewText(R.id.widget_button, context.getString(R.string.widget_button_run))
            setOnClickPendingIntent(R.id.widget_button, runNowPendingIntent(context))
            setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent(context))
        }
        manager.updateAppWidget(id, views)
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
