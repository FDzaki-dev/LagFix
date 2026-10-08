@file:Suppress("MagicNumber") // file UI baru: ukuran dp/alpha literal (pola sama dgn Design.kt)

package com.lagfix.fstrim

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import com.lagfix.fstrim.LagButton as Button // v142: pembungkus tema (Glass = Material3 identik)
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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
        ShizukuState.NOT_INSTALLED -> Triple(
            "Shizuku belum terpasang",
            "Pasang Shizuku, aktifkan via Wireless debugging (tanpa root).",
            "Unduh Shizuku"
        )
        ShizukuState.NOT_RUNNING -> Triple(
            "Shizuku tidak aktif",
            "Buka Shizuku lalu jalankan layanannya.",
            "Buka Shizuku"
        )
        ShizukuState.NEED_PERMISSION -> Triple(
            "Izin diperlukan",
            "Beri izin LagFix untuk memakai Shizuku.",
            "Beri izin"
        )
        ShizukuState.READY -> Triple("Siap", "Shizuku aktif dan izin diberikan.", null)
    }
    val ready = state == ShizukuState.READY
    val statusColors = LocalStatusColors.current
    val tint = if (ready) statusColors.success else statusColors.warning
    // v71 (M4, fix v73): fade progress 150 ms lewat animateFloatAsState + alpha, BUKAN AnimatedVisibility
    // (di dalam Box, AnimatedVisibility jatuh ke ColumnScope.AnimatedVisibility milik Column di luarnya
    // -> error kompilasi "cannot be called in this context with an implicit receiver", CI run 58).
    // Indikator hanya dikomposisi saat alpha > 0 -> setelah fade-out selesai keluar dari komposisi,
    // animasi indeterminate berhenti. Tween finite, tak ada loop abadi.
    val progressAlpha by animateFloatAsState(
        targetValue = if (running) 1f else 0f,
        animationSpec = tween(durationMillis = LagMotion.FADE_MS),
        label = "progressAlpha"
    )
    GlassCard(Modifier.fillMaxWidth(), animateSize = true) { // v71 (M4): tinggi kartu halus saat status/aksi berganti
        Column(
            Modifier.fillMaxWidth().padding(LagSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(LagSpacing.md)
        ) {
            Box(
                Modifier.size(72.dp).lagBadge(tint, neo = LocalAppTheme.current == AppTheme.NEO),
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
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() } // v82 (M9): judul hero = heading TalkBack
            )
            Text(body, textAlign = TextAlign.Center)
            if (action != null) {
                Button(
                    onClick = if (state == ShizukuState.NEED_PERMISSION) onGrant else onOpen,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(action) }
            }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(LagSpacing.xs)) {
                Box(Modifier.fillMaxWidth().height(4.dp)) {
                    if (progressAlpha > 0f) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().alpha(progressAlpha))
                    }
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
