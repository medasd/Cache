package com.example.cacheclean.ui

import com.example.cacheclean.data.AppCache

/** Приложения с кэшем меньше этого порога в очередь ручной очистки не попадают (1 МБ). */
const val MIN_QUEUE_BYTES = 1_000_000L

/** Пауза перед автопереходом к следующему приложению очереди. */
const val AUTO_ADVANCE_MS = 1_200L

enum class WizardPhase {
    /** Системные настройки приложения открыты, ждём возврата. */
    OPENED,

    /** Пользователь вернулся, измеряем, сколько освободилось. */
    CHECKING,

    /** Текущее приложение обработано. */
    DONE_ITEM,
}

/** Очередь ручной очистки: одно приложение за другим. */
data class Wizard(
    val queue: List<AppCache>,
    val index: Int = 0,
    val phase: WizardPhase = WizardPhase.OPENED,
    val freedBytes: Long = 0L,
    val lastFreed: Long = 0L,
    val cleared: Int = 0,
    val leftover: List<AppCache> = emptyList(),
)

data class CleanResult(
    val autoFreedBytes: Long,
    val manualFreedBytes: Long,
    val manualCleared: Int,
    val needManual: List<AppCache>,
) {
    val totalFreedBytes: Long get() = autoFreedBytes + manualFreedBytes

    /** Само приложение (автоматически) + приложения, очищенные через настройки. */
    val processedApps: Int get() = 1 + manualCleared
}

data class UiState(
    val usageAccess: Boolean = false,
    val loading: Boolean = true,
    val working: Boolean = false,
    val apps: List<AppCache> = emptyList(),
    val totalCache: Long = 0L,
    val wizard: Wizard? = null,
    val result: CleanResult? = null,
    val error: String? = null,
)

sealed interface UiEvent {
    data class OpenAppSettings(val packageName: String) : UiEvent
    data object OpenUsageAccess : UiEvent
}
