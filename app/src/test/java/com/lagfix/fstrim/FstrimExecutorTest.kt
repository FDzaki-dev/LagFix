package com.lagfix.fstrim

import android.content.Context
import android.content.pm.PackageManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * Menguji FstrimExecutor.state() murni sebagai fungsi mapping, tanpa device fisik.
 * FstrimExecutor.run() (blocking shell exec via reflection) TIDAK diuji di sini — itu butuh
 * Shizuku binder nyata, di luar scope unit test JVM (tetap PENDING_ROADMAP D1).
 *
 * Histori C4:
 * - v15: @Ignore 6 test ini — CI run nyata pertama (fail-log-16, C3/v14) nunjukin
 *   mockStatic(Shizuku::class.java) TIDAK ter-intercept Mockito inline mock maker di JVM
 *   unit-test worker sungguhan (5/6 MissingMethodInvocationException, 1/6 RuntimeException asli
 *   dari pingBinder()) — bukan bukti FstrimExecutor.state() salah, murni kegagalan teknik mocking.
 * - v16 (kandidat a): @Ignore dicabut, coba JVM arg `-Djdk.attach.allowAttachSelf=true`. Run CI
 *   nyata (fail-log-18) masih gagal dgn error IDENTIK (MissingMethodInvocationException) —
 *   kandidat (a) TERBUKTI SALAH TEBAKAN 2x (fail-log-16 & fail-log-18), dihentikan.
 * - v17 (kandidat b, approval eksplisit user — refactor produksi): `FstrimExecutor.kt` sekarang
 *   punya seam `ShizukuGateway` (interface). Static mocking Shizuku DIHAPUS TOTAL dari test ini,
 *   diganti mock interface biasa (non-static, proxy subclass) — tidak butuh instrumentasi/
 *   self-attach sama sekali, jadi root cause C4 tidak relevan lagi (paling robust, sesuai catatan
 *   PENDING_ROADMAP C4). Body & ekspektasi ke-6 test TIDAK diubah, murni ganti mekanisme mock.
 */
class FstrimExecutorTest {

    @After
    fun tearDown() {
        // WAJIB: FstrimExecutor.gateway itu state singleton bersama — kalau tidak dikembalikan,
        // mock test ini bisa bocor ke test lain / ke run production dalam proses JVM yang sama.
        FstrimExecutor.gateway = RealShizukuGateway
    }

    @Test
    fun `state NOT_INSTALLED saat binder mati dan app Shizuku tidak terpasang`() {
        val gw = mock(ShizukuGateway::class.java)
        `when`(gw.pingBinder()).thenReturn(false)
        FstrimExecutor.gateway = gw

        val pm = mock(PackageManager::class.java)
        `when`(pm.getPackageInfo(FstrimExecutor.SHIZUKU_PKG, 0))
            .thenThrow(PackageManager.NameNotFoundException())
        val ctx = mock(Context::class.java)
        `when`(ctx.packageManager).thenReturn(pm)

        assertEquals(ShizukuState.NOT_INSTALLED, FstrimExecutor.state(ctx))
    }

    @Test
    fun `state NOT_RUNNING saat binder mati tapi app Shizuku terpasang`() {
        val gw = mock(ShizukuGateway::class.java)
        `when`(gw.pingBinder()).thenReturn(false)
        FstrimExecutor.gateway = gw

        val pm = mock(PackageManager::class.java)
        `when`(pm.getPackageInfo(FstrimExecutor.SHIZUKU_PKG, 0)).thenReturn(null)
        val ctx = mock(Context::class.java)
        `when`(ctx.packageManager).thenReturn(pm)

        assertEquals(ShizukuState.NOT_RUNNING, FstrimExecutor.state(ctx))
    }

    @Test
    fun `state NOT_RUNNING saat binder aktif tapi versi Shizuku preV11`() {
        val gw = mock(ShizukuGateway::class.java)
        `when`(gw.pingBinder()).thenReturn(true)
        `when`(gw.isPreV11()).thenReturn(true)
        FstrimExecutor.gateway = gw

        val ctx = mock(Context::class.java)
        assertEquals(ShizukuState.NOT_RUNNING, FstrimExecutor.state(ctx))
    }

    @Test
    fun `state READY saat binder aktif, bukan preV11, dan izin granted`() {
        val gw = mock(ShizukuGateway::class.java)
        `when`(gw.pingBinder()).thenReturn(true)
        `when`(gw.isPreV11()).thenReturn(false)
        `when`(gw.checkSelfPermission()).thenReturn(PackageManager.PERMISSION_GRANTED)
        FstrimExecutor.gateway = gw

        val ctx = mock(Context::class.java)
        assertEquals(ShizukuState.READY, FstrimExecutor.state(ctx))
    }

    @Test
    fun `state NEED_PERMISSION saat binder aktif, bukan preV11, izin belum granted`() {
        val gw = mock(ShizukuGateway::class.java)
        `when`(gw.pingBinder()).thenReturn(true)
        `when`(gw.isPreV11()).thenReturn(false)
        `when`(gw.checkSelfPermission()).thenReturn(PackageManager.PERMISSION_DENIED)
        FstrimExecutor.gateway = gw

        val ctx = mock(Context::class.java)
        assertEquals(ShizukuState.NEED_PERMISSION, FstrimExecutor.state(ctx))
    }

    @Test
    fun `state fallback ke NOT_RUNNING kalau pingBinder melempar exception`() {
        val gw = mock(ShizukuGateway::class.java)
        `when`(gw.pingBinder()).thenThrow(RuntimeException("binder gone"))
        FstrimExecutor.gateway = gw

        val ctx = mock(Context::class.java)
        assertEquals(ShizukuState.NOT_RUNNING, FstrimExecutor.state(ctx))
    }
}
