package com.example.cacheclean.ui

import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.cacheclean.data.AppCache
import com.example.cacheclean.data.IconLoader
import com.example.cacheclean.data.Intents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MainScreen(vm: MainViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Одноразовые события из ViewModel: открыть системные настройки.
    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is UiEvent.OpenAppSettings ->
                    if (!Intents.openAppSettings(context, event.packageName)) vm.onSettingsUnavailable()
                UiEvent.OpenUsageAccess -> Intents.openUsageAccess(context)
            }
        }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 24.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Header(state) }

            item {
                BigCleanButton(
                    working = state.working,
                    enabled = state.wizard == null,
                    onClick = vm::onCleanAll,
                )
            }

            item {
                FilledTonalButton(
                    onClick = vm::onCleanAvailable,
                    enabled = !state.working && state.wizard == null,
                ) {
                    Text("ОЧИСТИТЬ КЭШ ВСЕХ ДОСТУПНЫХ")
                }
            }

            state.error?.let { message ->
                item { ErrorCard(message, onDismiss = vm::dismissError) }
            }

            if (!state.usageAccess) {
                item { PermissionCard(onClick = vm::requestUsageAccess) }
            }

            state.wizard?.let { wizard ->
                item {
                    WizardCard(
                        w = wizard,
                        onAdvance = vm::advance,
                        onRetry = vm::reopenCurrent,
                        onStop = vm::stopWizard,
                    )
                }
            }

            state.result?.let { result ->
                item {
                    ResultCard(
                        result = result,
                        onOpenQueue = { vm.startWizard(result.needManual) },
                        onDismiss = vm::dismissResult,
                    )
                }
            }

            item {
                Text(
                    text = "Приложения",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, top = 12.dp),
                )
            }

            if (state.loading && state.apps.isEmpty()) {
                item { CircularProgressIndicator() }
            }

            if (state.usageAccess && !state.loading && state.apps.isEmpty()) {
                item {
                    Text(
                        text = "Кэш не найден — всё чисто.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(state.apps, key = { it.packageName }) { app ->
                AppRow(app = app, onClick = { vm.openSettingsFor(app.packageName) })
            }
        }
    }
}

// ------------------------------------------------------------------ шапка и большая кнопка

