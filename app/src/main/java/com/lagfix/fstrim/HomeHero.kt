@file:Suppress("MagicNumber") // file UI baru: ukuran dp/alpha literal (pola sama dgn Design.kt)

package com.lagfix.fstrim

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

// v68 (M3, additive): komposabel baru utk tab Utama. Visual-only: callback, kondisi `enabled`, dan
// teks (judul/isi/aksi/label tombol) dipindah PERSIS dari `StatusCard` + `Button` run di
// MainActivity.kt (v67). File ini tak mengakses state/Prefs/ViewModel: semua lewat parameter.

/**
 * Hero status Shizuku + aksi utama dalam satu kartu kaca.
 * - Lencana bulat berikon (READY = centang mint, selain itu = peringatan amber; token `LocalStatusColors`).
 * - Aksi status (`Unduh Shizuku`/`Buka Shizuku`/`Beri izin`) hanya bila belum READY, seperti `StatusCard` lama.
 * - Tombol run SELALU ada (identik v67): `enabled = READY && !running`, `onClick = onRunNow`.
 * - Progress indeterminate HANYA selama `running` (terikat state, bukan animasi abadi); slot 4dp
 *   dicadangkan supaya tata letak tak melompat saat run mulai/selesai.
 */
@Composable
internal fun HeroStatusCard(
    state: ShizukuState,
    running: Boolean,
    onGrant: () -> Unit,
    onOpen: () -> Unit,
    onRunNow: () -> Unit
) {
    val (title, body, action) = when (state) {
        ShizukuState.NOT_INSTALLED -> Triple("Shizuku belum terpasang", "Pasang Shizuku, aktifkan via Wireless debugging (tanpa root).", "Unduh Shizuku")
        ShizukuState.NOT_RUNNING -> Triple("Shizuku tidak aktif", "Buka Shizuku lalu jalankan layanannya.", "Buka Shizuku")
        ShizukuState.NEED_PERMISSION -> Triple("Izin diperlukan", "Beri izin LagFix untuk memakai Shizuku.", "Beri izin")
        ShizukuState.READY -> Triple("Siap", "Shizuku aktif dan izin diberikan.", null)
    }
    val ready = state == ShizukuState.READY
    val statusColors = LocalStatusColors.current
    val tint = if (ready) statusColors.success else statusColors.warning
    GlassCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(LagSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(LagSpacing.md)
        ) {
            Box(
                Modifier.size(72.dp).background(tint.copy(alpha = 0.16f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(if (ready) R.drawable.ic_status_ok else R.drawable.ic_status_warning),
                    contentDescription = stringResource(
                        if (ready) R.string.cd_shizuku_status_ready else R.string.cd_shizuku_status_action
                    ),
                    tint = tint,
                    modifier = Modifier.size(36.dp)
                )
            }
            Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text(body, textAlign = TextAlign.Center)
            if (action != null) {
                Button(
                    onClick = if (state == ShizukuState.NEED_PERMISSION) onGrant else onOpen,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(action) }
            }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(LagSpacing.xs)) {
                Box(Modifier.fillMaxWidth().height(4.dp)) {
                    if (running) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Button(
                    onClick = onRunNow,
                    enabled = ready && !running,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (running) "Menjalankan\u2026" else "Jalankan fstrim sekarang") }
            }
        }
    }
}

/** Empty state kartu Riwayat (belum ada run). Judul = copy lama "Belum pernah dijalankan" + petunjuk aksi. */
@Composable
internal fun HistoryEmptyState() {
    Column(
        Modifier.fillMaxWidth().padding(vertical = LagSpacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(LagSpacing.xs)
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_empty_history),
            contentDescription = null, // dekoratif: judul di bawahnya sudah dibaca TalkBack
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(32.dp)
        )
        Text(stringResource(R.string.history_empty_title))
        Text(
            stringResource(R.string.history_empty_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
