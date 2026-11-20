package com.lagfix.fstrim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v179: bagian murni pemasangan senyap via Shizuku (perintah & penilaian hasil); jalur Shizuku/disk tak diuji. */
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
}
