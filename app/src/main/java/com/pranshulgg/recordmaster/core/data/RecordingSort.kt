package com.pranshulgg.recordmaster.core.data

enum class RecordingSort(val label: String) {
    DATE_DESC("Newest first"),
    DATE_ASC("Oldest first"),
    NAME_ASC("Name A–Z"),
    NAME_DESC("Name Z–A"),
    DURATION_DESC("Longest first"),
    DURATION_ASC("Shortest first"),
    SIZE_DESC("Largest first"),
    SIZE_ASC("Smallest first");
}

enum class RecordingFilter(val label: String) {
    ALL("All"),
    FAVORITES("Favorites"),
    TRANSCRIBED("With transcript"),
    TAGGED("With tags");
}
