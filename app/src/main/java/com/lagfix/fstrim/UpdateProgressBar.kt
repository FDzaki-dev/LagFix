package com.lagfix.fstrim

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

// v171 (additive, laporan user: unduhan update tak punya bilah progres). Komposabel baru utk kartu Pembaruan;
// tak mengakses ViewModel/Prefs: kemajuan masuk lewat lambda [progress].

/**
 * Bilah progres unduhan APK update: judul + persen, bilah, lalu "x MB dari y MB".
 * - Total diketahui -> bilah TERTENTU (pecahan dari [DownloadProgress.fraction], diperhalus tween 150 ms).
 * - Belum terhubung (null) / total tak diketahui -> bilah tak tentu (animasi hanya selama unduhan berjalan).
 * [progress] dibaca DI SINI, bukan di pemanggil: tiap laporan (~100x per unduhan) hanya fungsi ini yang
 * direkomposisi, bukan seluruh layar.
 */
@Composable
internal fun DownloadProgressBar(progress: () -> DownloadProgress?, modifier: Modifier = Modifier) {
    val current = progress()
    val fraction = current?.fraction
    val percent = current?.percent
    val animated by animateFloatAsState(
        targetValue = fraction ?: 0f,
        animationSpec = tween(durationMillis = LagMotion.FADE_MS),
        label = "downloadFraction"
    )
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(LagSpacing.xs)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Mengunduh pembaruan…", style = MaterialTheme.typography.labelLarge)
            if (percent != null) Text("$percent%", style = MaterialTheme.typography.labelLarge)
        }
        if (fraction != null) {
            LinearProgressIndicator(progress = { animated }, modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        Text(
            downloadStatusLine(current),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
