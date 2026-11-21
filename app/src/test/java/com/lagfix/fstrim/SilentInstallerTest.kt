package com.lagfix.fstrim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v179: bagian murni pemasangan senyap via Shizuku (perintah & penilaian hasil); v181: pengaman paket;
 * v182: detail & penilaian saat pengaliran APK gagal. Jalur Shizuku/disk tak diuji.
 */
class SilentInstallerTest {
    @Test
    fun `perintah pasang membawa ukuran APK dan menggabung galat ke keluaran`() {
        assertEquals("pm install -r -S 26214400 2>&1", SilentInstaller.installCommand(26_214_400L))
    }

    @Test
    fun `sukses hanya bila kode keluar 0 dan keluaran memuat Success`() {
        assertTrue(SilentInstaller.isInstallSuccess(0, "Success\n"))
        assertTrue(SilentInstaller.isInstallSuccess(0, "success"))
    }

    @Test
    fun `kegagalan pm tidak dianggap sukses`() {
        assertFalse(SilentInstaller.isInstallSuccess(1, "Failure [INSTALL_FAILED_VERSION_DOWNGRADE]"))
        assertFalse(SilentInstaller.isInstallSuccess(0, "Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE]"))
        assertFalse(SilentInstaller.isInstallSuccess(null, "Success"))
        assertFalse(SilentInstaller.isInstallSuccess(0, ""))
    }

    @Test
    fun `paket APK sama dengan paket aplikasi tidak ditolak`() {
        assertNull(SilentInstaller.packageMismatch("com.lagfix.fstrim", "com.lagfix.fstrim"))
    }

    @Test
    fun `paket APK lain ditolak dengan nama kedua paket`() {
        assertEquals(
            "Paket APK (com.contoh.lain) bukan com.lagfix.fstrim",
            SilentInstaller.packageMismatch("com.contoh.lain", "com.lagfix.fstrim")
        )
    }

    @Test
    fun `paket APK tak terbaca ditolak dan ditulis null apa adanya`() {
        assertEquals(
            "Paket APK (null) bukan com.lagfix.fstrim",
            SilentInstaller.packageMismatch(null, "com.lagfix.fstrim")
        )
    }

    @Test
    fun `detail tanpa galat pengaliran sama dengan format sebelum v182`() {
        assertEquals("exit=0 timedOut=false\nSuccess\n", SilentInstaller.installDetail(0, false, "Success\n", null))
    }

    @Test
    fun `detail memuat galat pengaliran sebelum keluaran mentah pm dan exit null ditulis null`() {
        assertEquals(
            "exit=null timedOut=false\nfeed=IOException: Broken pipe\nFailure [INSTALL_FAILED_USER_RESTRICTED]",
            SilentInstaller.installDetail(
                null,
                false,
                "Failure [INSTALL_FAILED_USER_RESTRICTED]",
                "IOException: Broken pipe"
            )
        )
    }

    @Test
    fun `galat pengaliran APK tidak pernah dinilai sukses walau keluaran memuat Success`() {
        assertFalse(SilentInstaller.isInstallSuccess(0, "Success", feedFailed = true))
        assertTrue(SilentInstaller.isInstallSuccess(0, "Success", feedFailed = false))
    }
}
