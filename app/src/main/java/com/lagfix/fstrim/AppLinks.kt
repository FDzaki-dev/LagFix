package com.lagfix.fstrim

/**
 * Isi GITHUB_OWNER dengan username/organisasi GitHub kamu (pemilik repo LagFix)
 * sebelum build — dipakai untuk tautan "Tautan" di layar utama.
 */
object AppLinks {
    private const val GITHUB_OWNER = "FDzaki-dev"
    private const val GITHUB_REPO = "LagFix"

    private val repo = "https://github.com/$GITHUB_OWNER/$GITHUB_REPO"

    /** Selalu ke rilis terbaru — CI membuat rilis baru tiap build sukses. */
    val releases = "$repo/releases/latest"
    val source = repo
    val newIssue = "$repo/issues/new"
}
