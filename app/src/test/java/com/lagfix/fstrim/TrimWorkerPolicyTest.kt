package com.lagfix.fstrim

import com.lagfix.fstrim.TrimWorker.NotReadyAction
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * v163: kebijakan akhir `TrimWorker` saat Shizuku belum siap. Fungsi murni, tanpa Android/WorkManager.
 * Run manual (widget) tak boleh ikut `Result.retry()` (WorkManager mengulang tanpa batas: toast + baris Riwayat
 * berulang); rantai radikal melanjutkan tik berikutnya; periodik tetap retry seperti sebelumnya (v13).
 */
class TrimWorkerPolicyTest {

    @Test
    fun manualRun_stopsWithoutRetry() {
        assertEquals(NotReadyAction.STOP, TrimWorker.notReadyAction(manual = true, chain = false))
    }

    @Test
    fun radicalChain_schedulesNextTick() {
        assertEquals(NotReadyAction.NEXT_TICK, TrimWorker.notReadyAction(manual = false, chain = true))
    }

    @Test
    fun periodicRun_keepsRetry() {
        assertEquals(NotReadyAction.RETRY, TrimWorker.notReadyAction(manual = false, chain = false))
    }

    @Test
    fun chainWinsOverManual_ifBothFlagsEverSet() {
        assertEquals(NotReadyAction.NEXT_TICK, TrimWorker.notReadyAction(manual = true, chain = true))
    }
}
