package com.example.cacheclean.data

/** Установленное приложение и размер его кэша. [cacheBytes] = -1, если размер узнать нельзя. */
data class AppCache(
    val packageName: String,
    val label: String,
    val cacheBytes: Long,
)
