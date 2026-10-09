package com.example.cacheclean.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/** Переходы в системные настройки (только публичные, документированные intent'ы). */
object Intents {

    /** Экран «О приложении»: Хранилище и кеш → Очистить кеш. */
    fun openAppSettings(context: Context, packageName: String): Boolean = start(
        context,
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
    )

    /** «Доступ к данным об использовании» — сразу на страницу нашего приложения, иначе общий список. */
    fun openUsageAccess(context: Context): Boolean =
        start(
            context,
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
        ) || start(context, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }
}
