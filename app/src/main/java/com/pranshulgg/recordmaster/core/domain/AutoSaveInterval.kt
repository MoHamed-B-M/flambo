package com.pranshulgg.recordmaster.core.domain

/**
 * How often an active take is checkpointed for crash recovery.
 *
 * [DISABLED] keeps the current single-file-at-save behaviour; a selected
 * interval is consumed by the chunked recorder when that work lands.
 */
enum class AutoSaveInterval(
    val label: String,
    val minutes: Int,
    val description: String
) {
    DISABLED("Disabled", 0, "Save only when you stop the recording"),
    ONE_MINUTE("Every 1 min", 1, "Checkpoint the take every minute"),
    FIVE_MINUTES("Every 5 min", 5, "Checkpoint the take every five minutes"),
    TEN_MINUTES("Every 10 min", 10, "Checkpoint the take every ten minutes");

    val isEnabled: Boolean get() = minutes > 0

    companion object {
        /** Opt-in: keeps existing recording behaviour until the user chooses an interval. */
        val Default: AutoSaveInterval = DISABLED

        fun fromMinutes(value: Int): AutoSaveInterval =
            entries.firstOrNull { it.minutes == value } ?: Default
    }
}
