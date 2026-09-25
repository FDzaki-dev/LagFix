package com.lagfix.fstrim

import android.content.Context
import android.content.pm.PackageManager
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.`when`
import rikka.shizuku.Shizuku

/**
 * Menguji FstrimExecutor.state() murni sebagai fungsi mapping (Shizuku static calls di-mock),
 * tanpa device fisik. FstrimExecutor.run() (blocking shell exec via reflection) TIDAK diuji di
 * sini — itu butuh Shizuku binder nyata, di luar scope unit test JVM (tetap PENDING_ROADMAP D1).
 */
class FstrimExecutorTest {

    @Test
    fun `state NOT_INSTALLED saat binder mati dan app Shizuku tidak terpasang`() {
        mockStatic(Shizuku::class.java).use { shizuku ->
            shizuku.`when`<Boolean> { Shizuku.pingBinder() }.thenReturn(false)

            val pm = mock(PackageManager::class.java)
            `when`(pm.getPackageInfo(FstrimExecutor.SHIZUKU_PKG, 0))
                .thenThrow(PackageManager.NameNotFoundException())
            val ctx = mock(Context::class.java)
            `when`(ctx.packageManager).thenReturn(pm)

            assertEquals(ShizukuState.NOT_INSTALLED, FstrimExecutor.state(ctx))
        }
    }

    @Test
    fun `state NOT_RUNNING saat binder mati tapi app Shizuku terpasang`() {
        mockStatic(Shizuku::class.java).use { shizuku ->
            shizuku.`when`<Boolean> { Shizuku.pingBinder() }.thenReturn(false)

            val pm = mock(PackageManager::class.java)
            `when`(pm.getPackageInfo(FstrimExecutor.SHIZUKU_PKG, 0)).thenReturn(null)
            val ctx = mock(Context::class.java)
            `when`(ctx.packageManager).thenReturn(pm)

            assertEquals(ShizukuState.NOT_RUNNING, FstrimExecutor.state(ctx))
        }
    }

    @Test
    fun `state NOT_RUNNING saat binder aktif tapi versi Shizuku preV11`() {
        mockStatic(Shizuku::class.java).use { shizuku ->
            shizuku.`when`<Boolean> { Shizuku.pingBinder() }.thenReturn(true)
            shizuku.`when`<Boolean> { Shizuku.isPreV11() }.thenReturn(true)

            val ctx = mock(Context::class.java)
            assertEquals(ShizukuState.NOT_RUNNING, FstrimExecutor.state(ctx))
        }
    }

    @Test
    fun `state READY saat binder aktif, bukan preV11, dan izin granted`() {
        mockStatic(Shizuku::class.java).use { shizuku ->
            shizuku.`when`<Boolean> { Shizuku.pingBinder() }.thenReturn(true)
            shizuku.`when`<Boolean> { Shizuku.isPreV11() }.thenReturn(false)
            shizuku.`when`<Int> { Shizuku.checkSelfPermission() }
                .thenReturn(PackageManager.PERMISSION_GRANTED)

            val ctx = mock(Context::class.java)
            assertEquals(ShizukuState.READY, FstrimExecutor.state(ctx))
        }
    }

    @Test
    fun `state NEED_PERMISSION saat binder aktif, bukan preV11, izin belum granted`() {
        mockStatic(Shizuku::class.java).use { shizuku ->
            shizuku.`when`<Boolean> { Shizuku.pingBinder() }.thenReturn(true)
            shizuku.`when`<Boolean> { Shizuku.isPreV11() }.thenReturn(false)
            shizuku.`when`<Int> { Shizuku.checkSelfPermission() }
                .thenReturn(PackageManager.PERMISSION_DENIED)

            val ctx = mock(Context::class.java)
            assertEquals(ShizukuState.NEED_PERMISSION, FstrimExecutor.state(ctx))
        }
    }

    @Test
    fun `state fallback ke NOT_RUNNING kalau pingBinder melempar exception`() {
        mockStatic(Shizuku::class.java).use { shizuku ->
            shizuku.`when`<Boolean> { Shizuku.pingBinder() }.thenThrow(RuntimeException("binder gone"))

            val ctx = mock(Context::class.java)
            assertEquals(ShizukuState.NOT_RUNNING, FstrimExecutor.state(ctx))
        }
    }
}
