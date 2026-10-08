package com.lagfix.fstrim

// v141: jalur kedua pemicu hapus-task (`LeaveGuard.onUserLeave`). Bukti X6850 17:57 (APK 114 = v140): Recents dibuka
// dari dalam app lalu kartu digeser -> proses mati `[Swipe-up clean]`; `recentsReadBack` proses berikutnya memuat
// `lastDecision=none lastDecisionAt=0` = `onUserLeaveHint` tak pernah mencatat satu keputusan pun. `onStop` menjadi
// pemicu kedua dgn keputusan & pengaman yg SAMA; tiap panggilan dihitung per sumber (lihat `formatLeaveCalls`).

/** Sumber panggilan `LeaveGuard.onUserLeave`: callback `onUserLeaveHint` (jalur v140). */
internal const val LEAVE_SOURCE_HINT = "hint"

/** Sumber panggilan `LeaveGuard.onUserLeave`: callback `onStop` (jalur v141). */
internal const val LEAVE_SOURCE_STOP = "stop"

/** v141: alasan jalur `onStop` BUKAN user pergi (null = lanjut ke `leaveDecision`); sumber `hint` selalu null. */
internal fun stopSkipReason(source: String, changingConfig: Boolean, interactive: Boolean): String? = when {
    source != LEAVE_SOURCE_STOP -> null
    changingConfig -> "skip:config_change"
    !interactive -> "skip:screen_off"
    else -> null
}

/** v141: jumlah panggilan per sumber + panggilan terakhir + galat mentah, apa adanya (tanpa tafsiran). */
internal fun formatLeaveCalls(
    hintCalls: Int,
    stopCalls: Int,
    lastCall: String,
    lastCallMs: Long,
    lastError: String
): String =
    "calls_hint=$hintCalls calls_stop=$stopCalls lastCall=$lastCall lastCallAt=$lastCallMs lastError=$lastError"
