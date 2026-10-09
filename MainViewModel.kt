package com.example.cacheclean.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.cacheclean.data.AppCache
import com.example.cacheclean.data.CacheRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = CacheRepository(app)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var scanJob: Job? = null

    /** Сколько байт освобождено автоматически в текущей «сессии» очистки. */
    private var sessionAutoFreed = 0L

    // ---------------------------------------------------------------- жизненный цикл

    /** Вызывается из Activity.onResume — в том числе при возврате из системных настроек. */
    fun onResume() {
        _state.update { it.copy(usageAccess = repo.hasUsageAccess()) }
        val wizard = _state.value.wizard
        if (wizard == null) {
            refresh()
        } else if (wizard.phase == WizardPhase.OPENED) {
            evaluateReturn(wizard)
        }
    }

    fun refresh() {
        scanJob?.cancel()
        scanJob = launchGuarded {
            _state.update { it.copy(loading = true) }
            val apps = repo.scan()
            _state.update {
                it.copy(
                    loading = false,
                    apps = apps,
                    totalCache = apps.sumOf { app -> app.cacheBytes.coerceAtLeast(0L) },
                )
            }
        }
    }

    // ---------------------------------------------------------------- кнопки

    /** Большая кнопка: авто-часть + очередь экранов настроек для остальных приложений. */
    fun onCleanAll() {
        val s = _state.value
        if (s.working || s.wizard != null) return
        if (!s.usageAccess) {
            _events.trySend(UiEvent.OpenUsageAccess)
            return
        }
        launchGuarded {
            _state.update { it.copy(working = true, result = null, error = null) }
            sessionAutoFreed = repo.clearOwnCache()
            val apps = repo.scan()
            delay(600) // чтобы анимация успела показаться
            _state.update {
                it.copy(
                    working = false,
                    apps = apps,
                    totalCache = apps.sumOf { app -> app.cacheBytes.coerceAtLeast(0L) },
                )
            }
            val queue = apps.filter { it.cacheBytes >= MIN_QUEUE_BYTES }
            if (queue.isEmpty()) {
                _state.update {
                    it.copy(result = CleanResult(sessionAutoFreed, 0L, 0, emptyList()))
                }
            } else {
                startWizard(queue)
            }
        }
    }

    /** «ОЧИСТИТЬ КЭШ ВСЕХ ДОСТУПНЫХ»: только то, что Android разрешает без участия пользователя. */
    fun onCleanAvailable() {
        val s = _state.value
        if (s.working || s.wizard != null) return
        launchGuarded {
            _state.update { it.copy(working = true, result = null, error = null) }
            sessionAutoFreed = repo.clearOwnCache()
            val apps = repo.scan()
            delay(600)
            _state.update {
                it.copy(
                    working = false,
                    apps = apps,
                    totalCache = apps.sumOf { app -> app.cacheBytes.coerceAtLeast(0L) },
                    result = CleanResult(
                        autoFreedBytes = sessionAutoFreed,
                        manualFreedBytes = 0L,
                        manualCleared = 0,
                        needManual = apps.filter { app -> app.cacheBytes >= MIN_QUEUE_BYTES },
                    ),
                )
            }
        }
    }

    fun requestUsageAccess() {
        _events.trySend(UiEvent.OpenUsageAccess)
    }

    fun openSettingsFor(packageName: String) {
        _events.trySend(UiEvent.OpenAppSettings(packageName))
    }

    fun dismissResult() {
        sessionAutoFreed = 0L
        _state.update { it.copy(result = null) }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    // ---------------------------------------------------------------- очередь настроек

    fun startWizard(queue: List<AppCache>) {
        if (queue.isEmpty()) return
        _state.update { it.copy(result = null, wizard = Wizard(queue = queue)) }
        _events.trySend(UiEvent.OpenAppSettings(queue.first().packageName))
    }

    /** Перейти к следующему приложению очереди (кнопка «Далее» или автопереход). */
    fun advance() {
        val w = _state.value.wizard ?: return
        if (w.phase != WizardPhase.DONE_ITEM || w.index >= w.queue.lastIndex) return
        val next = w.index + 1
        _state.update {
            it.copy(wizard = w.copy(index = next, phase = WizardPhase.OPENED, lastFreed = 0L))
        }
        _events.trySend(UiEvent.OpenAppSettings(w.queue[next].packageName))
    }

    /** Открыть настройки текущего приложения ещё раз. */
    fun reopenCurrent() {
        val w = _state.value.wizard ?: return
        val current = w.queue[w.index]
        _state.update {
            it.copy(wizard = w.copy(phase = WizardPhase.OPENED, lastFreed = 0L, leftover = w.leftover - current))
        }
        _events.trySend(UiEvent.OpenAppSettings(current.packageName))
    }

    fun stopWizard() {
        val w = _state.value.wizard ?: return
        finishWizard(w)
    }

    /** Настройки приложения не открылись (нет подходящей activity) — пропускаем его. */
    fun onSettingsUnavailable() {
        _state.update { it.copy(error = "Не удалось открыть настройки приложения.") }
        val w = _state.value.wizard ?: return
        if (w.phase == WizardPhase.OPENED) completeItem(w, 0L)
    }

    private fun evaluateReturn(w: Wizard) {
        _state.update { it.copy(wizard = w.copy(phase = WizardPhase.CHECKING)) }
        launchGuarded {
            val current = w.queue[w.index]
            val after = withContext(Dispatchers.IO) { repo.cacheOf(current.packageName) }
            val freed = if (after >= 0) (current.cacheBytes - after).coerceAtLeast(0L) else 0L
            completeItem(w, freed)
        }
    }

    private fun completeItem(w: Wizard, freed: Long) {
        val current = w.queue[w.index]
        val updated = w.copy(
            phase = WizardPhase.DONE_ITEM,
            freedBytes = w.freedBytes + freed,
            lastFreed = freed,
            cleared = w.cleared + if (freed > 0) 1 else 0,
            leftover = if (freed > 0) w.leftover else w.leftover + current,
        )
        if (w.index >= w.queue.lastIndex) {
            finishWizard(updated)
        } else {
            _state.update { it.copy(wizard = updated) }
        }
    }

    private fun finishWizard(w: Wizard) {
        val lastProcessed = if (w.phase == WizardPhase.DONE_ITEM) w.index else w.index - 1
        val untouched = w.queue.drop(lastProcessed + 1)
        val result = CleanResult(
            autoFreedBytes = sessionAutoFreed,
            manualFreedBytes = w.freedBytes,
            manualCleared = w.cleared,
            needManual = w.leftover + untouched,
        )
        _state.update { it.copy(wizard = null, result = result) }
        refresh()
    }

    // ---------------------------------------------------------------- утилиты

    private fun launchGuarded(block: suspend () -> Unit): Job = viewModelScope.launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    loading = false,
                    working = false,
                    error = "Ошибка: ${e.localizedMessage ?: e.javaClass.simpleName}",
                )
            }
        }
    }
}
