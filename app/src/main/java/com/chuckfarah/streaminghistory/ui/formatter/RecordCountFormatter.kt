package com.chuckfarah.streaminghistory.ui.formatter

fun viewingRecords(count: Int): String =
    "$count viewing record${if (count == 1) "" else "s"}"

fun distinctEpisodes(count: Int): String =
    "$count episode${if (count == 1) "" else "s"}"

fun seasons(count: Int): String =
    "$count season${if (count == 1) "" else "s"}"

fun repeatBadge(count: Int): String? =
    if (count > 1) "×$count" else null