@Composable
private fun Header(state: UiState) {
    val context = LocalContext.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Очистка кэша",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = "Общий кэш приложений",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = if (state.usageAccess) Formatter.formatShortFileSize(context, state.totalCache) else "—",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
        )
        if (state.usageAccess && state.apps.isNotEmpty()) {
            Text(
                text = "Приложений с кэшем: ${state.apps.size}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BigCleanButton(working: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(260.dp)
            .alpha(if (enabled) 1f else 0.5f),
        contentAlignment = Alignment.Center,
    ) {
        Halo(working)
        Box(
            modifier = Modifier
                .size(196.dp)
                .shadow(8.dp, CircleShape)
                .clip(CircleShape)
                .background(colors.primary)
                .clickable(enabled = enabled && !working, role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (working) {
                CircularProgressIndicator(
                    color = colors.onPrimary,
                    modifier = Modifier.size(64.dp),
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = null,
                        tint = colors.onPrimary,
                        modifier = Modifier.size(40.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "ОЧИСТИТЬ\nКЭШ",
                        color = colors.onPrimary,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** Пульсирующий ореол вокруг кнопки, пока идёт работа. */
@Composable
private fun Halo(working: Boolean) {
    val scale = if (working) {
        val transition = rememberInfiniteTransition(label = "halo")
        transition.animateFloat(
            initialValue = 1f,
            targetValue = 1.14f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "haloScale",
        ).value
    } else {
        1f
    }
    Box(
        modifier = Modifier
            .size(230.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
    )
}

// ------------------------------------------------------------------ карточки

@Composable
private fun PermissionCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Settings, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Нужен доступ", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Text(
                "Чтобы видеть размер кэша приложений, включите для этого приложения " +
                    "«Доступ к данным об использовании». Android не позволяет запросить его диалогом — " +
                    "переключатель нужно включить вручную.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onClick) { Text("Открыть настройки") }
        }
    }
}

@Composable
private fun ErrorCard(message: String, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Row(
            Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Warning, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text(message, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onDismiss) { Text("OK") }
        }
    }
}

@Composable
private fun WizardCard(
    w: Wizard,
    onAdvance: () -> Unit,
    onRetry: () -> Unit,
    onStop: () -> Unit,
) {
    val context = LocalContext.current
    val current = w.queue[w.index]
    val next = w.queue.getOrNull(w.index + 1)
    val lifecycleOwner = LocalLifecycleOwner.current

    // После успешной очистки сами открываем следующее приложение — но только пока наш экран на переднем плане.
    LaunchedEffect(w.index, w.phase, w.lastFreed) {
        if (w.phase == WizardPhase.DONE_ITEM && w.lastFreed > 0) {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                delay(AUTO_ADVANCE_MS)
                onAdvance()
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Приложение ${w.index + 1} из ${w.queue.size}",
                style = MaterialTheme.typography.labelLarge,
            )
            LinearProgressIndicator(
                progress = {
                    val done = w.index + if (w.phase == WizardPhase.DONE_ITEM) 1 else 0
                    done / w.queue.size.toFloat()
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(current.packageName, 48.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        current.label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Кэш: ${Formatter.formatShortFileSize(context, current.cacheBytes)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            when (w.phase) {
                WizardPhase.OPENED -> Text(
                    "В открывшемся экране нажмите «Хранилище и кеш» → «Очистить кеш», затем вернитесь назад.",
                    style = MaterialTheme.typography.bodyMedium,
                )

                WizardPhase.CHECKING -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Проверяю результат…", style = MaterialTheme.typography.bodyMedium)
                }

                WizardPhase.DONE_ITEM ->
                    if (w.lastFreed > 0) {
                        Text(
                            "Освобождено: ${Formatter.formatShortFileSize(context, w.lastFreed)}." +
                                if (next != null) " Дальше: ${next.label}…" else "",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else {
                        Text(
                            "Размер кэша не изменился. Повторить или пропустить?",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(onClick = onStop) { Text("Остановить") }
                when (w.phase) {
                    WizardPhase.OPENED -> Button(onClick = onRetry) { Text("Открыть снова") }
                    WizardPhase.CHECKING -> Unit
                    WizardPhase.DONE_ITEM ->
                        if (w.lastFreed > 0) {
                            Button(onClick = onAdvance) { Text("Далее") }
                        } else {
                            TextButton(onClick = onAdvance) { Text("Пропустить") }
                            Button(onClick = onRetry) { Text("Повторить") }
                        }
                }
            }
        }
    }
}

@Composable
private fun ResultCard(
    result: CleanResult,
    onOpenQueue: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val visible = remember(result) { MutableTransitionState(false).apply { targetState = true } }
    val progress = remember(result) { Animatable(0f) }
    val iconScale = remember(result) { Animatable(0.4f) }

    LaunchedEffect(result) {
        launch { iconScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
        progress.animateTo(1f, tween(durationMillis = 1200, easing = FastOutSlowInEasing))
    }

    AnimatedVisibility(
        visibleState = visible,
        enter = fadeIn() + expandVertically() + scaleIn(initialScale = 0.92f),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(44.dp)
                            .graphicsLayer {
                                scaleX = iconScale.value
                                scaleY = iconScale.value
                            },
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Готово", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        val shown = (result.totalFreedBytes * progress.value).toLong()
                        Text(
                            "Освобождено: ${Formatter.formatShortFileSize(context, shown)}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }

                Text("Обработано приложений: ${result.processedApps}")
                Text(
                    "Автоматически: ${Formatter.formatShortFileSize(context, result.autoFreedBytes)} " +
                        "(кэш самого приложения)",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (result.manualCleared > 0 || result.manualFreedBytes > 0) {
                    Text(
                        "Через настройки: ${result.manualCleared} ${appsWord(result.manualCleared)} — " +
                            Formatter.formatShortFileSize(context, result.manualFreedBytes),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                if (result.needManual.isNotEmpty()) {
                    val count = result.needManual.size
                    Text(
                        "Требуют ручного действия: $count ${appsWord(count)}",
                        fontWeight = FontWeight.SemiBold,
                    )
                    val names = result.needManual.take(6).joinToString(", ") { it.label }
                    val more = count - 6
                    Text(
                        names + if (more > 0) " и ещё $more" else "",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    TextButton(onClick = onDismiss) { Text("Закрыть") }
                    if (result.needManual.isNotEmpty()) {
                        Button(onClick = onOpenQueue) { Text("Открыть по очереди") }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ список приложений

@Composable
private fun AppRow(app: AppCache, onClick: () -> Unit) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app.packageName, 40.dp)
        Spacer(Modifier.width(12.dp))
        Text(
            text = app.label,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (app.cacheBytes >= 0) Formatter.formatShortFileSize(context, app.cacheBytes) else "—",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AppIcon(packageName: String, size: Dp) {
    val appContext = LocalContext.current.applicationContext
    val bitmap by produceState<ImageBitmap?>(IconLoader.peek(packageName), packageName) {
        if (value == null) {
            value = withContext(Dispatchers.IO) { IconLoader.load(appContext, packageName) }
        }
    }
    val shape = RoundedCornerShape(10.dp)
    val image = bitmap
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = null,
            modifier = Modifier
                .size(size)
                .clip(shape),
        )
    } else {
        Box(
            Modifier
                .size(size)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
    }
}

/** Склонение: 1 приложение, 2 приложения, 5 приложений. */
private fun appsWord(n: Int): String {
    val m10 = n % 10
    val m100 = n % 100
    return when {
        m10 == 1 && m100 != 11 -> "приложение"
        m10 in 2..4 && m100 !in 12..14 -> "приложения"
        else -> "приложений"
    }
}
